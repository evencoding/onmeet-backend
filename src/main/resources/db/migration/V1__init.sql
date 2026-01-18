USE `onmeet`;

CREATE DATABASE IF NOT EXISTS `onmeet`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

SET FOREIGN_KEY_CHECKS = 0;

# -- Drop (reverse order)
DROP TABLE IF EXISTS notification_streams;
DROP TABLE IF EXISTS notification_recipients;
DROP TABLE IF EXISTS notifications;

DROP TABLE IF EXISTS tokens;

DROP TABLE IF EXISTS voice_segments;
DROP TABLE IF EXISTS chat_messages;

DROP TABLE IF EXISTS minutes_generation_jobs;
DROP TABLE IF EXISTS minutes;

DROP TABLE IF EXISTS meeting_guests;
DROP TABLE IF EXISTS meeting_participants;
DROP TABLE IF EXISTS meetings;

DROP TABLE IF EXISTS team_members;
DROP TABLE IF EXISTS teams;

DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS companies;

DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- =========================================================
-- 1) users
-- =========================================================
CREATE TABLE users
(
    id                CHAR(36)                               NOT NULL,
    email             VARCHAR(255)                           NOT NULL,
    password_hash     VARCHAR(255)                           NOT NULL,
    name              VARCHAR(100)                                    DEFAULT NULL,
    status            ENUM ('ACTIVE','INACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    profile_image_url VARCHAR(512)                                    DEFAULT NULL,
    created_at        DATETIME(3)                            NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)                            NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =========================================================
-- 2) companies / employees (optional, but your original SQL had it)
-- =========================================================
CREATE TABLE companies
(
    id         CHAR(36)                   NOT NULL,
    name       VARCHAR(255)                        DEFAULT NULL,
    domain     VARCHAR(255)                        DEFAULT NULL,
    status     ENUM ('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3)                NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)                NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_companies_domain (domain)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE employees
(
    id          CHAR(36)                         NOT NULL,
    user_id     CHAR(36)                         NOT NULL,
    company_id  CHAR(36)                         NOT NULL,
    role        ENUM ('OWNER','ADMIN','MEMBER')  NOT NULL DEFAULT 'MEMBER',
    employee_no VARCHAR(50)                               DEFAULT NULL,
    status      ENUM ('ACTIVE','INVITED','LEFT') NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(3)                      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_employees_user_company (user_id, company_id),
    KEY idx_employees_company (company_id),
    CONSTRAINT fk_employees_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_employees_company
        FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =========================================================
-- 3) teams / team_members
-- =========================================================
CREATE TABLE teams
(
    id          CHAR(36)     NOT NULL,
    company_id  CHAR(36)              DEFAULT NULL,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500)          DEFAULT NULL,
    color       VARCHAR(30)           DEFAULT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_teams_company (company_id),
    CONSTRAINT fk_teams_company
        FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE team_members
(
    id         CHAR(36)                         NOT NULL,
    team_id    CHAR(36)                         NOT NULL,
    user_id    CHAR(36)                         NOT NULL,
    role       ENUM ('HOST','MEMBER')           NOT NULL DEFAULT 'MEMBER',
    status     ENUM ('ACTIVE','INVITED','LEFT') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3)                      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_members_team_user (team_id, user_id),
    KEY idx_team_members_user (user_id),
    CONSTRAINT fk_team_members_team
        FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =========================================================
-- 4) meetings / participants / guests  (REVISED)
-- =========================================================
CREATE TABLE meetings
(
    id           CHAR(36)     NOT NULL,
    team_id      CHAR(36)     DEFAULT NULL,
    -- 나중에 NOT NULL + FK 권장
    host_user_id CHAR(36)     DEFAULT NULL,

    title        VARCHAR(200)  NOT NULL,
    description  VARCHAR(1000) DEFAULT NULL,
    meet_tag     VARCHAR(100)  DEFAULT NULL,

    scheduled_at DATETIME(3)   DEFAULT NULL,
    started_at   DATETIME(3)   DEFAULT NULL,
    ended_at     DATETIME(3)   DEFAULT NULL,

    is_recording TINYINT(1)    NOT NULL DEFAULT 0,

    -- DB ENUM 제거: 자바 enum + STRING 저장
    status       VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',

    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_meetings_team (team_id),
    KEY idx_meetings_host (host_user_id),
    KEY idx_meetings_scheduled_at (scheduled_at)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE meeting_participants
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,

    meeting_id  CHAR(36)  NOT NULL,

    -- guests는 별도 테이블이므로 participant는 회원만: NOT NULL 권장
    user_id     CHAR(36)  NOT NULL,

    join_status VARCHAR(20) NOT NULL DEFAULT 'INVITED',

    mic_on      TINYINT(1) NOT NULL DEFAULT 0,
    cam_on      TINYINT(1) NOT NULL DEFAULT 0,

    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE KEY uk_meeting_participants_meeting_user (meeting_id, user_id),
    KEY idx_meeting_participants_user (user_id),
    KEY idx_meeting_participants_meeting_created (meeting_id, created_at),

    CONSTRAINT fk_meeting_participants_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE meeting_guests
(
    id          CHAR(36)      NOT NULL,
    meeting_id  CHAR(36)      NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(300)  DEFAULT NULL,

    -- DB ENUM 제거
    join_status VARCHAR(20)   NOT NULL DEFAULT 'JOINED',

    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_meeting_guests_meeting (meeting_id),

    CONSTRAINT fk_meeting_guests_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- =========================================================
-- 5) minutes / generation job
-- =========================================================
CREATE TABLE minutes
(
    id           CHAR(36)                                         NOT NULL,
    meeting_id   CHAR(36)                                         NOT NULL,
    status       ENUM ('DRAFT','COMPLETED','PUBLISHED','PRIVATE') NOT NULL DEFAULT 'DRAFT',
    summary_text LONGTEXT                                                  DEFAULT NULL,
    decisions    LONGTEXT                                                  DEFAULT NULL,
    keywords     JSON                                                      DEFAULT NULL,
    created_at   DATETIME(3)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    published_at DATETIME(3)                                               DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_minutes_meeting (meeting_id),
    CONSTRAINT fk_minutes_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE minutes_generation_jobs
(
    id             CHAR(36)                                      NOT NULL,
    meeting_id     CHAR(36)                                      NOT NULL,
    status         ENUM ('PENDING','PROCESSING','DONE','FAILED') NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT                                                   DEFAULT NULL,
    requested_at   DATETIME(3)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at   DATETIME(3)                                            DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_minutes_jobs_meeting (meeting_id),
    KEY idx_minutes_jobs_status (status),
    CONSTRAINT fk_minutes_jobs_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE chats (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       meet_room_id CHAR(36) NOT NULL,
                       sender_name VARCHAR(255) NOT NULL,
                       sender_type VARCHAR(50) NOT NULL,
                       message_content LONGTEXT NOT NULL,
                       message_type VARCHAR(50) NOT NULL,
                       created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                       KEY idx_chats_meetroom_createdat (meet_room_id, created_at)
);