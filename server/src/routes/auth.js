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
        displayName: user.display_name
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

export default router;
