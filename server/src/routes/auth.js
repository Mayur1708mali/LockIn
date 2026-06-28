/*
 * File: server/src/routes/auth.js
 * Purpose: Express router for Google Authentication and JWT issuance.
 * Verifies Google ID tokens, manages registration / login records, and returns signed JWT tokens.
 */

import express from 'express';
import { OAuth2Client } from 'google-auth-library';
import { query, pool } from '../db/index.js';
import { generateToken } from '../utils/jwt.js';

const router = express.Router();
const client = new OAuth2Client();

/**
 * Verifies Google ID Token, registers or logs in the user, and returns a JWT.
 * Route: POST /auth/google
 * Request Body: { idToken: string }
 * Response Body: { jwt: string, userId: string, email: string, displayName: string }
 */
router.post('/google', async (req, res, next) => {
  const { idToken } = req.body;

  if (!idToken) {
    return res.status(400).json({
      success: false,
      message: 'Missing idToken parameter.'
    });
  }

  try {
    // 1. Verify Google ID token using google-auth-library
    const audience = process.env.GOOGLE_CLIENT_ID;
    const ticket = await client.verifyIdToken({
      idToken,
      audience: audience ? [audience] : undefined
    });
    const payload = ticket.getPayload();

    if (!payload) {
      return res.status(400).json({
        success: false,
        message: 'Invalid Google ID token payload.'
      });
    }

    const googleId = payload.sub;
    const email = payload.email;
    const displayName = payload.name || payload.given_name || 'Google User';

    if (!googleId || !email) {
      return res.status(400).json({
        success: false,
        message: 'Insufficient user details in Google ID token.'
      });
    }

    const dbClient = await pool.connect();
    try {
      await dbClient.query('BEGIN');

      // Check if the user already exists in PostgreSQL
      let userQuery = await dbClient.query(
        'SELECT * FROM users WHERE google_id = $1',
        [googleId]
      );

      let user;
      if (userQuery.rows.length === 0) {
        // Create new user record
        const insertUser = await dbClient.query(
          `INSERT INTO users (google_id, email, display_name, platform)
           VALUES ($1, $2, $3, 'android')
           RETURNING *`,
          [googleId, email, displayName]
        );
        user = insertUser.rows[0];

        // Create a default empty wallet record with availableBalance = 0 and heldBalance = 0
        const now = Date.now();
        await dbClient.query(
          `INSERT INTO wallets (
            user_id, available_balance, held_balance, total_deposited,
            total_penalties_paid, auto_top_up_enabled,
            auto_top_up_threshold_paise, auto_top_up_amount_paise, last_updated
          ) VALUES ($1, 0, 0, 0, 0, true, 10000, 20000, $2)`,
          [user.user_id, now]
        );
      } else {
        user = userQuery.rows[0];
        // Update user profile info if it has changed, and refresh last_seen_at
        const updateUser = await dbClient.query(
          `UPDATE users 
           SET email = $1, display_name = $2, last_seen_at = NOW() 
           WHERE user_id = $3
           RETURNING *`,
          [email, displayName, user.user_id]
        );
        user = updateUser.rows[0];
      }

      await dbClient.query('COMMIT');

      // Generate our signed JWT containing the user's UUID
      const token = generateToken(user.user_id);

      res.status(200).json({
        jwt: token,
        userId: user.user_id,
        email: user.email,
        displayName: user.display_name,
        isExistingUser: userQuery.rows.length > 0
      });

    } catch (err) {
      await dbClient.query('ROLLBACK');
      throw err;
    } finally {
      dbClient.release();
    }

  } catch (err) {
    console.error('Google Auth Error:', err);
    res.status(401).json({
      success: false,
      message: 'Authentication failed. Invalid Google ID token.',
      error: err.message
    });
  }
});

/**
 * Hydrates mock data (wallet, sessions, transactions) for a given user email.
 * Route: POST /auth/mock-hydrate
 * Request Body: { email: string }
 */
