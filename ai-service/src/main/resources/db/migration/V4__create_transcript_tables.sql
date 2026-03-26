-- ONMEET-58: Transcript 원본 데이터 DB 전환
-- transcript (헤더) + transcript_event (1:N 이벤트) 테이블 신규 생성
-- minutes.transcript_s3_key → transcript_id 컬럼 교체

-- 1. transcript 헤더 테이블 생성
CREATE TABLE transcript
(
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id       BIGINT      NOT NULL COMMENT '회의방 ID',
    transcript_id VARCHAR(64) NOT NULL UNIQUE COMMENT '대본 UUID (Minutes FK 연결용)',
    version       INT         NOT NULL DEFAULT 1 COMMENT '재생성 버전',
    status        VARCHAR(32) NOT NULL COMMENT 'COMPLETED | FAILED',
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,

    INDEX idx_transcript_room_id (room_id)
);

-- 2. transcript_event 자식 테이블 생성 (1:N)
CREATE TABLE transcript_event
(
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transcript_id    VARCHAR(64)  NOT NULL COMMENT 'transcript.transcript_id 참조',
    event_id         VARCHAR(64)  NOT NULL COMMENT '이벤트 고유 UUID',
    type             VARCHAR(8)   NOT NULL COMMENT 'VOICE | CHAT',
    participant_id   VARCHAR(128) NULL    COMMENT '회원 userId(비회원 null)',
    participant_name VARCHAR(128) NOT NULL COMMENT '표시 이름(회원/비회원 모두)',
    seq              BIGINT       NOT NULL COMMENT '시간 내 순서',
    timestamp        DATETIME(6)  NOT NULL COMMENT '이벤트 발생 시각',
    text             TEXT         NOT NULL COMMENT '발화/채팅 텍스트',
    segment_start_ms BIGINT       NULL    COMMENT 'VOICE 전용 시작 시각(ms)',
    segment_end_ms   BIGINT       NULL    COMMENT 'VOICE 전용 종료 시각(ms)',

    INDEX idx_transcript_event_transcript_id (transcript_id)
);

-- 3. minutes 테이블: transcript_s3_key 제거, transcript_id 위치/NULL 허용으로 변경
ALTER TABLE minutes
    DROP COLUMN transcript_s3_key;

ALTER TABLE minutes
    MODIFY COLUMN transcript_id VARCHAR(64) NULL COMMENT 'transcript.transcript_id 논리 참조' AFTER room_id;
