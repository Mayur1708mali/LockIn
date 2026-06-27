/*
 * File: server/src/jobs/heartbeatMonitor.js
 * Purpose: Watchdog background sweeps checking for active session notifications and missed heartbeats.
 */

import { query, pool } from '../db/index.js';
import { clearActiveSession, redisClient } from '../db/redis.js';
import { sendPushNotification } from '../utils/fcm.js';

/**
 * Sweeps the database for active focus sessions and sends temporal push warnings (halfway, 15-min remaining).
 * Why: Keeps users engaged and warns them of session timeline updates in real-time.
 */
export async function checkSessionNotifications() {
  const now = Date.now();
  
  if (process.env.NODE_ENV === 'development') {
    console.log('Watchdog Cron: Auditing active focus session notifications...');
  }

  try {
    const activeSessionsQuery = await query(
      "SELECT * FROM sessions WHERE status = 'ACTIVE'"
    );
    const activeSessions = activeSessionsQuery.rows;

    for (const session of activeSessions) {
      const startTime = parseInt(session.start_time, 10);
      const targetEndTime = parseInt(session.target_end_time, 10);
      const duration = targetEndTime - startTime;

      // 1. Check for Halfway Mark Alert (elapsed time >= 50%)
      const halfwayMark = startTime + Math.floor(duration / 2);
      if (now >= halfwayMark && now < targetEndTime) {
        const flagKey = `lockin:notified:${session.session_id}:halfway`;
        
        // Use Redis to cache flag so warning fires exactly once per session
        const alreadyNotified = await redisClient.get(flagKey);
        if (!alreadyNotified) {
          await redisClient.set(flagKey, 'true', { EX: 24 * 60 * 60 });
          
          await sendPushNotification(
            session.user_id,
            'Halfway There!',
            'You have completed 50% of your focus session. Stay strong!',
            { 
              type: 'SESSION_HALFWAY', 
              sessionId: session.session_id 
            }
          );
        }
      }

      // 2. Check for 15-Minute Remaining Alert
      const fifteenMinMark = targetEndTime - (15 * 60 * 1000);
      if (now >= fifteenMinMark && now < targetEndTime) {
        const flagKey = `lockin:notified:${session.session_id}:15min`;
        
        const alreadyNotified = await redisClient.get(flagKey);
        if (!alreadyNotified) {
          await redisClient.set(flagKey, 'true', { EX: 24 * 60 * 60 });
          
          await sendPushNotification(
            session.user_id,
            '15 Minutes Remaining',
            'Almost done! 15 minutes left before your wallet funds are unlocked.',
            { 
              type: 'SESSION_15MIN_WARNING', 
              sessionId: session.session_id 
            }
          );
        }
      }
    }
  } catch (err) {
    console.error('Watchdog Cron: Error in active session notifications check:', err);
  }
}

/**
 * Sweeps the database for focus sessions that have missed heartbeats for > 10 minutes.
 * Why: Resolves sessions where the user uninstalls the app, shuts off their phone, or bypasses VPN.
 */
