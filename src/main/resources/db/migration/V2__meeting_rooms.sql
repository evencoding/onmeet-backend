CREATE TABLE meeting_rooms
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    room_code         VARCHAR(20)  NOT NULL,
    livekit_room_name VARCHAR(100) NOT NULL,
    title             VARCHAR(200) NOT NULL,
    description       TEXT                  DEFAULT NULL,
    host_user_id      BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    type              VARCHAR(20)  NOT NULL DEFAULT 'INSTANT',
    max_participants  INT          NOT NULL DEFAULT 10,
    is_locked         TINYINT(1)   NOT NULL DEFAULT 0,
    password          VARCHAR(255)          DEFAULT NULL,
    scheduled_at      DATETIME(3)           DEFAULT NULL,
    started_at        DATETIME(3)           DEFAULT NULL,
    ended_at          DATETIME(3)           DEFAULT NULL,
    duration_seconds  INT                   DEFAULT NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_meeting_rooms_code (room_code),
    UNIQUE KEY uk_meeting_rooms_livekit (livekit_room_name),
    INDEX idx_meeting_rooms_host (host_user_id),
    INDEX idx_meeting_rooms_status (status),
    INDEX idx_meeting_rooms_scheduled (scheduled_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE room_participants
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    room_id          BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    role             VARCHAR(20) NOT NULL DEFAULT 'PARTICIPANT',
    status           VARCHAR(20) NOT NULL DEFAULT 'JOINED',
    joined_at        DATETIME(3) NOT NULL,
    left_at          DATETIME(3)          DEFAULT NULL,
    duration_seconds INT                  DEFAULT NULL,
    device_type      VARCHAR(20)          DEFAULT NULL,
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_room_participants_room_user (room_id, user_id),
    INDEX idx_room_participants_user (user_id),
    CONSTRAINT fk_room_participants_room
        FOREIGN KEY (room_id) REFERENCES meeting_rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE room_settings
(
    id                   BIGINT     NOT NULL AUTO_INCREMENT,
    room_id              BIGINT     NOT NULL,
    video_enabled        TINYINT(1) NOT NULL DEFAULT 1,
    audio_enabled        TINYINT(1) NOT NULL DEFAULT 1,
    screen_share_allowed TINYINT(1) NOT NULL DEFAULT 1,
    chat_enabled         TINYINT(1) NOT NULL DEFAULT 1,
    recording_enabled    TINYINT(1) NOT NULL DEFAULT 1,
    waiting_room         TINYINT(1) NOT NULL DEFAULT 0,
    auto_mute_on_join    TINYINT(1) NOT NULL DEFAULT 1,
    created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_settings_room (room_id),
    CONSTRAINT fk_room_settings_room
        FOREIGN KEY (room_id) REFERENCES meeting_rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE room_recordings
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    room_id          BIGINT       NOT NULL,
    egress_id        VARCHAR(100) NOT NULL,
    type             VARCHAR(20)  NOT NULL DEFAULT 'SEGMENT',
    status           VARCHAR(20)  NOT NULL DEFAULT 'RECORDING',
    s3_path          VARCHAR(500)          DEFAULT NULL,
    file_size_bytes  BIGINT                DEFAULT NULL,
    duration_seconds INT                   DEFAULT NULL,
    segment_index    INT                   DEFAULT NULL,
    started_at       DATETIME(3)           DEFAULT NULL,
    ended_at         DATETIME(3)           DEFAULT NULL,
    error_message    TEXT                  DEFAULT NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_room_recordings_room_type (room_id, type),
    INDEX idx_room_recordings_egress (egress_id),
    CONSTRAINT fk_room_recordings_room
        FOREIGN KEY (room_id) REFERENCES meeting_rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE room_tags
(
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    room_id   BIGINT      NOT NULL,
    tag_name  VARCHAR(50) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_tags_room_tag (room_id, tag_name),
    INDEX idx_room_tags_tag (tag_name),
    CONSTRAINT fk_room_tags_room
        FOREIGN KEY (room_id) REFERENCES meeting_rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE room_invitations
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    room_id         BIGINT      NOT NULL,
    inviter_user_id BIGINT      NOT NULL,
    invitee_user_id BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_invitations_room_invitee (room_id, invitee_user_id),
    INDEX idx_room_invitations_invitee (invitee_user_id),
    INDEX idx_room_invitations_room (room_id),
    CONSTRAINT fk_room_invitations_room
        FOREIGN KEY (room_id) REFERENCES meeting_rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE room_favorites
(
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    user_id   BIGINT      NOT NULL,
    room_id   BIGINT      NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_favorites_user_room (user_id, room_id),
    INDEX idx_room_favorites_user (user_id),
    CONSTRAINT fk_room_favorites_room
        FOREIGN KEY (room_id) REFERENCES meeting_rooms (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
