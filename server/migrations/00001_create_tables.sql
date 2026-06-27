-- 00001_create_tables.sql
-- Create initial schemas for LockIn focus detox server

-- 1. Users Table
-- Why: Stores central user records identified by device/account registrations.
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(255) PRIMARY KEY,
    fcm_token TEXT NULL,
    platform VARCHAR(50) DEFAULT 'android',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Wallets Table
-- Why: Tracks available and held balances for lock session penalties.
CREATE TABLE IF NOT EXISTS wallets (
    user_id VARCHAR(255) PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    available_balance INT DEFAULT 0, -- stored in paise (1 INR = 100 paise)
    held_balance INT DEFAULT 0,      -- stored in paise
    total_deposited INT DEFAULT 0,   -- cumulative deposits in paise
    total_penalties_paid INT DEFAULT 0, -- cumulative consumed penalties in paise
    auto_top_up_enabled BOOLEAN DEFAULT TRUE,
    auto_top_up_threshold_paise INT DEFAULT 0,
    auto_top_up_amount_paise INT DEFAULT 0,
    last_updated BIGINT NOT NULL      -- millisecond epoch timestamp
);

-- 3. Sessions Table
-- Why: Logs historical and active detox lock-in sessions.
CREATE TABLE IF NOT EXISTS sessions (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL, -- PENDING, ACTIVE, COMPLETED, BROKEN
    start_time BIGINT NOT NULL,  -- millisecond epoch timestamp
    target_end_time BIGINT NOT NULL, -- millisecond epoch timestamp
    actual_end_time BIGINT NULL,     -- millisecond epoch timestamp
    penalty_amount INT NOT NULL, -- stored in paise
    currency VARCHAR(10) DEFAULT 'INR',
    wallet_tx_hold_id VARCHAR(255) NULL,
    allowlist_version INT NOT NULL,
    platform VARCHAR(50) DEFAULT 'android'
);

-- 4. Wallet Transactions Table
-- Why: Financial audit ledger for deposits, withdrawals, holds, releases, and penalties.
CREATE TABLE IF NOT EXISTS wallet_transactions (
    tx_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL, -- DEPOSIT, AUTO_TOPUP, SESSION_HOLD, SESSION_RELEASE, PENALTY, WITHDRAWAL
    amount INT NOT NULL, -- in paise
    direction VARCHAR(10) NOT NULL, -- CREDIT or DEBIT
    session_id VARCHAR(255) NULL REFERENCES sessions(session_id) ON DELETE SET NULL,
    description TEXT NOT NULL,
    timestamp BIGINT NOT NULL   -- millisecond epoch timestamp
);

-- 5. Session Events Table
-- Why: Heartbeats and security breach events (e.g. VPN gaps, break attempts) recorded during sessions.
CREATE TABLE IF NOT EXISTS session_events (
    event_id VARCHAR(255) PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL REFERENCES sessions(session_id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL, -- HEARTBEAT, VPN_GAP, BREAK_ATTEMPT, BREAK_CONFIRMED, COMPLETED
    timestamp BIGINT NOT NULL,      -- millisecond epoch timestamp
    metadata TEXT NULL
);

-- Database Performance Indexes
-- Why: Optimize foreign key joins, user details queries, and event audits.
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_user_id ON wallet_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_session_events_session_id ON session_events(session_id);
