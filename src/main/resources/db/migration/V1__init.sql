-- MySQL-specific: disable FK checks; commented out for H2 compatibility
-- SET FOREIGN_KEY_CHECKS = 0;

-- Drop (reverse order)
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

DROP TABLE IF EXISTS employee_invites;
DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS positions;
DROP TABLE IF EXISTS companies;
DROP TABLE IF EXISTS company_email_verifications;

DROP TABLE IF EXISTS users;

-- SET FOREIGN_KEY_CHECKS = 1;

-- =========================================================
-- 1) users
-- =========================================================
CREATE TABLE users
(
    id                CHAR(36)                               NOT NULL,
    email             VARCHAR(255)                           NOT NULL,
    password_hash     VARCHAR(255)                           NOT NULL,
    name              VARCHAR(100)                                    DEFAULT NULL,
    status            VARCHAR(30)                            NOT NULL DEFAULT 'ACTIVE',
    profile_image_url VARCHAR(512)                                    DEFAULT NULL,
    created_at        DATETIME(3)                            NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)                            NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

-- =========================================================
-- 2) companies / employees (optional, but your original SQL had it)
-- =========================================================
CREATE TABLE companies
(
    id         CHAR(36)                   NOT NULL,
    name       VARCHAR(255)                        DEFAULT NULL,
    domain     VARCHAR(255)                        DEFAULT NULL,
    company_size VARCHAR(30) NOT NULL DEFAULT 'SMALL',
    status     VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3)                NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)                NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_companies_domain UNIQUE (domain)
);

CREATE TABLE company_email_verifications
(
    id           CHAR(36)                               NOT NULL,
    email        VARCHAR(255)                           NOT NULL,
    company_name VARCHAR(255)                           NOT NULL,
    domain       VARCHAR(255)                           NOT NULL,
    company_size VARCHAR(30) NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    token        CHAR(36)                               NOT NULL,
    created_at   DATETIME(3)                            NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    verified_at  DATETIME(3)                                     DEFAULT NULL,
    expires_at   DATETIME(3)                                     DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_company_email_verifications_token UNIQUE (token)
);

CREATE TABLE departments
(
    id         CHAR(36)                   NOT NULL,
    company_id CHAR(36)                   NOT NULL,
    name       VARCHAR(150)               NOT NULL,
    status     VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3)                NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)                NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_departments_company_name UNIQUE (company_id, name),
    CONSTRAINT fk_departments_company
        FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
);

CREATE TABLE positions
(
    id         CHAR(36)                   NOT NULL,
    company_id CHAR(36)                   NOT NULL,
    name       VARCHAR(150)               NOT NULL,
    status     VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3)                NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)                NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_positions_company_name UNIQUE (company_id, name),
    CONSTRAINT fk_positions_company
        FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
);

CREATE TABLE employee_invites
(
    id          CHAR(36)                         NOT NULL,
    company_id  CHAR(36)                         NOT NULL,
    email       VARCHAR(255)                     NOT NULL,
    department_id CHAR(36)                                DEFAULT NULL,
    position_id CHAR(36)                                 DEFAULT NULL,
    role        VARCHAR(30)  NOT NULL DEFAULT 'MEMBER',
    employee_no VARCHAR(50)                               DEFAULT NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'INVITED',
    token       CHAR(36)                         NOT NULL,
    created_at  DATETIME(3)                      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at  DATETIME(3)                               DEFAULT NULL,
    accepted_at DATETIME(3)                               DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_employee_invites_token UNIQUE (token),
    CONSTRAINT fk_employee_invites_company
        FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT fk_employee_invites_department
        FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT fk_employee_invites_position
        FOREIGN KEY (position_id) REFERENCES positions (id) ON DELETE SET NULL
);


CREATE TABLE employees
(
    id          CHAR(36)                         NOT NULL,
    user_id     CHAR(36)                         NOT NULL,
    company_id  CHAR(36)                         NOT NULL,
    department_id CHAR(36)                                DEFAULT NULL,
    position_id CHAR(36)                                 DEFAULT NULL,
    role        VARCHAR(30)  NOT NULL DEFAULT 'MEMBER',
    employee_no VARCHAR(50)                               DEFAULT NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(3)                      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_employees_user_company UNIQUE (user_id, company_id),
    CONSTRAINT fk_employees_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_employees_company
        FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE,
    CONSTRAINT fk_employees_department
        FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE SET NULL,
    CONSTRAINT fk_employees_position
        FOREIGN KEY (position_id) REFERENCES positions (id) ON DELETE SET NULL
);

