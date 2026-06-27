/*
 * File: server/src/db/index.js
 * Purpose: Configures and exports the node-postgres Pool for database connectivity.
 * Implements standard pool error handling and connection event logging.
 */

import pg from 'pg';
import dotenv from 'dotenv';

// Ensure environment variables are loaded
dotenv.config();

const { Pool } = pg;

const connectionString = process.env.DATABASE_URL;

if (!connectionString) {
  console.error('DATABASE_URL environment variable is missing.');
  process.exit(1);
}

// Create pg Pool instance using connection string
export const pool = new Pool({
  connectionString,
  // Disable SSL requirements for local development, enable in production if needed
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false
});

// Event listener for new connections established to PostgreSQL
pool.on('connect', () => {
  if (process.env.NODE_ENV === 'development') {
    console.log('PostgreSQL connection established successfully.');
  }
});

// Event listener for idle connection errors
pool.on('error', (err) => {
  console.error('Unexpected error on idle PostgreSQL client:', err);
});

/**
 * Executes a SQL query against the database pool.
 * Why: Standard helper method to quickly run queries without manual client checkout.
 *
 * @param {string} text SQL query string.
 * @param {Array} params Parameterized values.
 * @return {Promise<Object>} The query result object from pg.
 */
export const query = (text, params) => pool.query(text, params);
