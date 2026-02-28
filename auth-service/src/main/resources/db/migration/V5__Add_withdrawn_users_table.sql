CREATE TABLE IF NOT EXISTS `withdrawn_users` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `original_user_id` BIGINT NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `reason` VARCHAR(500),
    `withdrawn_at` DATETIME(6) NOT NULL
) ENGINE=InnoDB;
