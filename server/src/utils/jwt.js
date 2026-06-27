/*
 * File: server/src/utils/jwt.js
 * Purpose: Provides functions to sign and verify JWT tokens.
 * Secures subsequent client requests by encoding the userId in the token payload.
 */

import jwt from 'jsonwebtoken';
import dotenv from 'dotenv';

dotenv.config();

const JWT_SECRET = process.env.JWT_SECRET || 'lockin_dev_secret_key_1234567890';
const JWT_EXPIRES_IN = process.env.JWT_EXPIRES_IN || '30d';

/**
 * Generates a signed JWT token containing the user's ID.
 * Why: Authenticates the user on subsequent API calls after device registration.
 *
 * @param {string} userId Unique user identifier.
 * @return {string} Signed JWT.
 */
export function generateToken(userId) {
  return jwt.sign({ userId }, JWT_SECRET, {
    expiresIn: JWT_EXPIRES_IN
  });
}

/**
 * Verifies a JWT token and decodes its payload.
 * Why: Ensures that authorization tokens presented by the client are valid and un-tampered.
 *
 * @param {string} token Signed JWT token from Authorization header.
 * @return {Object} Decoded payload containing the userId.
 */
export function verifyToken(token) {
  return jwt.verify(token, JWT_SECRET);
}
