CREATE TABLE IF NOT EXISTS minutes (
    meeting_id              VARCHAR(64)     NOT NULL COMMENT '회의 ID (PK)',
    transcript_id           VARCHAR(64)     NOT NULL COMMENT '트랜스크립트 ID',
    transcript_s3_key       VARCHAR(512)    NOT NULL COMMENT 'S3 트랜스크립트 키',
    summary_s3_key          VARCHAR(512)    NULL     COMMENT 'S3 요약 키',
    summary_json            LONGTEXT        NOT NULL COMMENT 'AI 생성 요약 JSON',
    user_edited_summary_json LONGTEXT       NULL     COMMENT '사용자 편집 요약 JSON',
    status                  VARCHAR(32)     NOT NULL COMMENT '상태 (GENERATED, EDITED_BY_USER, REGENERATING, FAILED)',
    access_scope            VARCHAR(16)     NOT NULL COMMENT '접근 범위 (PRIVATE, TEAM, PUBLIC)',
    last_error              VARCHAR(1024)   NULL     COMMENT '마지막 오류 메시지',
    created_at              DATETIME(6)     NOT NULL COMMENT '생성 시각',
    updated_at              DATETIME(6)     NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (meeting_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 회의록(Minutes) 테이블';
