/*
 * File: server/src/utils/fcm.js
 * Purpose: Firebase Cloud Messaging service client.
 * Handles SDK initialization and sends visual push notifications to client devices.
 */

import admin from 'firebase-admin';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { query } from '../db/index.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

let appInitialized = false;

/**
 * Initializes the Firebase Admin SDK.
 * Why: Establishes a secure connection to Firebase to authorize push messaging.
 */
export function initFirebase() {
  try {
    const saPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || 'configs/firebase-service-account.json';
    const resolvedPath = path.resolve(__dirname, '../../', saPath);

    if (fs.existsSync(resolvedPath)) {
      const serviceAccount = JSON.parse(fs.readFileSync(resolvedPath, 'utf8'));
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
      appInitialized = true;
      console.log('Firebase Admin SDK initialized successfully.');
    } else {
      console.warn(`Firebase service account file not found at [${resolvedPath}]. FCM push notifications running in MOCK mode.`);
    }
  } catch (err) {
    console.error('CRITICAL: Failed to initialize Firebase Admin SDK:', err);
  }
}

/**
 * Dispatches a push notification message to a specific user's registered device.
 * Why: Alerts users of important detox session lifecycle transitions and wallet updates.
 *
 * @param {string} userId Unique user identifier.
 * @param {string} title Notification header text.
 * @param {string} body Notification body content.
 * @param {Object} data Custom key-value pairs parsed by the client router.
 */
export async function sendPushNotification(userId, title, body, data = {}) {
  try {
    // 1. Retrieve the registered FCM token for this user from database
    const result = await query('SELECT fcm_token FROM users WHERE user_id = $1', [userId]);
    
    if (result.rows.length === 0 || !result.rows[0].fcm_token) {
      console.log(`[FCM Mock Log] User ${userId} has no registered FCM token. Skipping notification: "${title}" - "${body}"`);
      return;
    }

    const fcmToken = result.rows[0].fcm_token;

    // 2. Dispatch using real Firebase client or mock logs
    if (appInitialized) {
      const message = {
        token: fcmToken,
        notification: {
          title,
          body
        },
        data: data
      };
      
      const response = await admin.messaging().send(message);
      console.log(`Successfully dispatched FCM notification to user ${userId}:`, response);
    } else {
      console.log(`[FCM Mock Log] Send push to user ${userId} (Token: ${fcmToken}):`);
      console.log(`  Title: ${title}`);
      console.log(`  Body: ${body}`);
      console.log(`  Data:`, data);
    }
  } catch (err) {
    console.error(`Failed to dispatch FCM push notification to user ${userId}:`, err);
  }
}