-- =========================================================
-- 3) teams / team_members
-- =========================================================
CREATE TABLE teams
(
    id          CHAR(36)     NOT NULL,
    company_id  CHAR(36)     NOT NULL,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500)          DEFAULT NULL,
    color       VARCHAR(30)           DEFAULT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_teams_company
        FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
);

CREATE TABLE team_members
(
    id         CHAR(36)                         NOT NULL,
    team_id    CHAR(36)                         NOT NULL,
    user_id    CHAR(36)                         NOT NULL,
    role       VARCHAR(30)  NOT NULL DEFAULT 'MEMBER',
    status     VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3)                      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_team_members_team_user UNIQUE (team_id, user_id),
    CONSTRAINT fk_team_members_team
        FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- =========================================================
-- 4) meetings / participants / guests  (REVISED)
-- =========================================================
CREATE TABLE meetings
(
    id           CHAR(36)                                            NOT NULL,
    team_id      CHAR(36)                                            DEFAULT NULL,
-- 나중에 host_user_id not null로 바꾸고 fk도 걸어두기
    host_user_id CHAR(36)                                            DEFAULT NULL,
    title        VARCHAR(200)                                        NOT NULL,
    description  VARCHAR(1000)                                                DEFAULT NULL,
    meet_tag     VARCHAR(100)                                                 DEFAULT NULL,
    scheduled_at DATETIME(3)                                                  DEFAULT NULL,
    started_at   DATETIME(3)                                                  DEFAULT NULL,
    ended_at     DATETIME(3)                                                  DEFAULT NULL,
    is_recording BOOLEAN                                             NOT NULL DEFAULT FALSE,
    status       VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    created_at   DATETIME(3)                                         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)                                         NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
-- team_id랑 host_user_id fk 나중에 걸어두기
--     , CONSTRAINT fk_meetings_team
--         FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
--     CONSTRAINT fk_meetings_host
--         FOREIGN KEY (host_user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE meeting_participants
(
    id          CHAR(36)                    NOT NULL,
    meeting_id  CHAR(36)                    NOT NULL,
-- 나중에 user_id not null로 해두기
    user_id     CHAR(36)                    DEFAULT NULL,
    role        VARCHAR(30) NOT NULL DEFAULT 'PARTICIPANT',
    join_status VARCHAR(30) NOT NULL DEFAULT 'JOINED',
    mic_on      BOOLEAN                     NOT NULL DEFAULT FALSE,
    cam_on      BOOLEAN                     NOT NULL DEFAULT FALSE,
    created_at  DATETIME(3)                 NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_meeting_participants_meeting_user UNIQUE (meeting_id, user_id),

    CONSTRAINT fk_meeting_participants_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE
-- 나중에 user_id랑
-- ,
--     CONSTRAINT fk_meeting_participants_user
--         FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE meeting_guests
(
    id          CHAR(36)                 NOT NULL,
    meeting_id  CHAR(36)                 NOT NULL,
    name        VARCHAR(100)             NOT NULL,
    description VARCHAR(300)                      DEFAULT NULL,
    join_status VARCHAR(30) NOT NULL DEFAULT 'JOINED',
    created_at  DATETIME(3)              NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_meeting_guests_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE
);

-- =========================================================
-- 5) minutes / generation job
-- =========================================================
CREATE TABLE minutes
(
    id           CHAR(36)                                         NOT NULL,
    meeting_id   CHAR(36)                                         NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    summary_text CHAR(36) ,
    decisions    CHAR(36) ,
    keywords     CHAR(36) ,
    created_at   DATETIME(3)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3)                                               DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_minutes_meeting UNIQUE (meeting_id),
    CONSTRAINT fk_minutes_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE
);

CREATE TABLE minutes_generation_jobs
(
    id             CHAR(36)                                      NOT NULL,
    meeting_id     CHAR(36)                                      NOT NULL,
    status         VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT                                                   DEFAULT NULL,
    requested_at   DATETIME(3)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at   DATETIME(3)                                            DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_minutes_jobs_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE
);
