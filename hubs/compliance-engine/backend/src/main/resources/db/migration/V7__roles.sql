-- Add role column to users table
ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'ADMIN';

-- Set all existing users as ADMIN
UPDATE users SET role = 'ADMIN' WHERE role IS NULL;
