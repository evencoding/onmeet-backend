ALTER TABLE room_participants
    ADD COLUMN screen_sharing TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN screen_share_started_at DATETIME(3) DEFAULT NULL;
