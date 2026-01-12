SET FOREIGN_KEY_CHECKS = 0;

# -- Drop (reverse order)
# DROP TABLE IF EXISTS notification_streams;
# DROP TABLE IF EXISTS notification_recipients;
# DROP TABLE IF EXISTS notifications;
#
# DROP TABLE IF EXISTS tokens;
#
# DROP TABLE IF EXISTS voice_segments;
# DROP TABLE IF EXISTS chat_messages;
#
# DROP TABLE IF EXISTS minutes_generation_jobs;
# DROP TABLE IF EXISTS minutes;
#
# DROP TABLE IF EXISTS meeting_guests;
# DROP TABLE IF EXISTS meeting_participants;
# DROP TABLE IF EXISTS meetings;
#
# DROP TABLE IF EXISTS team_members;
# DROP TABLE IF EXISTS teams;
#
# DROP TABLE IF EXISTS employees;
# DROP TABLE IF EXISTS companies;
#
# DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- =========================================================
-- 1) users
-- =========================================================
CREATE TABLE users (
                       id                CHAR(36)     NOT NULL,
                       email             VARCHAR(255)  NOT NULL,
                       password_hash     VARCHAR(255)  NOT NULL,
                       name              VARCHAR(100)  NULL,
                       status            ENUM('ACTIVE','INACTIVE','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
                       profile_image_url VARCHAR(512)  NULL,
                       created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                       updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                       PRIMARY KEY (id),
                       UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 2) companies / employees (optional, but your original SQL had it)
-- =========================================================
CREATE TABLE companies (
                           id         CHAR(36)     NOT NULL,
                           name       VARCHAR(255) NULL,
                           domain     VARCHAR(255) NULL,
                           status     ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
                           created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                           updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                           PRIMARY KEY (id),
                           UNIQUE KEY uk_companies_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE employees (
                           id          CHAR(36)    NOT NULL,
                           user_id     CHAR(36)    NOT NULL,
                           company_id  CHAR(36)    NOT NULL,
                           role        ENUM('OWNER','ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER',
                           employee_no VARCHAR(50) NULL,
                           status      ENUM('ACTIVE','INVITED','LEFT') NOT NULL DEFAULT 'ACTIVE',
                           created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                           PRIMARY KEY (id),
                           UNIQUE KEY uk_employees_user_company (user_id, company_id),
                           KEY idx_employees_company (company_id),
                           CONSTRAINT fk_employees_user
                               FOREIGN KEY (user_id) REFERENCES users(id),
                           CONSTRAINT fk_employees_company
                               FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 3) teams / team_members
-- =========================================================
CREATE TABLE teams (
                       id          CHAR(36)     NOT NULL,
                       company_id  CHAR(36)     NULL,
                       name        VARCHAR(150) NOT NULL,
                       description VARCHAR(500) NULL,
                       color       VARCHAR(30)  NULL,
                       created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                       updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                       PRIMARY KEY (id),
                       KEY idx_teams_company (company_id),
                       CONSTRAINT fk_teams_company
                           FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE team_members (
                              id         CHAR(36)    NOT NULL,
                              team_id    CHAR(36)    NOT NULL,
                              user_id    CHAR(36)    NOT NULL,
                              role       ENUM('HOST','MEMBER') NOT NULL DEFAULT 'MEMBER',
                              status     ENUM('ACTIVE','INVITED','LEFT') NOT NULL DEFAULT 'ACTIVE',
                              created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                              PRIMARY KEY (id),
                              UNIQUE KEY uk_team_members_team_user (team_id, user_id),
                              KEY idx_team_members_user (user_id),
                              CONSTRAINT fk_team_members_team
                                  FOREIGN KEY (team_id) REFERENCES teams(id),
                              CONSTRAINT fk_team_members_user
                                  FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 4) meetings / participants / guests
-- =========================================================
CREATE TABLE meetings (
                          id            CHAR(36)     NOT NULL,
                          team_id        CHAR(36)     NOT NULL,
                          host_user_id   CHAR(36)     NOT NULL,
                          title          VARCHAR(200) NOT NULL,
                          description    VARCHAR(1000) NULL,
                          scheduled_at   DATETIME(3)  NULL,
                          started_at     DATETIME(3)  NULL,
                          ended_at       DATETIME(3)  NULL,
                          is_recording   TINYINT(1)   NOT NULL DEFAULT 0,
                          status         ENUM('SCHEDULED','IN_PROGRESS','ENDED','CANCELED') NOT NULL DEFAULT 'SCHEDULED',
                          created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                          updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                          PRIMARY KEY (id),
                          KEY idx_meetings_team (team_id),
                          KEY idx_meetings_host (host_user_id),
                          KEY idx_meetings_scheduled_at (scheduled_at),
                          CONSTRAINT fk_meetings_team
                              FOREIGN KEY (team_id) REFERENCES teams(id),
                          CONSTRAINT fk_meetings_host
                              FOREIGN KEY (host_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE meeting_participants (
                                      id          CHAR(36)   NOT NULL,
                                      meeting_id  CHAR(36)   NOT NULL,
                                      user_id     CHAR(36)   NOT NULL,
                                      role        ENUM('HOST','PARTICIPANT') NOT NULL DEFAULT 'PARTICIPANT',
                                      join_status ENUM('JOINED','ABSENT') NOT NULL DEFAULT 'JOINED',
                                      mic_on      TINYINT(1) NOT NULL DEFAULT 1,
                                      cam_on      TINYINT(1) NOT NULL DEFAULT 1,
                                      created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                      PRIMARY KEY (id),
                                      UNIQUE KEY uk_meeting_participants_meeting_user (meeting_id, user_id),
                                      KEY idx_meeting_participants_user (user_id),
                                      CONSTRAINT fk_meeting_participants_meeting
                                          FOREIGN KEY (meeting_id) REFERENCES meetings(id),
                                      CONSTRAINT fk_meeting_participants_user
                                          FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE meeting_guests (
                                id          CHAR(36)     NOT NULL,
                                meeting_id  CHAR(36)     NOT NULL,
                                name        VARCHAR(100) NOT NULL,
                                description VARCHAR(300) NULL,
                                join_status ENUM('JOINED','ABSENT') NOT NULL DEFAULT 'JOINED',
                                created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                PRIMARY KEY (id),
                                KEY idx_meeting_guests_meeting (meeting_id),
                                CONSTRAINT fk_meeting_guests_meeting
                                    FOREIGN KEY (meeting_id) REFERENCES meetings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 5) minutes / generation job
-- =========================================================
CREATE TABLE minutes (
                         id           CHAR(36)    NOT NULL,
                         meeting_id   CHAR(36)    NOT NULL,
                         status       ENUM('DRAFT','COMPLETED','PUBLISHED','PRIVATE') NOT NULL DEFAULT 'DRAFT',
                         summary_text LONGTEXT    NULL,
                         decisions    LONGTEXT    NULL,
                         keywords     JSON        NULL,
                         created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                         updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                         published_at DATETIME(3) NULL,
                         PRIMARY KEY (id),
                         UNIQUE KEY uk_minutes_meeting (meeting_id),
                         CONSTRAINT fk_minutes_meeting
                             FOREIGN KEY (meeting_id) REFERENCES meetings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE minutes_generation_jobs (
                                         id            CHAR(36)    NOT NULL,
                                         meeting_id    CHAR(36)    NOT NULL,
                                         status        ENUM('PENDING','PROCESSING','DONE','FAILED') NOT NULL DEFAULT 'PENDING',
                                         failure_reason TEXT       NULL,
                                         requested_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                         completed_at  DATETIME(3) NULL,
                                         PRIMARY KEY (id),
                                         KEY idx_minutes_jobs_meeting (meeting_id),
                                         KEY idx_minutes_jobs_status (status),
                                         CONSTRAINT fk_minutes_jobs_meeting
                                             FOREIGN KEY (meeting_id) REFERENCES meetings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 6) chat / voice (optional but from your original SQL)
-- =========================================================
CREATE TABLE chat_messages (
                               id            BIGINT       NOT NULL AUTO_INCREMENT,
                               meeting_id    CHAR(36)     NOT NULL,
                               sender_user_id CHAR(36)    NOT NULL,
                               content       TEXT         NULL,
                               message_type  ENUM('ENTER','LEAVE','NOTICE','TEXT') NOT NULL DEFAULT 'TEXT',
                               created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                               PRIMARY KEY (id),
                               KEY idx_chat_messages_meeting (meeting_id),
                               KEY idx_chat_messages_sender (sender_user_id),
                               CONSTRAINT fk_chat_messages_meeting
                                   FOREIGN KEY (meeting_id) REFERENCES meetings(id),
                               CONSTRAINT fk_chat_messages_sender
                                   FOREIGN KEY (sender_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE voice_segments (
                                id          BIGINT       NOT NULL AUTO_INCREMENT,
                                meeting_id  CHAR(36)     NOT NULL,
                                speaker     VARCHAR(128) NOT NULL,
                                content     TEXT         NULL,
                                started_at  DATETIME(3)  NULL,
                                ended_at    DATETIME(3)  NULL,
                                created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                PRIMARY KEY (id),
                                KEY idx_voice_segments_meeting (meeting_id),
                                CONSTRAINT fk_voice_segments_meeting
                                    FOREIGN KEY (meeting_id) REFERENCES meetings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 7) tokens (refresh token)
-- =========================================================
CREATE TABLE tokens (
                        id            CHAR(36)     NOT NULL,
                        user_id       CHAR(36)     NOT NULL,
                        refresh_token VARCHAR(512) NULL,
                        expires_at    DATETIME(3)  NULL,
                        revoked       TINYINT(1)   NOT NULL DEFAULT 0,
                        created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        PRIMARY KEY (id),
                        KEY idx_tokens_user (user_id),
                        KEY idx_tokens_expires (expires_at),
                        CONSTRAINT fk_tokens_user
                            FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================================================
-- 8) notifications / recipients / (SSE) streams
-- =========================================================
CREATE TABLE notifications (
                               id            CHAR(36)     NOT NULL,
                               type          ENUM(
                                   'MEETING_CREATED',
                                   'MEETING_TODAY',
                                   'MEETING_STARTED',
                                   'TEAM_MEMBER_ADDED',
                                   'MINUTES_COMPLETED',
                                   'MINUTES_PUBLISHED',
                                   'MINUTES_VISIBILITY_DECIDED'
                                   ) NOT NULL,
                               resource_type ENUM('MEETING','MINUTES','TEAM') NOT NULL,
                               resource_id   CHAR(36)     NOT NULL,
                               title         VARCHAR(200) NOT NULL,
                               body          TEXT         NULL,
                               link_url      VARCHAR(1024) NULL,
                               dedup_key     VARCHAR(191) NOT NULL,
                               scheduled_at  DATETIME(3)  NULL,
                               sender_user_id CHAR(36)    NULL,
                               created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                               PRIMARY KEY (id),
                               UNIQUE KEY uk_notifications_dedup_key (dedup_key),
                               KEY idx_notifications_resource (resource_type, resource_id),
                               KEY idx_notifications_created_at (created_at),
                               KEY idx_notifications_sender (sender_user_id),
                               CONSTRAINT fk_notifications_sender
                                   FOREIGN KEY (sender_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_recipients (
                                         notification_id CHAR(36)   NOT NULL,
                                         user_id         CHAR(36)   NOT NULL,
                                         delivered_at    DATETIME(3) NULL,
                                         read_at         DATETIME(3) NULL,
                                         created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                         PRIMARY KEY (notification_id, user_id),
                                         KEY idx_notification_recipients_user (user_id),
                                         KEY idx_notification_recipients_read_at (read_at),
                                         CONSTRAINT fk_notification_recipients_notification
                                             FOREIGN KEY (notification_id) REFERENCES notifications(id),
                                         CONSTRAINT fk_notification_recipients_user
                                             FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

/* SSE 연결 상태/마지막 이벤트 아이디 저장 (원본: "알림 전송여부") */
CREATE TABLE notification_streams (
                                      user_id        CHAR(36)    NOT NULL,
                                      last_event_id  VARCHAR(128) NULL,
                                      connected_at   DATETIME(3)  NULL,
                                      disconnected_at DATETIME(3) NULL,
                                      updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                                      PRIMARY KEY (user_id),
                                      CONSTRAINT fk_notification_streams_user
                                          FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
