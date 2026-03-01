ALTER TABLE meeting_rooms ADD COLUMN access_scope VARCHAR(20) NOT NULL DEFAULT 'ALL';
ALTER TABLE meeting_rooms ADD COLUMN team_id BIGINT DEFAULT NULL;
ALTER TABLE meeting_rooms ADD INDEX idx_meeting_rooms_access_scope (access_scope);
ALTER TABLE meeting_rooms ADD INDEX idx_meeting_rooms_team (team_id);
