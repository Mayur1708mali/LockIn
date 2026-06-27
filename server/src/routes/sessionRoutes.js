/*
 * File: server/src/routes/sessionRoutes.js
 * Purpose: Express router handling focus session creation and state lookup.
 * Integrates wallet holds with database transactions and Redis state caching.
 */

import express from 'express';
import { query, pool } from '../db/index.js';
import { authenticateToken } from '../middleware/auth.js';
import { getActiveSession, setActiveSession, clearActiveSession } from '../db/redis.js';
import { sendPushNotification } from '../utils/fcm.js';

const router = express.Router();

/**
 * Helper function to map database session rows to SessionDto structure.
 */
function mapToSessionDto(row) {
  return {
    sessionId: row.session_id,
    userId: row.user_id,
    status: row.status,
    startTime: parseInt(row.start_time, 10),
    targetEndTime: parseInt(row.target_end_time, 10),
    actualEndTime: row.actual_end_time ? parseInt(row.actual_end_time, 10) : null,
    penaltyAmount: row.penalty_amount,
    currency: row.currency || 'INR',
    walletTxHoldId: row.wallet_tx_hold_id,
    allowlistVersion: row.allowlist_version,
    platform: row.platform || 'android'
  };
}

/**
 * Creates a new focus session.
 * Route: POST /sessions
 * Headers: Authorization: Bearer <token>
 * Request Body: { sessionId: string, penaltyAmount: number, startTime: number, targetEndTime: number, allowlistVersion: number }
 * Response Body: SessionDto
 */
router.post('/', authenticateToken, async (req, res, next) => {
  const { sessionId, penaltyAmount, startTime, targetEndTime, allowlistVersion } = req.body;
  const userId = req.user.userId;

  // Validate request parameters
  if (!sessionId || penaltyAmount === undefined || !startTime || !targetEndTime || allowlistVersion === undefined) {
    return res.status(400).json({
      success: false,
      message: 'Missing required session parameters (sessionId, penaltyAmount, startTime, targetEndTime, allowlistVersion).'
    });
  }

  // Ensure penalty is non-negative
  if (penaltyAmount < 0) {
    return res.status(400).json({
      success: false,
      message: 'Penalty amount cannot be negative.'
    });
  }

  // Check if user already has an active session cached in Redis
  try {
    const cachedSession = await getActiveSession(userId);
    if (cachedSession) {
      return res.status(400).json({
        success: false,
        message: 'An active focus session already exists for this user.'
      });
    }
  } catch (err) {
    console.error('Failed to check active session in Redis:', err);
    // Continue database check even if Redis fails (failover)
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 1. Double check active session in database (fail-safe fallback)
    const sessionCheck = await client.query(
      "SELECT * FROM sessions WHERE user_id = $1 AND status = 'ACTIVE'",
      [userId]
    );
    if (sessionCheck.rows.length > 0) {
      await client.query('ROLLBACK');
      return res.status(400).json({
        success: false,
        message: 'An active focus session already exists in database for this user.'
      });
    }

    // 2. Fetch the user's wallet
    const walletSelect = await client.query(
      'SELECT * FROM wallets WHERE user_id = $1 FOR UPDATE',
      [userId]
    );

    if (walletSelect.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({
        success: false,
        message: 'Wallet not found for this user.'
      });
    }

    const wallet = walletSelect.rows[0];

    // 3. Verify available balance matches penalty requirement
    if (wallet.available_balance < penaltyAmount) {
      await client.query('ROLLBACK');
      return res.status(400).json({
        success: false,
        message: `Insufficient wallet balance. Required: ₹${penaltyAmount / 100}, Available: ₹${wallet.available_balance / 100}`
      });
    }

    // 4. Generate holds and record session details
    const txHoldId = `tx_hold_${sessionId}`;
    const now = Date.now();

    // Deduct available, increment held
    const newAvailable = wallet.available_balance - penaltyAmount;
    const newHeld = wallet.held_balance + penaltyAmount;

    await client.query(
      `UPDATE wallets 
       SET available_balance = $1, held_balance = $2, last_updated = $3 
       WHERE user_id = $4`,
      [newAvailable, newHeld, now, userId]
    );

    // Create session record
    const sessionInsert = await client.query(
      `INSERT INTO sessions (
        session_id, user_id, status, start_time, target_end_time, 
        penalty_amount, wallet_tx_hold_id, allowlist_version, platform
      ) VALUES ($1, $2, 'ACTIVE', $3, $4, $5, $6, $7, 'android')
      RETURNING *`,
      [sessionId, userId, startTime, targetEndTime, penaltyAmount, txHoldId, allowlistVersion]
    );

    // Log hold ledger transaction
    await client.query(
      `INSERT INTO wallet_transactions (
        tx_id, user_id, type, amount, direction, session_id, description, timestamp
      ) VALUES ($1, $2, 'SESSION_HOLD', $3, 'DEBIT', $4, $5, $6)`,
      [
        txHoldId,
        userId,
        'SESSION_HOLD',
        penaltyAmount,
        'DEBIT',
        sessionId,
        'Hold penalty amount for active session',
        startTime
      ]
    );

    await client.query('COMMIT');

    const createdSession = sessionInsert.rows[0];
    const sessionDto = mapToSessionDto(createdSession);

    // Cache the active session details in Redis for fast updates
    try {
      await setActiveSession(userId, sessionDto);
    } catch (redisErr) {
      console.error('Failed to set active session cache in Redis:', redisErr);
      // Don't crash request if cache failed, database state is committed
    }

    res.status(200).json(sessionDto);
  } catch (err) {
    await client.query('ROLLBACK');
    next(err);
  } finally {
    client.release();
  }
});

