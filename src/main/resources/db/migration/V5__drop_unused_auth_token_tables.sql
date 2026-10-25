-- The JWT refresh/reset-token flow was removed when authentication moved to HTTP Basic.
-- These tables are no longer mapped by any entity, so drop them to keep the schema clean.
DROP TABLE IF EXISTS "RefreshToken";
DROP TABLE IF EXISTS "PasswordResetToken";
