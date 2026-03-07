CREATE TABLE IF NOT EXISTS `guest_invitations` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `uuid` VARCHAR(64) NOT NULL UNIQUE,
    `guest_email` VARCHAR(255) NOT NULL,
    `room_id` VARCHAR(255) NOT NULL,
    `host_name` VARCHAR(255) NOT NULL,
    `room_name` VARCHAR(255) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `expires_at` DATETIME(6) NOT NULL
) ENGINE=InnoDB;
