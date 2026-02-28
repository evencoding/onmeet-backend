-- 팀별 역할 관리를 위한 team_members 테이블 생성
CREATE TABLE IF NOT EXISTS `team_members` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `team_id` BIGINT NOT NULL,
    `role` VARCHAR(50) NOT NULL,
    `joined_at` DATETIME(6) NOT NULL,
    UNIQUE KEY `uk_team_members_user_team` (`user_id`, `team_id`),
    CONSTRAINT `fk_team_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_team_members_team` FOREIGN KEY (`team_id`) REFERENCES `teams` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 기존 user_teams 데이터를 team_members로 마이그레이션
-- 기존 팀 리더는 LEADER 역할, 나머지는 MEMBER 역할로 설정
INSERT INTO `team_members` (`user_id`, `team_id`, `role`, `joined_at`)
SELECT
    ut.user_id,
    ut.team_id,
    CASE
        WHEN t.leader_id = ut.user_id THEN 'LEADER'
        ELSE 'MEMBER'
    END as role,
    NOW() as joined_at
FROM `user_teams` ut
INNER JOIN `teams` t ON ut.team_id = t.id;

-- 기존 user_teams 테이블 삭제
DROP TABLE IF EXISTS `user_teams`;
