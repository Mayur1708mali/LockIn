/*
 * File: server/src/index.js
 * Purpose: Main entry point for the LockIn Express backend server.
 * Sets up middleware, health check, runs DB migrations, connects to Redis, mounts routes, starts watchdog jobs, initializes Firebase, and binds to the specified port.
 */

import express from 'express';
import dotenv from 'dotenv';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import { runMigrations } from './db/migrate.js';
import { connectRedis } from './db/redis.js';
import { startHeartbeatMonitor } from './jobs/heartbeatMonitor.js';
import { initFirebase } from './utils/fcm.js';

// Route imports
import userRoutes from './routes/userRoutes.js';
import sessionRoutes from './routes/sessionRoutes.js';
import walletRoutes from './routes/walletRoutes.js';
import authRoutes from './routes/auth.js';

// Load environment variables from .env file
dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;
const NODE_ENV = process.env.NODE_ENV || 'development';

// Security middleware to protect headers
app.use(helmet());

// CORS configuration to allow access from local/Android client requests
app.use(cors());

// Parse incoming request bodies in JSON format and capture raw body buffer for signature verification
app.use(express.json({
  verify: (req, res, buf) => {
    req.rawBody = buf;
  }
}));

// Log HTTP requests in development or production styles
if (NODE_ENV === 'development') {
  app.use(morgan('dev'));
} else {
  app.use(morgan('combined'));
}

/**
 * Health check endpoint.
 * Why: Allows client applications or hosting platforms (e.g. Render, Railway) to verify service uptime.
 */
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'UP',
    timestamp: new Date().toISOString(),
    env: NODE_ENV
  });
});

// Mount domain routes
app.use('/users', userRoutes);
app.use('/sessions', sessionRoutes);
app.use('/wallet', walletRoutes);
app.use('/auth', authRoutes);

/**
 * Fallback route handler for unmatched routes.
 * Why: Returns a consistent JSON error structure when a non-existent API route is called.
 */
app.use((req, res, next) => {
  res.status(404).json({
    success: false,
    message: `Route not found: ${req.method} ${req.originalUrl}`
  });
});

/**
 * Centralized error handler.
 * Why: Intercepts all unhandled errors in express routing and returns clean JSON instead of stack traces.
 */
app.use((err, req, res, next) => {
  console.error('Unhandled Server Error:', err);
  res.status(err.status || 500).json({
    success: false,
    message: NODE_ENV === 'development' ? err.message : 'Internal Server Error',
    error: NODE_ENV === 'development' ? err.stack : undefined
  });
});

/**
 * Starts database migrations, connects to Redis, mounts cron jobs, initializes Firebase, and starts listening on the configured PORT.
 * Why: Guarantees database schemas are up-to-date, caching is connected, and background audits run before handling active requests.
 */
async function startServer() {
  try {
    // Run database migrations before starting the network listener
    await runMigrations();

    // Connect to Redis instance
    await connectRedis();

    // Initialize Firebase Admin SDK for FCM pushes
    initFirebase();

    // Start server-side background watchdog for missed heartbeats and session warnings
    startHeartbeatMonitor();

    app.listen(PORT, () => {
      console.log('LockIn server successfully started on port ' + PORT + ' in [' + NODE_ENV + '] mode.');
    });
  } catch (error) {
    console.error('CRITICAL: Server startup failed:', error);
    process.exit(1);
  }
}

startServer();