/**
 * Updates an active session's state (COMPLETED/BROKEN), resolving held funds and logging audit events.
 * Route: PATCH /sessions/:id
 * Headers: Authorization: Bearer <token>
 * Request Body: { status: string, actualEndTime: number }
 * Response Body: SessionDto
 */
router.patch('/:id', authenticateToken, async (req, res, next) => {
  const sessionId = req.params.id;
  const { status, actualEndTime } = req.body;
  const userId = req.user.userId;

  if (!status || !actualEndTime) {
    return res.status(400).json({
      success: false,
      message: 'Missing required update parameters (status, actualEndTime).'
    });
  }

  if (status !== 'COMPLETED' && status !== 'BROKEN') {
    return res.status(400).json({
      success: false,
      message: "Invalid session status. Must be 'COMPLETED' or 'BROKEN'."
    });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 1. Fetch active session record
    const sessionSelect = await client.query(
      'SELECT * FROM sessions WHERE session_id = $1 AND user_id = $2 FOR UPDATE',
      [sessionId, userId]
    );

    if (sessionSelect.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({
        success: false,
        message: 'Session not found or does not belong to the user.'
      });
    }

    const session = sessionSelect.rows[0];

    // Ensure session is active before modifying
    if (session.status !== 'ACTIVE') {
      await client.query('ROLLBACK');
      return res.status(400).json({
        success: false,
        message: `Session is already resolved with status: ${session.status}.`
      });
    }

    // 2. Fetch the wallet details
    const walletSelect = await client.query(
      'SELECT * FROM wallets WHERE user_id = $1 FOR UPDATE',
      [userId]
    );

    if (walletSelect.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({
        success: false,
        message: 'Wallet not found for this user.'
      });
    }

    const wallet = walletSelect.rows[0];
    const penaltyAmount = session.penalty_amount;
    const now = Date.now();

    let newAvailable = wallet.available_balance;
    let newHeld = wallet.held_balance - penaltyAmount;
    let newTotalPenaltiesPaid = wallet.total_penalties_paid;

    if (newHeld < 0) {
      await client.query('ROLLBACK');
      return res.status(400).json({
        success: false,
        message: 'Held balance cannot drop below zero. Session cannot be settled.'
      });
    }

    let txType = '';
    let direction = '';
    let txDescription = '';
    let eventType = '';

    if (status === 'COMPLETED') {
      // Release held balance back to available balance
      newAvailable += penaltyAmount;
      txType = 'SESSION_RELEASE';
      direction = 'CREDIT';
      txDescription = 'Release penalty amount for successfully completed session';
      eventType = 'COMPLETED';
    } else {
      // Status is BROKEN: Permanently deduct held balance
      newTotalPenaltiesPaid += penaltyAmount;
      txType = 'PENALTY';
      direction = 'DEBIT';
      txDescription = 'Penalty charged for breaking lock-in session early';
      eventType = 'BREAK_CONFIRMED';
    }

    // Update the wallet records
    await client.query(
      `UPDATE wallets 
       SET available_balance = $1, held_balance = $2, total_penalties_paid = $3, last_updated = $4 
       WHERE user_id = $5`,
      [newAvailable, newHeld, newTotalPenaltiesPaid, now, userId]
    );

    // Update the session details
    const sessionUpdate = await client.query(
      `UPDATE sessions 
       SET status = $1, actual_end_time = $2 
       WHERE session_id = $3 
       RETURNING *`,
      [status, actualEndTime, sessionId]
    );

    // Record the wallet transaction in the audit log
    const txId = `tx_${status.toLowerCase()}_${sessionId}`;
    await client.query(
      `INSERT INTO wallet_transactions (
        tx_id, user_id, type, amount, direction, session_id, description, timestamp
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [txId, userId, txType, penaltyAmount, direction, sessionId, txDescription, actualEndTime]
    );

    // Record the session lifecycle event
    const eventId = `evt_${status.toLowerCase()}_${sessionId}`;
    await client.query(
      `INSERT INTO session_events (
        event_id, session_id, event_type, timestamp, metadata
      ) VALUES ($1, $2, $3, $4, NULL)`,
      [eventId, sessionId, eventType, actualEndTime]
    );

    await client.query('COMMIT');

    const updatedSession = sessionUpdate.rows[0];
    const sessionDto = mapToSessionDto(updatedSession);

    // Clear active session in Redis cache since it has resolved
    try {
      await clearActiveSession(userId);
    } catch (redisErr) {
      console.error('Failed to clear active session cache in Redis:', redisErr);
      // Fail silently to avoid interrupting response
    }

    res.status(200).json(sessionDto);

    // Send push notification asynchronously to inform the user of the result
    if (status === 'COMPLETED') {
      sendPushNotification(
        userId,
        'Session Completed',
        `Congratulations! Your focus session is complete and ₹${penaltyAmount / 100} has been returned to your wallet.`,
        { type: 'SESSION_COMPLETE', sessionId }
      );
    } else {
      sendPushNotification(
        userId,
        'Session Ended Early',
        `Session ended early. ₹${penaltyAmount / 100} penalty charged to your wallet balance.`,
        { type: 'SESSION_BROKEN', sessionId }
      );
    }
  } catch (err) {
    await client.query('ROLLBACK');
    next(err);
  } finally {
    client.release();
  }
});

/**
 * Receives session heartbeats and audit events, logging them to PostgreSQL and updating the last-seen status in Redis.
 * Route: POST /sessions/:id/heartbeat
 * Headers: Authorization: Bearer <token>
 * Request Body: { timestamp: number, eventType: string, metadata: string|null }
 * Response Body: StatusResponse
 */
router.post('/:id/heartbeat', authenticateToken, async (req, res, next) => {
  const sessionId = req.params.id;
  const { timestamp, eventType, metadata } = req.body;
  const userId = req.user.userId;

  if (!timestamp || !eventType) {
    return res.status(400).json({
      success: false,
      message: 'Missing required heartbeat parameters (timestamp, eventType).'
    });
  }

  try {
    // 1. Verify the session belongs to the user and is ACTIVE in DB
    const sessionSelect = await query(
      'SELECT * FROM sessions WHERE session_id = $1 AND user_id = $2',
      [sessionId, userId]
    );

    if (sessionSelect.rows.length === 0) {
      return res.status(404).json({
        success: false,
        message: 'Session not found or access denied.'
      });
    }

    const session = sessionSelect.rows[0];
    if (session.status !== 'ACTIVE') {
      return res.status(400).json({
        success: false,
        message: 'Heartbeats are only accepted for ACTIVE sessions.'
      });
    }

    // 2. Insert the event into session_events
    const eventId = `evt_hb_${sessionId}_${timestamp}`;
    await query(
      `INSERT INTO session_events (event_id, session_id, event_type, timestamp, metadata) 
       VALUES ($1, $2, $3, $4, $5)`,
      [eventId, sessionId, eventType, timestamp, metadata || null]
    );

    // 3. Update the last-seen timestamp in the Redis session cache
    try {
      const cachedSession = await getActiveSession(userId);
      if (cachedSession && cachedSession.sessionId === sessionId) {
        cachedSession.lastSeen = timestamp;
        await setActiveSession(userId, cachedSession);
      } else {
        // Cache miss: rebuild the active session cache in Redis
        const sessionDto = mapToSessionDto(session);
        sessionDto.lastSeen = timestamp;
        await setActiveSession(userId, sessionDto);
      }
    } catch (redisErr) {
      console.error('Failed to update active session heartbeat in Redis:', redisErr);
      // Fail silently to avoid breaking the heartbeat response
    }

    res.status(200).json({
      success: true,
      message: 'Heartbeat event logged successfully.'
    });
  } catch (err) {
    next(err);
  }
});

/**
 * Retrieves details for a specific session by its ID.
 * Route: GET /sessions/:id
 * Headers: Authorization: Bearer <token>
 * Response Body: SessionDto
 */
router.get('/:id', authenticateToken, async (req, res, next) => {
  const sessionId = req.params.id;
  const userId = req.user.userId;

  try {
    const result = await query(
      'SELECT * FROM sessions WHERE session_id = $1 AND user_id = $2',
      [sessionId, userId]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({
        success: false,
        message: 'Session not found or access denied.'
      });
    }

    const sessionDto = mapToSessionDto(result.rows[0]);
    res.status(200).json(sessionDto);
  } catch (err) {
    next(err);
  }
});

export default router;
