/*
 * File: server/src/routes/walletRoutes.js
 * Purpose: Express router handling wallet balance queries, manual deposits, withdrawals, and auto top-ups.
 * Directly maps to Retrofit WalletApi interface in the Android client.
 */

import express from 'express';
import crypto from 'crypto';
import { query, pool } from '../db/index.js';
import { authenticateToken } from '../middleware/auth.js';
import { getActiveSession, getDailyTopUpCount, incrementDailyTopUpCount } from '../db/redis.js';
import { sendPushNotification } from '../utils/fcm.js';

const router = express.Router();


/**
 * Helper function to map database wallet row to WalletDto structure.
 */
export function mapToWalletDto(row) {
  return {
    userId: row.user_id,
    availableBalance: row.available_balance,
    heldBalance: row.held_balance,
    totalDeposited: row.total_deposited,
    totalPenaltiesPaid: row.total_penalties_paid,
    autoTopUpEnabled: row.auto_top_up_enabled,
    autoTopUpThresholdPaise: row.auto_top_up_threshold_paise,
    autoTopUpAmountPaise: row.auto_top_up_amount_paise,
    lastUpdated: parseInt(row.last_updated, 10)
  };
}

/**
 * Retrieves the authenticated user's wallet state.
 * Route: GET /wallet
 * Headers: Authorization: Bearer <token>
 * Response Body: WalletDto
 */
router.get('/', authenticateToken, async (req, res, next) => {
  const userId = req.user.userId;

  try {
    const result = await query('SELECT * FROM wallets WHERE user_id = $1', [userId]);

    if (result.rows.length === 0) {
      return res.status(404).json({
        success: false,
        message: 'Wallet not found for this user.'
      });
    }

    res.status(200).json(mapToWalletDto(result.rows[0]));
  } catch (err) {
    next(err);
  }
});

/**
 * Retrieves all wallet transactions for the authenticated user.
 * Route: GET /wallet/transactions
 * Headers: Authorization: Bearer <token>
 * Response Body: Array of WalletTransactionDto
 */
router.get('/transactions', authenticateToken, async (req, res, next) => {
  const userId = req.user.userId;

  try {
    const result = await query(
      'SELECT * FROM wallet_transactions WHERE user_id = $1 ORDER BY timestamp DESC',
      [userId]
    );

    const transactionDtos = result.rows.map(row => ({
      txId: row.tx_id,
      userId: row.user_id,
      type: row.type,
      amount: row.amount,
      direction: row.direction,
      sessionId: row.session_id,
      description: row.description,
      timestamp: parseInt(row.timestamp, 10)
    }));

    res.status(200).json(transactionDtos);
  } catch (err) {
    next(err);
  }
});

/**
 * Reports a manual Razorpay deposit to the server for verification and credits the wallet.
 * Route: POST /wallet/deposit
 * Headers: Authorization: Bearer <token>
 * Request Body: { razorpayPaymentId: string, amount: number }
 * Response Body: WalletDto
 */
