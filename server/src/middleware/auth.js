/*
 * File: server/src/middleware/auth.js
 * Purpose: Express middleware to verify incoming JWT bearer authorization headers.
 * Populates req.user with the validated userId or rejects with a 401 Unauthorized response.
 */

import { verifyToken } from '../utils/jwt.js';

/**
 * Authentication middleware function.
 * Why: Secures API routes by rejecting requests without a valid, signed authentication token.
 *
 * @param {Object} req Express request object.
 * @param {Object} res Express response object.
 * @param {Function} next Next middleware function callback.
 */
export function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  
  // Authorization header must be present and follow 'Bearer <token>' format
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({
      success: false,
      message: 'Access denied. Missing or malformed authorization token.'
    });
  }

  const token = authHeader.substring(7); // Remove 'Bearer ' prefix

  try {
    const decoded = verifyToken(token);
    
    // Attach decoded token details (e.g. userId) to request object
    req.user = decoded;
    next();
  } catch (err) {
    return res.status(401).json({
      success: false,
      message: 'Access denied. Invalid or expired token.'
    });
  }
}
