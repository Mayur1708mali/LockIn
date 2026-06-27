/*
 * File: server/src/routes/userRoutes.js
 * Purpose: Express router for user management (registration, FCM token updates, account deletion).
 * Directly maps to Retrofit UserApi interface in the Android client.
 */

import express from 'express';
import { query, pool } from '../db/index.js';
import { generateToken } from '../utils/jwt.js';
import { authenticateToken } from '../middleware/auth.js';

const router = express.Router();

/**
 * Registers the device or logs in the user, initializing a default wallet.
 * Route: POST /users/register
 * Request Body: { deviceId: string, platform: string }
 * Response Body: { userId: string, token: string }
 */
router.post('/register', async (req, res, next) => {
  const { deviceId, platform } = req.body;

  if (!deviceId) {
    return res.status(400).json({
      success: false,
      message: 'Missing deviceId parameter.'
    });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // Check if the user already exists in PostgreSQL
    const userSelect = await client.query('SELECT * FROM users WHERE user_id = $1', [deviceId]);
    
    if (userSelect.rows.length === 0) {
      // User is registering for the first time
      const userPlatform = platform || 'android';
      await client.query(
        'INSERT INTO users (user_id, platform) VALUES ($1, $2)',
        [deviceId, userPlatform]
      );

      // Create a default empty wallet linked to this user
      const now = Date.now();
      await client.query(
        `INSERT INTO wallets (
          user_id, available_balance, held_balance, total_deposited, 
          total_penalties_paid, auto_top_up_enabled, 
          auto_top_up_threshold_paise, auto_top_up_amount_paise, last_updated
        ) VALUES ($1, 0, 0, 0, 0, true, 10000, 20000, $2)`,
        [deviceId, now]
      );
    } else {
      // Make sure the wallet exists in case of schema discrepancy or partial registration
      const walletSelect = await client.query('SELECT * FROM wallets WHERE user_id = $1', [deviceId]);
      if (walletSelect.rows.length === 0) {
        const now = Date.now();
        await client.query(
          `INSERT INTO wallets (
            user_id, available_balance, held_balance, total_deposited, 
            total_penalties_paid, auto_top_up_enabled, 
            auto_top_up_threshold_paise, auto_top_up_amount_paise, last_updated
          ) VALUES ($1, 0, 0, 0, 0, true, 10000, 20000, $2)`,
          [deviceId, now]
        );
      }
    }

    await client.query('COMMIT');

    // Generate authenticated JWT token for the client
    const token = generateToken(deviceId);

    res.status(200).json({
      userId: deviceId,
      token: token
    });
  } catch (err) {
    await client.query('ROLLBACK');
    next(err);
  } finally {
    client.release();
  }
});

/**
 * Saves or updates the Firebase Cloud Messaging registration token for push alerts.
 * Route: POST /users/fcm-token
 * Headers: Authorization: Bearer <token>
 * Request Body: { fcmToken: string }
 * Response Body: { success: boolean, message: string }
 */
router.post('/fcm-token', authenticateToken, async (req, res, next) => {
  const { fcmToken } = req.body;
  const userId = req.user.userId;

  if (!fcmToken) {
    return res.status(400).json({
      success: false,
      message: 'Missing fcmToken parameter.'
    });
  }

  try {
    const result = await query(
      'UPDATE users SET fcm_token = $1 WHERE user_id = $2',
      [fcmToken, userId]
    );

    if (result.rowCount === 0) {
      return res.status(404).json({
        success: false,
        message: 'User account not found.'
      });
    }

    res.status(200).json({
      success: true,
      message: 'FCM token successfully synchronized.'
    });
  } catch (err) {
    next(err);
  }
});

/**
 * Deletes the authenticated user account and all cascading data from the server.
 * Route: DELETE /users/account
 * Headers: Authorization: Bearer <token>
 * Response Body: { success: boolean, message: string }
 */
router.delete('/account', authenticateToken, async (req, res, next) => {
  const userId = req.user.userId;

  try {
    // Delete user; foreign keys will cascade and delete wallet, sessions, etc.
    const result = await query('DELETE FROM users WHERE user_id = $1', [userId]);

    if (result.rowCount === 0) {
      return res.status(404).json({
        success: false,
        message: 'User account not found.'
      });
    }

    res.status(200).json({
      success: true,
      message: 'User account and all associated data permanently deleted.'
    });
  } catch (err) {
    next(err);
  }
});

export default router;