router.post('/deposit', authenticateToken, async (req, res, next) => {
  const { razorpayPaymentId, amount } = req.body;
  const userId = req.user.userId;

  if (!razorpayPaymentId || amount === undefined) {
    return res.status(400).json({
      success: false,
      message: 'Missing required deposit parameters (razorpayPaymentId, amount).'
    });
  }

  if (amount <= 0) {
    return res.status(400).json({
      success: false,
      message: 'Deposit amount must be positive.'
    });
  }

  // Prevent double-processing (replay attacks) by checking if transaction already exists
  try {
    const txCheck = await query(
      'SELECT * FROM wallet_transactions WHERE tx_id = $1',
      [razorpayPaymentId]
    );
    if (txCheck.rows.length > 0) {
      return res.status(400).json({
        success: false,
        message: 'This payment has already been credited to the wallet.'
      });
    }
  } catch (err) {
    return next(err);
  }

  // Call Razorpay API to fetch payment details
  const keyId = process.env.RAZORPAY_KEY_ID;
  const keySecret = process.env.RAZORPAY_KEY_SECRET;

  if (!keyId || !keySecret || keyId === 'rzp_test_placeholder') {
    return res.status(500).json({
      success: false,
      message: 'Razorpay keys are not configured or invalid on the server.'
    });
  }

  try {
    const authString = Buffer.from(`${keyId}:${keySecret}`).toString('base64');
    const razorpayUrl = `https://api.razorpay.com/v1/payments/${razorpayPaymentId}`;

    const response = await fetch(razorpayUrl, {
      method: 'GET',
      headers: {
        'Authorization': `Basic ${authString}`,
        'Content-Type': 'application/json'
      }
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`Razorpay API responded with status ${response.status}:`, errorText);
      return res.status(400).json({
        success: false,
        message: 'Failed to verify payment with Razorpay. Invalid payment ID or credentials.'
      });
    }

    const paymentData = await response.json();

    // Verify payment status
    if (paymentData.status !== 'captured') {
      return res.status(400).json({
        success: false,
        message: `Payment status is '${paymentData.status}'. Only captured payments are credited.`
      });
    }

    // Verify payment amount (note: Razorpay amounts are also in paise)
    const razorpayAmount = parseInt(paymentData.amount, 10);
    if (razorpayAmount !== amount) {
      return res.status(400).json({
        success: false,
        message: `Payment amount mismatch. Client claimed: ${amount} paise, Razorpay recorded: ${razorpayAmount} paise.`
      });
    }

    // Verify currency
    if (paymentData.currency !== 'INR') {
      return res.status(400).json({
        success: false,
        message: `Payment currency mismatch. Expected INR, got ${paymentData.currency}.`
      });
    }

    // Perform database wallet update and transaction log in a transaction
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      // Fetch wallet FOR UPDATE to lock row
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
      const now = Date.now();
      const newAvailable = wallet.available_balance + amount;
      const newTotalDeposited = wallet.total_deposited + amount;

      // Update available balance and total deposited
      const walletUpdate = await client.query(
        `UPDATE wallets 
         SET available_balance = $1, total_deposited = $2, last_updated = $3 
         WHERE user_id = $4 
         RETURNING *`,
        [newAvailable, newTotalDeposited, now, userId]
      );

      // Insert transaction ledger record
      await client.query(
        `INSERT INTO wallet_transactions (
          tx_id, user_id, type, amount, direction, session_id, description, timestamp
        ) VALUES ($1, $2, 'DEPOSIT', $3, 'CREDIT', NULL, $4, $5)`,
        [
          razorpayPaymentId,
          userId,
          amount,
          'Manual Razorpay deposit',
          now
        ]
      );

      await client.query('COMMIT');

      const updatedWallet = walletUpdate.rows[0];
      res.status(200).json(mapToWalletDto(updatedWallet));
    } catch (dbErr) {
      await client.query('ROLLBACK');
      throw dbErr;
    } finally {
      client.release();
    }
  } catch (err) {
    next(err);
  }
});

/**
 * Executes server-side silent Razorpay charge for auto top-up (capped at 3 per day).
 * Route: POST /wallet/auto-topup
 * Headers: Authorization: Bearer <token>
 * Request Body: { amount: number }
 * Response Body: WalletDto
 */
