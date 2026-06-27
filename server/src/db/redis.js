/*
 * File: server/src/db/redis.js
 * Purpose: Configures the Redis client and defines helpers for session tracking and daily top-up caps.
 * Uses official redis v4 client API.
 */

import { createClient } from 'redis';
import dotenv from 'dotenv';

dotenv.config();

const redisUrl = process.env.REDIS_URL || 'redis://localhost:6379';

// Instantiate Redis client
export const redisClient = createClient({
  url: redisUrl
});

redisClient.on('error', (err) => {
  console.error('Redis Client Error:', err);
});

redisClient.on('connect', () => {
  if (process.env.NODE_ENV === 'development') {
    console.log('Redis connection established successfully.');
  }
});

/**
 * Connects the Redis client to the server.
 * Why: Initializes connection programmatically on server startup.
 */
export async function connectRedis() {
  if (!redisClient.isOpen) {
    await redisClient.connect();
  }
}

/**
 * Gets the current date string formatted as YYYY-MM-DD.
 * Why: Used to namespace daily top-up counts in Redis keys.
 */
function getTodayDateString() {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Sets the active session details for a user in Redis.
 * Why: Avoids database polling for hot paths (e.g. heartbeat tracking) and enables fast lookups.
 *
 * @param {string} userId Unique user identifier.
 * @param {Object} session Session detail object containing startTime, targetEndTime, penaltyAmount, status.
 */
export async function setActiveSession(userId, session) {
  const key = `lockin:active-session:${userId}`;
  // Store session as stringified JSON
  await redisClient.set(key, JSON.stringify(session));
}

/**
 * Retrieves the active session details for a user.
 * Why: Fast lookup to verify if the user has a session running currently.
 *
 * @param {string} userId Unique user identifier.
 * @return {Promise<Object|null>} The parsed session object, or null if none active.
 */
export async function getActiveSession(userId) {
  const key = `lockin:active-session:${userId}`;
  const data = await redisClient.get(key);
  if (!data) return null;
  try {
    return JSON.parse(data);
  } catch (err) {
    console.error('Error parsing active session data from Redis:', err);
    return null;
  }
}

/**
 * Removes the active session key for a user.
 * Why: Cleans up Redis state when a session completes or is broken.
 *
 * @param {string} userId Unique user identifier.
 */
export async function clearActiveSession(userId) {
  const key = `lockin:active-session:${userId}`;
  await redisClient.del(key);
}

/**
 * Retrieves the number of auto top-ups completed by a user today.
 * Why: Validates the daily auto top-up limit (max 3/day).
 *
 * @param {string} userId Unique user identifier.
 * @return {Promise<number>} Current top-up count for today.
 */
export async function getDailyTopUpCount(userId) {
  const today = getTodayDateString();
  const key = `lockin:topup-count:${userId}:${today}`;
  const count = await redisClient.get(key);
  return count ? parseInt(count, 10) : 0;
}

/**
 * Increments the daily auto top-up counter for a user.
 * Why: Tracks total top-ups completed today; expires after 36 hours for automatic cleanup.
 *
 * @param {string} userId Unique user identifier.
 * @return {Promise<number>} Updated top-up count.
 */
export async function incrementDailyTopUpCount(userId) {
  const today = getTodayDateString();
  const key = `lockin:topup-count:${userId}:${today}`;
  
  const count = await redisClient.incr(key);
  if (count === 1) {
    // Set 36 hour TTL to ensure the key is cleaned up after the day ends
    await redisClient.expire(key, 36 * 60 * 60);
  }
  return count;
}
