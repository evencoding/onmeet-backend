ALTER TABLE room_recordings ADD COLUMN participant_identity VARCHAR(100) DEFAULT NULL;
ALTER TABLE room_recordings ADD COLUMN track_sid VARCHAR(100) DEFAULT NULL;
ALTER TABLE room_recordings ADD INDEX idx_room_recordings_participant (room_id, participant_identity);
