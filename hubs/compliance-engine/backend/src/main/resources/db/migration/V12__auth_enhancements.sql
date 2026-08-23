-- V12: Auth enhancements — email verification, password reset, role expansion

-- Add email verification columns
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_token VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_token VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_expires TIMESTAMP;

-- Add unique constraints
ALTER TABLE users ADD CONSTRAINT uk_users_verification_token UNIQUE (verification_token);
ALTER TABLE users ADD CONSTRAINT uk_users_password_reset_token UNIQUE (password_reset_token);

-- Expand role enum to include MANAGER and OPERATOR
-- PostgreSQL: alter the column type to accept new values
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(20);
