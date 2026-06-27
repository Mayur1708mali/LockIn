/*
 * File: server/src/db/migrate.js
 * Purpose: Script to programmatically apply SQL schema migrations using postgres-migrations.
 * Ensures the database schema is kept up-to-date on application startup.
 */

import { migrate } from 'postgres-migrations';
import { pool } from './index.js';
import path from 'path';
import { fileURLToPath } from 'url';

// Resolve directory name in ES modules environment
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * Runs all database migrations defined in the /migrations directory.
 * Why: Guarantees database tables are created and modified automatically when the server runs.
 */
export async function runMigrations() {
  console.log('Starting PostgreSQL database migrations...');
  const client = await pool.connect();
  try {
    const migrationsDir = path.resolve(__dirname, '../../migrations');
    
    // Runs raw SQL files sequentially, logging operations
    await migrate({ client }, migrationsDir);
    console.log('PostgreSQL database migrations applied successfully.');
  } catch (err) {
    console.error('CRITICAL: Database migration failed to execute:', err);
    throw err;
  } finally {
    // Release client back into connection pool
    client.release();
  }
}
