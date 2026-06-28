-- 00002_google_sign_in.sql
-- Drop existing tables and recreate them using UUID for user_id to support Google Sign-in.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Drop tables in reverse order of foreign key dependency
DROP TABLE IF EXISTS session_events CASCADE;
DROP TABLE IF EXISTS wallet_transactions CASCADE;
DROP TABLE IF EXISTS sessions CASCADE;
DROP TABLE IF EXISTS wallets CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 1. Recreate Users Table with UUID user_id and Google authentication fields
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_id VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    fcm_token TEXT NULL,
    platform VARCHAR(50) DEFAULT 'android',
    created_at TIMESTAMP DEFAULT NOW(),
    last_seen_at TIMESTAMP DEFAULT NOW()
);

-- 2. Recreate Wallets Table with UUID referencing users
CREATE TABLE wallets (
    user_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    available_balance INT DEFAULT 0,
    held_balance INT DEFAULT 0,
    total_deposited INT DEFAULT 0,
    total_penalties_paid INT DEFAULT 0,
    auto_top_up_enabled BOOLEAN DEFAULT TRUE,
    auto_top_up_threshold_paise INT DEFAULT 10000, -- 100 INR default threshold
    auto_top_up_amount_paise INT DEFAULT 20000,    -- 200 INR default topup
    last_updated BIGINT NOT NULL
);

-- 3. Recreate Sessions Table with UUID referencing users
CREATE TABLE sessions (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    start_time BIGINT NOT NULL,
    target_end_time BIGINT NOT NULL,
    actual_end_time BIGINT NULL,
    penalty_amount INT NOT NULL,
    currency VARCHAR(10) DEFAULT 'INR',
    wallet_tx_hold_id VARCHAR(255) NULL,
    allowlist_version INT NOT NULL,
    platform VARCHAR(50) DEFAULT 'android'
);

-- 4. Recreate Wallet Transactions Table with UUID referencing users
CREATE TABLE wallet_transactions (
    tx_id VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    direction VARCHAR(10) NOT NULL,
    session_id VARCHAR(255) NULL REFERENCES sessions(session_id) ON DELETE SET NULL,
    description TEXT NOT NULL,
    timestamp BIGINT NOT NULL
);

-- 5. Recreate Session Events Table
CREATE TABLE session_events (
    event_id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL REFERENCES sessions(session_id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    timestamp BIGINT NOT NULL,
    metadata TEXT NULL
);

-- Recreate indexes
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_wallet_transactions_user_id ON wallet_transactions(user_id);
CREATE INDEX idx_session_events_session_id ON session_events(session_id);
