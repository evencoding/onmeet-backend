-- notification_setting 테이블에 신규 알림 설정 컬럼 추가
-- 기존 컬럼(is_meeting_notification, is_minutes_completed_notification, is_team_notification)은 유지하며
-- 각각 meetingInviteNotification, minutesCompletedNotification, systemNoticeNotification으로 재사용

ALTER TABLE notification_setting
    ADD COLUMN IF NOT EXISTS push_enabled                TINYINT(1) NOT NULL DEFAULT 1 COMMENT '전체 푸시 알림 ON/OFF',
    ADD COLUMN IF NOT EXISTS meeting_start_notification  TINYINT(1) NOT NULL DEFAULT 1 COMMENT '회의 시작/참가 알림',
    ADD COLUMN IF NOT EXISTS meeting_remind_notification TINYINT(1) NOT NULL DEFAULT 1 COMMENT '회의 리마인더 알림',
    ADD COLUMN IF NOT EXISTS do_not_disturb_enabled      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '방해금지 모드 ON/OFF',
    ADD COLUMN IF NOT EXISTS do_not_disturb_start_time   TIME                 DEFAULT NULL COMMENT '방해금지 시작 시간',
    ADD COLUMN IF NOT EXISTS do_not_disturb_end_time     TIME                 DEFAULT NULL COMMENT '방해금지 종료 시간';