export async function checkMissedHeartbeats() {
  const now = Date.now();
  const MISSED_THRESHOLD_MS = 10 * 60 * 1000; // 10 minutes

  if (process.env.NODE_ENV === 'development') {
    console.log('Watchdog Cron: Checking for active sessions with missed heartbeats...');
  }

  try {
    const activeSessionsQuery = await query(
      `SELECT s.*, 
              COALESCE(MAX(e.timestamp), s.start_time) as last_heartbeat 
       FROM sessions s
       LEFT JOIN session_events e ON s.session_id = e.session_id AND e.event_type = 'HEARTBEAT'
       WHERE s.status = 'ACTIVE'
       GROUP BY s.session_id`
    );

    const activeSessions = activeSessionsQuery.rows;

    for (const session of activeSessions) {
      const lastHeartbeat = parseInt(session.last_heartbeat, 10);
      const elapsedSinceHeartbeat = now - lastHeartbeat;

      if (elapsedSinceHeartbeat > MISSED_THRESHOLD_MS) {
        console.warn(`Watchdog Cron: Session ${session.session_id} for user ${session.user_id} missed heartbeats for ${Math.round(elapsedSinceHeartbeat / 1000 / 60)} minutes. Processing break penalty.`);

        const client = await pool.connect();
        try {
          await client.query('BEGIN');

          // Fetch user's wallet with row lock
          const walletSelect = await client.query(
            'SELECT * FROM wallets WHERE user_id = $1 FOR UPDATE',
            [session.user_id]
          );

          if (walletSelect.rows.length > 0) {
            const wallet = walletSelect.rows[0];
            const penalty = session.penalty_amount;

            // Deduct held balance and increment paid penalties
            const newHeld = Math.max(0, wallet.held_balance - penalty);
            const newTotalPenaltiesPaid = wallet.total_penalties_paid + penalty;

            await client.query(
              `UPDATE wallets 
               SET held_balance = $1, total_penalties_paid = $2, last_updated = $3 
               WHERE user_id = $4`,
              [newHeld, newTotalPenaltiesPaid, now, session.user_id]
            );

            // Mark session status as BROKEN
            await client.query(
              `UPDATE sessions 
               SET status = 'BROKEN', actual_end_time = $1 
               WHERE session_id = $2`,
              [now, session.session_id]
            );

            // Log penalty debit transaction in ledger
            const txId = `tx_penalty_cron_${session.session_id}`;
            await client.query(
              `INSERT INTO wallet_transactions (
                tx_id, user_id, type, amount, direction, session_id, description, timestamp
              ) VALUES ($1, $2, 'PENALTY', $3, 'DEBIT', $4, $5, $6)`,
              [
                txId,
                session.user_id,
                'PENALTY',
                penalty,
                session.session_id,
                'Penalty charged due to missed heartbeats (watchdog cron)',
                now
              ]
            );

            // Log BREAK_CONFIRMED audit event
            const eventId = `evt_break_cron_${session.session_id}`;
            await client.query(
              `INSERT INTO session_events (
                event_id, session_id, event_type, timestamp, metadata
              ) VALUES ($1, $2, 'BREAK_CONFIRMED', $3, $4)`,
              [
                eventId,
                session.session_id,
                'BREAK_CONFIRMED',
                now,
                'Session automatically marked broken by server cron due to missed heartbeats (>10 min)'
              ]
            );

            await client.query('COMMIT');
            console.log(`Watchdog Cron: Charged penalty of ₹${penalty / 100} for broken session ${session.session_id}.`);

            // Clear the active session key in Redis cache
            try {
              await clearActiveSession(session.user_id);
            } catch (redisErr) {
              console.error('Watchdog Cron: Failed to clear active session in Redis:', redisErr);
            }

            // Send notification to warn user that the session has broken due to inactivity
            await sendPushNotification(
              session.user_id,
              'Session Broken early',
              `Session ended early. ₹${penalty / 100} penalty deducted due to missed heartbeats.`,
              { type: 'SESSION_BROKEN', sessionId: session.session_id }
            );
          } else {
            await client.query('ROLLBACK');
            console.error(`Watchdog Cron: Wallet for user ${session.user_id} not found.`);
          }
        } catch (txnErr) {
          await client.query('ROLLBACK');
          console.error(`Watchdog Cron: Failed to settle penalty for session ${session.session_id}:`, txnErr);
        } finally {
          client.release();
        }
      }
    }
  } catch (err) {
    console.error('Watchdog Cron: Error executing missed heartbeat monitor sweep:', err);
  }
}

/**
 * Starts the background watchdog timers.
 * Why: Binds the watchdog check and notification intervals to run periodically.
 */
export function startHeartbeatMonitor() {
  const WATCHDOG_INTERVAL_MS = 5 * 60 * 1000;    // 5 minutes
  const NOTIFICATION_INTERVAL_MS = 1 * 60 * 1000;  // 1 minute

  setInterval(checkMissedHeartbeats, WATCHDOG_INTERVAL_MS);
  setInterval(checkSessionNotifications, NOTIFICATION_INTERVAL_MS);
  
  if (process.env.NODE_ENV === 'development') {
    console.log('watchdog: Missed heartbeat watchdog monitor (5-min interval) & session alerts monitor (1-min interval) started.');
  }
}