router.post('/auto-topup', authenticateToken, async (req, res, next) => {
  const { amount } = req.body;
  const userId = req.user.userId;

  if (amount === undefined) {
    return res.status(400).json({
      success: false,
      message: 'Missing required auto top-up parameter (amount).'
    });
  }

  if (amount <= 0) {
    return res.status(400).json({
      success: false,
      message: 'Top-up amount must be positive.'
    });
  }

  // 1. Enforce: never trigger during an active session
  try {
    const cachedSession = await getActiveSession(userId);
    if (cachedSession) {
      return res.status(400).json({
        success: false,
        message: 'Auto top-up is not allowed during an active focus session.'
      });
    }
  } catch (err) {
    console.error('Failed to check active session in Redis during top-up:', err);
  }

  // Double check active session in PostgreSQL
  try {
    const sessionCheck = await query(
      "SELECT * FROM sessions WHERE user_id = $1 AND status = 'ACTIVE'",
      [userId]
    );
    if (sessionCheck.rows.length > 0) {
      return res.status(400).json({
        success: false,
        message: 'Auto top-up is not allowed during an active focus session.'
      });
    }
  } catch (err) {
    return next(err);
  }

  // 2. Enforce: daily cap of 3 top-ups
  try {
    const dailyCount = await getDailyTopUpCount(userId);
    if (dailyCount >= 3) {
      sendPushNotification(
        userId,
        'Auto Top-Up Limit Reached',
        'Daily auto top-up limit has been reached.',
        { type: 'DAILY_CAP' }
      );
      return res.status(400).json({
        success: false,
        message: 'Daily auto top-up limit (3) reached.'
      });
    }
  } catch (err) {
    console.error('Failed to check daily top-up limit in Redis:', err);
    return res.status(500).json({
      success: false,
      message: 'Failed to verify daily top-up constraints.'
    });
  }

  const keyId = process.env.RAZORPAY_KEY_ID;
  const keySecret = process.env.RAZORPAY_KEY_SECRET;

  if (!keyId || !keySecret || keyId === 'rzp_test_placeholder') {
    return res.status(500).json({
      success: false,
      message: 'Razorpay keys are not configured or invalid on the server.'
    });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 3. Fetch user's wallet with row lock and ensure auto top-up is enabled
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
    if (!wallet.auto_top_up_enabled) {
      await client.query('ROLLBACK');
      return res.status(400).json({
        success: false,
        message: 'Auto top-up feature is disabled for this wallet.'
      });
    }

    // 4. Retrieve user's latest payment instrument token (parent payment ID)
    const depositSelect = await client.query(
      `SELECT tx_id FROM wallet_transactions 
       WHERE user_id = $1 AND type = 'DEPOSIT' 
       ORDER BY timestamp DESC LIMIT 1`,
      [userId]
    );

    if (depositSelect.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(400).json({
        success: false,
        message: 'No saved payment instrument found on the server. Complete at least one manual deposit first.'
      });
    }

    const savedPaymentToken = depositSelect.rows[0].tx_id;

    // 5. Call Razorpay Token Charge API (Silent recurring transaction)
    const authString = Buffer.from(`${keyId}:${keySecret}`).toString('base64');
    const chargeUrl = 'https://api.razorpay.com/v1/payments/charge';

    const response = await fetch(chargeUrl, {
      method: 'POST',
      headers: {
        'Authorization': `Basic ${authString}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        amount,
        currency: 'INR',
        email: 'support@lockin.app',
        contact: '9999999999',
        token: savedPaymentToken
      })
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`Razorpay token charge failed with status ${response.status}:`, errorText);
      await client.query('ROLLBACK');
      
      sendPushNotification(
        userId,
        'Auto Top-Up Failed',
        'Auto top-up failed. Add money manually.',
        { type: 'AUTO_TOPUP_FAILURE' }
      );

      return res.status(400).json({
        success: false,
        message: 'Silent auto top-up transaction failed at payment gateway.'
      });
    }

    const chargeData = await response.json();
    const newPaymentId = chargeData.id;

    // 6. Update database wallet balances and log auto top-up ledger
    const now = Date.now();
    const newAvailable = wallet.available_balance + amount;
    const newTotalDeposited = wallet.total_deposited + amount;

    const walletUpdate = await client.query(
      `UPDATE wallets 
       SET available_balance = $1, total_deposited = $2, last_updated = $3 
       WHERE user_id = $4 
       RETURNING *`,
      [newAvailable, newTotalDeposited, now, userId]
    );

    await client.query(
      `INSERT INTO wallet_transactions (
        tx_id, user_id, type, amount, direction, session_id, description, timestamp
      ) VALUES ($1, $2, 'AUTO_TOPUP', $3, 'CREDIT', NULL, $4, $5)`,
      [
        newPaymentId,
        userId,
        amount,
        'Silent background Auto Top-Up charge',
        now
      ]
    );

    await client.query('COMMIT');

    // Increment daily top-up counter in Redis on success
    try {
      await incrementDailyTopUpCount(userId);
    } catch (redisErr) {
      console.error('Failed to increment daily top-up counter in Redis:', redisErr);
    }

    sendPushNotification(
      userId,
      'Wallet Topped Up',
      `Wallet topped up · ₹${amount / 100} added automatically`,
      { type: 'AUTO_TOPUP_SUCCESS' }
    );

    const updatedWallet = walletUpdate.rows[0];
    res.status(200).json(mapToWalletDto(updatedWallet));
  } catch (err) {
    await client.query('ROLLBACK');
    next(err);
  } finally {
    client.release();
  }
});

/**
 * Initiates a wallet balance withdrawal by issuing a refund on the user's latest Razorpay deposit.
 * Route: POST /wallet/withdraw
 * Headers: Authorization: Bearer <token>
 * Request Body: { amount: number }
 * Response Body: WalletDto
 */
router.post('/withdraw', authenticateToken, async (req, res, next) => {
  const { amount } = req.body;
  const userId = req.user.userId;

  if (amount === undefined) {
    return res.status(400).json({
      success: false,
      message: 'Missing required withdrawal parameter (amount).'
    });
  }

  // Minimum withdrawal limit of ₹50 (5000 paise)
  if (amount < 5000) {
    return res.status(400).json({
      success: false,
      message: 'Minimum withdrawal amount is ₹50 (5000 paise).'
    });
  }

  const keyId = process.env.RAZORPAY_KEY_ID;
  const keySecret = process.env.RAZORPAY_KEY_SECRET;

  if (!keyId || !keySecret || keyId === 'rzp_test_placeholder') {
    return res.status(500).json({
      success: false,
      message: 'Razorpay keys are not configured or invalid on the server.'
    });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 1. Fetch user's wallet with row lock
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

    // 2. Validate sufficient available balance (held balance cannot be withdrawn)
    if (wallet.available_balance < amount) {
      await client.query('ROLLBACK');
      return res.status(400).json({
        success: false,
        message: `Insufficient available balance. Required: ₹${amount / 100}, Available: ₹${wallet.available_balance / 100}`
      });
    }

    // 3. Find the user's latest successful deposit transaction ID (to refund against)
    const depositSelect = await client.query(
      `SELECT tx_id FROM wallet_transactions 
       WHERE user_id = $1 AND type = 'DEPOSIT' 
       ORDER BY timestamp DESC LIMIT 1`,
      [userId]
    );

    if (depositSelect.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(400).json({
        success: false,
        message: 'No previous deposit transactions found to process refund against.'
      });
    }

    const parentPaymentId = depositSelect.rows[0].tx_id;

    // 4. Call Razorpay Refund API
    const authString = Buffer.from(`${keyId}:${keySecret}`).toString('base64');
    const refundUrl = `https://api.razorpay.com/v1/payments/${parentPaymentId}/refund`;

    const response = await fetch(refundUrl, {
      method: 'POST',
      headers: {
        'Authorization': `Basic ${authString}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ amount })
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`Razorpay Refund API failed with status ${response.status}:`, errorText);
      await client.query('ROLLBACK');
      return res.status(400).json({
        success: false,
        message: 'Refund failed with Razorpay gateway. Ensure deposit payment has not been fully refunded.'
      });
    }

    const refundData = await response.json();
    const refundId = refundData.id;

    // 5. Update database wallet balances and log withdrawal ledger
    const now = Date.now();
    const newAvailable = wallet.available_balance - amount;

    const walletUpdate = await client.query(
      `UPDATE wallets 
       SET available_balance = $1, last_updated = $2 
       WHERE user_id = $3 
       RETURNING *`,
      [newAvailable, now, userId]
    );

    await client.query(
      `INSERT INTO wallet_transactions (
        tx_id, user_id, type, amount, direction, session_id, description, timestamp
      ) VALUES ($1, $2, 'WITHDRAWAL', $3, 'DEBIT', NULL, $4, $5)`,
      [
        refundId,
        userId,
        amount,
        `Withdrawal refund processed on payment ${parentPaymentId}`,
        now
      ]
    );

    await client.query('COMMIT');

    const updatedWallet = walletUpdate.rows[0];
    res.status(200).json(mapToWalletDto(updatedWallet));
  } catch (err) {
    await client.query('ROLLBACK');
    next(err);
  } finally {
    client.release();
  }
});

/**
 * Public endpoint to handle verified Razorpay webhook notifications.
 * Route: POST /wallet/webhook
 * Headers: X-Razorpay-Signature: <hmac-signature>
 * Response Body: { success: boolean, message: string }
 */
router.post('/webhook', async (req, res, next) => {
  const signature = req.headers['x-razorpay-signature'];
  const webhookSecret = process.env.RAZORPAY_WEBHOOK_SECRET;

  if (!signature) {
    return res.status(400).json({
      success: false,
      message: 'Missing Razorpay signature header.'
    });
  }

  if (!webhookSecret || webhookSecret === 'rzp_webhook_secret_placeholder') {
    console.warn('Webhook secret is not configured or is placeholder. Webhook processing skipped.');
    return res.status(500).json({
      success: false,
      message: 'Webhook verification secret not configured.'
    });
  }

  // 1. Verify HMAC Webhook signature using raw payload buffer
  const computedSignature = crypto
    .createHmac('sha256', webhookSecret)
    .update(req.rawBody)
    .digest('hex');

  if (computedSignature !== signature) {
    console.warn('CRITICAL: Razorpay webhook signature verification failed.');
    return res.status(400).json({
      success: false,
      message: 'Invalid webhook signature.'
    });
  }

  const { event, payload } = req.body;

  // 2. Process only captured payment events
  if (event === 'payment.captured') {
    const payment = payload.payment.entity;
    const paymentId = payment.id;
    const amount = payment.amount; // in paise
    
    // User ID must be passed in Razorpay notes metadata (Task 20.8 / 20.11)
    const userId = payment.notes ? payment.notes.userId : null;

    if (!userId) {
      console.warn(`Webhook ignored: Payment ${paymentId} captured with no userId in notes metadata.`);
      return res.status(200).json({
        success: true,
        message: 'Ignored: No userId metadata in payment notes.'
      });
    }

    // 3. Prevent replay attacks (duplicate credits)
    try {
      const txCheck = await query(
        'SELECT * FROM wallet_transactions WHERE tx_id = $1',
        [paymentId]
      );
      if (txCheck.rows.length > 0) {
        console.log(`Webhook ignored: Deposit transaction ${paymentId} already processed.`);
        return res.status(200).json({
          success: true,
          message: 'Webhook duplicate already processed.'
        });
      }
    } catch (err) {
      return next(err);
    }

    // 4. Update wallet balance in database
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      const walletSelect = await client.query(
        'SELECT * FROM wallets WHERE user_id = $1 FOR UPDATE',
        [userId]
      );

      if (walletSelect.rows.length === 0) {
        await client.query('ROLLBACK');
        console.error(`Webhook error: Wallet not found for user ${userId}.`);
        return res.status(404).json({
          success: false,
          message: 'Wallet not found.'
        });
      }

      const wallet = walletSelect.rows[0];
      const now = Date.now();
      const newAvailable = wallet.available_balance + amount;
      const newTotalDeposited = wallet.total_deposited + amount;

      await client.query(
        `UPDATE wallets 
         SET available_balance = $1, total_deposited = $2, last_updated = $3 
         WHERE user_id = $4`,
        [newAvailable, newTotalDeposited, now, userId]
      );

      // Record transaction log entry
      await client.query(
        `INSERT INTO wallet_transactions (
          tx_id, user_id, type, amount, direction, session_id, description, timestamp
        ) VALUES ($1, $2, 'DEPOSIT', $3, 'CREDIT', NULL, $4, $5)`,
        [
          paymentId,
          userId,
          amount,
          'Razorpay webhook auto-deposit',
          now
        ]
      );

      await client.query('COMMIT');
      console.log(`Webhook success: Credited ₹${amount / 100} to user ${userId} wallet.`);
    } catch (dbErr) {
      await client.query('ROLLBACK');
      return next(dbErr);
    } finally {
      client.release();
    }
  }

  res.status(200).json({
    success: true,
    message: 'Webhook processed successfully.'
  });
});

export default router;
