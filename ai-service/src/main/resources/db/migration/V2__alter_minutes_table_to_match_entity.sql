-- Entity 리팩토링에 맞춰 minutes 테이블 스키마 변경
-- PK: meeting_id (VARCHAR) -> id (BIGINT AUTO_INCREMENT)
-- 추가: room_id
-- 제거: meeting_id, access_scope

-- 1. 기존 PK 제거
ALTER TABLE minutes DROP PRIMARY KEY;

-- 2. meeting_id, access_scope 컬럼 제거
ALTER TABLE minutes DROP COLUMN meeting_id;
ALTER TABLE minutes DROP COLUMN access_scope;

-- 3. id (AUTO_INCREMENT PK), room_id 컬럼 추가
ALTER TABLE minutes
    ADD COLUMN id      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
    ADD COLUMN room_id BIGINT NOT NULL AFTER id;
