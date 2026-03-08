-- Add isPasswordReset and fcmDeviceToken columns to users table
ALTER TABLE users
    ADD COLUMN is_password_reset BOOLEAN NOT NULL DEFAULT FALSE COMMENT '비밀번호 초기화 여부 (임시 비밀번호 발급 시 true)',
    ADD COLUMN fcm_device_token VARCHAR(512) NULL COMMENT 'FCM 디바이스 토큰';

-- Add index on fcmDeviceToken for better query performance
CREATE INDEX idx_users_fcm_device_token ON users(fcm_device_token);
