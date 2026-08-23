-- Unify role system: migrate User.Role to CompanyRole.Role
-- User.Role values: ADMIN, MANAGER, OPERATOR, MEMBER, VIEWER
-- CompanyRole.Role values: OWNER, ADMIN, MANAGER, USER

-- Step 1: Migrate existing User.role data to company_roles table
-- Users with a company get a CompanyRole based on their User.Role
INSERT INTO company_roles (id, company_id, user_id, role, created_at)
SELECT
    gen_random_uuid(),
    u.company_id,
    u.id,
    CASE
        WHEN u.role = 'ADMIN' THEN 'OWNER'
        WHEN u.role = 'MANAGER' THEN 'MANAGER'
        WHEN u.role IN ('OPERATOR', 'MEMBER', 'VIEWER') THEN 'USER'
        ELSE 'USER'
    END::varchar,
    COALESCE(u.created_at, NOW())
FROM users u
WHERE u.company_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM company_roles cr
    WHERE cr.company_id = u.company_id AND cr.user_id = u.id
  );

-- Step 2: Drop the role column from users table
ALTER TABLE users DROP COLUMN IF EXISTS role;
