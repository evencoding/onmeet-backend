CREATE TABLE IF NOT EXISTS `companies` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `status` VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `job_titles` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `company_id` BIGINT NOT NULL,
    `is_default` BOOLEAN NOT NULL,
    CONSTRAINT `fk_job_titles_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `email` VARCHAR(255) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `employee_id` VARCHAR(255),
    `company_id` BIGINT NOT NULL,
    `job_title_id` BIGINT,
    `status` VARCHAR(255) NOT NULL,
    CONSTRAINT `fk_users_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
    CONSTRAINT `fk_users_job_title` FOREIGN KEY (`job_title_id`) REFERENCES `job_titles` (`id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `user_roles` (
    `user_id` BIGINT NOT NULL,
    `role` VARCHAR(255) NOT NULL,
    CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `teams` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `company_id` BIGINT NOT NULL,
    `leader_id` BIGINT,
    `name` VARCHAR(255) NOT NULL,
    `description` VARCHAR(255),
    `color` VARCHAR(255) NOT NULL,
    `status` VARCHAR(255) NOT NULL,
    CONSTRAINT `fk_teams_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
    CONSTRAINT `fk_teams_leader` FOREIGN KEY (`leader_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `invitations` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    `email` VARCHAR(255) NOT NULL UNIQUE,
    `code` VARCHAR(255) NOT NULL,
    `role` VARCHAR(255) NOT NULL,
    `company_id` BIGINT NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    CONSTRAINT `fk_invitations_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `user_teams` (
    `user_id` BIGINT NOT NULL,
    `team_id` BIGINT NOT NULL,
    PRIMARY KEY (`user_id`, `team_id`),
    CONSTRAINT `fk_user_teams_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_user_teams_team` FOREIGN KEY (`team_id`) REFERENCES `teams` (`id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `server_keys` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `created_at` DATETIME(6) NOT NULL,
    `public_key` TEXT NOT NULL,
    `encrypted_private_key` TEXT NOT NULL
) ENGINE=InnoDB;