router.post('/mock-hydrate', async (req, res, next) => {
  const { email } = req.body;

  if (!email) {
    return res.status(400).json({
      success: false,
      message: 'Missing email parameter.'
    });
  }

  const dbClient = await pool.connect();
  try {
    await dbClient.query('BEGIN');

    // 1. Check if user exists
    const userResult = await dbClient.query(
      'SELECT * FROM users WHERE email = $1',
      [email]
    );

    if (userResult.rows.length === 0) {
      await dbClient.query('ROLLBACK');
      return res.status(404).json({
        success: false,
        message: `User with email ${email} not found. Please sign in on the app first.`
      });
    }

    const user = userResult.rows[0];
    const userId = user.user_id;
    const now = Date.now();

    // 2. Clear existing sessions, transactions, and wallets to prevent duplicate primary keys
    await dbClient.query('DELETE FROM session_events WHERE session_id IN (SELECT session_id FROM sessions WHERE user_id = $1)', [userId]);
    await dbClient.query('DELETE FROM wallet_transactions WHERE user_id = $1', [userId]);
    await dbClient.query('DELETE FROM sessions WHERE user_id = $1', [userId]);
    await dbClient.query('DELETE FROM wallets WHERE user_id = $1', [userId]);

    // 3. Insert default wallet with ₹500 available and ₹200 held
    await dbClient.query(
      `INSERT INTO wallets (
        user_id, available_balance, held_balance, total_deposited,
        total_penalties_paid, auto_top_up_enabled,
        auto_top_up_threshold_paise, auto_top_up_amount_paise, last_updated
      ) VALUES ($1, 50000, 20000, 70000, 20000, true, 10000, 20000, $2)`,
      [userId, now]
    );

    // 4. Insert mock sessions
    // Session 1: Completed (₹100 penalty amount)
    const session1Id = `mock_session_1_${userId}`;
    const s1Start = now - 2 * 24 * 3600 * 1000;
    const s1End = s1Start + 3600 * 1000;
    await dbClient.query(
      `INSERT INTO sessions (
        session_id, user_id, status, start_time, target_end_time, actual_end_time,
        penalty_amount, wallet_tx_hold_id, allowlist_version, platform
      ) VALUES ($1, $2, 'COMPLETED', $3, $4, $4, 10000, $5, 1, 'android')`,
      [session1Id, userId, s1Start, s1End, `tx_hold_${session1Id}`]
    );

    // Session 2: Broken (₹200 penalty amount)
    const session2Id = `mock_session_2_${userId}`;
    const s2Start = now - 24 * 3600 * 1000;
    const s2End = s2Start + 7200 * 1000;
    const s2ActualEnd = s2Start + 1800 * 1000; // broke after 30 mins
    await dbClient.query(
      `INSERT INTO sessions (
        session_id, user_id, status, start_time, target_end_time, actual_end_time,
        penalty_amount, wallet_tx_hold_id, allowlist_version, platform
      ) VALUES ($1, $2, 'BROKEN', $3, $4, $5, 20000, $6, 1, 'android')`,
      [session2Id, userId, s2Start, s2End, s2ActualEnd, `tx_hold_${session2Id}`]
    );

    // Session 3: Active (₹200 penalty amount)
    const session3Id = `mock_session_3_${userId}`;
    const s3Start = now - 1800 * 1000; // 30 mins ago
    const s3End = now + 1800 * 1000; // 30 mins remaining
    await dbClient.query(
      `INSERT INTO sessions (
        session_id, user_id, status, start_time, target_end_time, actual_end_time,
        penalty_amount, wallet_tx_hold_id, allowlist_version, platform
      ) VALUES ($1, $2, 'ACTIVE', $3, $4, NULL, 20000, $5, 1, 'android')`,
      [session3Id, userId, s3Start, s3End, `tx_hold_${session3Id}`]
    );

    // 5. Insert mock transactions
    // Initial Deposit
    await dbClient.query(
      `INSERT INTO wallet_transactions (
        tx_id, user_id, type, amount, direction, session_id, description, timestamp
      ) VALUES ($1, $2, 'DEPOSIT', 70000, 'CREDIT', NULL, 'Initial Deposit via Razorpay', $3)`,
      [`tx_dep_${now}`, userId, now - 3 * 24 * 3600 * 1000]
    );

    // Session 1 Hold & Release
    await dbClient.query(
      `INSERT INTO wallet_transactions (
        tx_id, user_id, type, amount, direction, session_id, description, timestamp
      ) VALUES ($1, $2, 'SESSION_HOLD', 10000, 'DEBIT', $3, 'Session hold', $4)`,
      [`tx_hold_${session1Id}`, userId, session1Id, s1Start]
    );
    await dbClient.query(
      `INSERT INTO wallet_transactions (
        tx_id, user_id, type, amount, direction, session_id, description, timestamp
      ) VALUES ($1, $2, 'SESSION_RELEASE', 10000, 'CREDIT', $3, 'Session completed and released', $4)`,
      [`tx_rel_${session1Id}`, userId, session1Id, s1End]
    );

    // Session 2 Hold & Penalty
    await dbClient.query(
      `INSERT INTO wallet_transactions (
        tx_id, user_id, type, amount, direction, session_id, description, timestamp
      ) VALUES ($1, $2, 'SESSION_HOLD', 20000, 'DEBIT', $3, 'Session hold', $4)`,
      [`tx_hold_${session2Id}`, userId, session2Id, s2Start]
    );
    await dbClient.query(
      `INSERT INTO wallet_transactions (
        tx_id, user_id, type, amount, direction, session_id, description, timestamp
      ) VALUES ($1, $2, 'PENALTY', 20000, 'DEBIT', $3, 'Broken session penalty charged', $4)`,
      [`tx_pen_${session2Id}`, userId, session2Id, s2ActualEnd]
    );

    // Session 3 Hold
    await dbClient.query(
      `INSERT INTO wallet_transactions (
        tx_id, user_id, type, amount, direction, session_id, description, timestamp
      ) VALUES ($1, $2, 'SESSION_HOLD', 20000, 'DEBIT', $3, 'Session hold', $4)`,
      [`tx_hold_${session3Id}`, userId, session3Id, s3Start]
    );

    await dbClient.query('COMMIT');
    res.status(200).json({
      success: true,
      message: `Successfully hydrated mock data for ${email}.`
    });
  } catch (err) {
    await dbClient.query('ROLLBACK');
    next(err);
  } finally {
    dbClient.release();
  }
});

export default router;
