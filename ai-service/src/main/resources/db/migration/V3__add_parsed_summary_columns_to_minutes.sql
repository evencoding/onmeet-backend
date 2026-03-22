-- 파싱된 개별 AI 요약 데이터를 저장하기 위한 컬럼 추가 (ONMEET-57)
ALTER TABLE minutes
    ADD COLUMN description  LONGTEXT NULL COMMENT '전반적인 회의 요약 텍스트',
    ADD COLUMN keywords     LONGTEXT NULL COMMENT '중요 키워드 배열 (JSON 문자열)',
    ADD COLUMN decisions    LONGTEXT NULL COMMENT '결정 사항 배열 (JSON 문자열)',
    ADD COLUMN action_items LONGTEXT NULL COMMENT '진행할 작업 배열 (JSON 문자열)';
