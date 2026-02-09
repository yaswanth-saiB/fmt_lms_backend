-- ============================================
-- FMT APP DATABASE SCHEMA
-- PostgreSQL with ENUM types
-- ============================================

-- Create ENUM types (PostgreSQL native enums)
CREATE TYPE user_role_enum AS ENUM ('STUDENT', 'MENTOR', 'ADMIN');
CREATE TYPE gender_enum AS ENUM ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY');

-- ============================================
-- USERS TABLE (Main table)
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Personal Information
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    
    -- Enums (PostgreSQL enums for double safety)
    user_role user_role_enum DEFAULT 'STUDENT',
    gender gender_enum,
    
    -- Contact Information
    phone_number VARCHAR(20),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),
    
    -- Status Flags
    is_active BOOLEAN DEFAULT FALSE,
    is_email_verified BOOLEAN DEFAULT FALSE,
    is_mobile_verified BOOLEAN DEFAULT FALSE,
    
    -- Timestamps for verification
    email_verified_at TIMESTAMP,
    mobile_verified_at TIMESTAMP,
    
    -- Security Tracking
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45), -- IPv6 max length
    failed_login_attempts INTEGER DEFAULT 0,
    account_locked_until TIMESTAMP,
    last_password_change_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- System Timestamps (automatically managed by Spring)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_password_length CHECK (length(password) >= 8),
    CONSTRAINT chk_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

-- ============================================
-- EMAIL VERIFICATION TOKENS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign Key with cascade delete
    CONSTRAINT fk_user_email_token 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE,
    
    -- Token should be unique per user
    CONSTRAINT unique_user_token UNIQUE(user_id, token)
);

-- ============================================
-- REFRESH TOKENS TABLE (For JWT refresh)
-- ============================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    device_fingerprint VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign Key
    CONSTRAINT fk_user_refresh_token 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE
);

-- ============================================
-- INDEXES FOR PERFORMANCE
-- ============================================

-- Users table indexes
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(user_role);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(is_active);
CREATE INDEX IF NOT EXISTS idx_users_created ON users(created_at DESC);

-- Token table indexes
CREATE INDEX IF NOT EXISTS idx_email_tokens_token ON email_verification_tokens(token);
CREATE INDEX IF NOT EXISTS idx_email_tokens_user ON email_verification_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_email_tokens_expiry ON email_verification_tokens(expires_at);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expiry ON refresh_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked ON refresh_tokens(revoked);

-- ============================================
-- FUNCTIONS & TRIGGERS (Optional but useful)
-- ============================================

-- Function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger for users table
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- SAMPLE DATA FOR TESTING (Optional)
-- ============================================
INSERT INTO users (
    first_name, 
    last_name, 
    email, 
    password, 
    user_role, 
    gender,
    phone_number,
    is_active,
    is_email_verified
) VALUES 
(
    'Admin',
    'User',
    'admin@tradingapp.com',
    '$2a$12$YourHashedPasswordHere', -- Will be replaced with actual hash
    'ADMIN',
    'MALE',
    '+1234567890',
    TRUE,
    TRUE
) ON CONFLICT (email) DO NOTHING;

INSERT INTO users (
    first_name, 
    last_name, 
    email, 
    password, 
    user_role, 
    gender,
    phone_number
) VALUES 
(
    'Test',
    'Student',
    'student@test.com',
    '$2a$12$YourHashedPasswordHere',
    'STUDENT',
    'FEMALE',
    '+9876543210'
) ON CONFLICT (email) DO NOTHING;