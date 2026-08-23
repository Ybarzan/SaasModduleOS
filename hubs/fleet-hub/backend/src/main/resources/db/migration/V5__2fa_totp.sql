-- 2FA / TOTP : colonnes de secret et flag d'activation sur app_user.

ALTER TABLE app_user ADD COLUMN totp_secret VARCHAR(64);
ALTER TABLE app_user ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
