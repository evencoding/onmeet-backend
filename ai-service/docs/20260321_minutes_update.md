# AI 서비스 회의록(Minutes) 고도화 및 검색 연동 업데이트

**작업일시**: 2026-03-21
**도메인**: `ai-service`, `onmeet-common`
**주제**: 회의록 데이터 세분화 파싱, 키워드 검색 기능 연동, 회의록 수정/삭제 파이프라인 정립

---

## 🚀 주요 업데이트 내역

### 1. 요약 데이터 구조 파싱 및 DB 세분화 저장
기존에는 Claude(또는 LLM)가 생성한 요약 결과를 단일 JSON 문자열(`summary_json`)로만 통째로 저장하여 내부 내용을 검색할 수 없었습니다. 이번 업데이트로 다음 데이터가 각 컬럼으로 분리 개편되었습니다.

- **데이터 계층 수정**: `ai-service` 내부의 `SummaryResult` 클래스를 `onmeet-common` 모듈(`com.onmeet.common.dto.ai`)로 이관. (이후 알림, 통계 모듈 등 타 기능에서 재사용)
- **추가된 컬럼 (LONGTEXT)**: 
  - `description`: 전반적인 요약 설명
  - `keywords`: 중요 키워드 배열 구조
  - `decisions`: 결정 사항 배열 구조
  - `action_items`: 진행할 작업 배열 구조
- AI 요약 워커(`SummaryWorkerService`) 및 재생성 로직(`MinutesService.regenerate`)은 위 모델을 준수하여 로우 JSON을 파싱 후 각각의 컬럼에 즉시 적재(INSERT/UPDATE)하도록 리팩토링되었습니다.

### 2. 회의록 본문 및 키워드 통합 검색 API 신설
개별 개체로 분산 저장된 요약 데이터(`description`, `keywords`, `decisions`, `action_items`)에 기반하여, 입력된 검색어가 각 컬럼의 내용 중 하나라도 포함되어 있는지를 파악하여 결과를 조회하는 API가 개설되었습니다.

- **Endpoint**: `GET /v1/rooms/minutes/search?q={keyword}`
- **Repository 전략**: Spring Data JPA의 `@Query` (JPQL) `LIKE %:keyword%` 연산자를 활용하여 4구획 전체를 검색 후 적합한 회의록 묶음을 반환

### 3. 회의록 부분 수정 시 검색 데이터 자동 동기화
사용자가 회의록 내용을 수정 및 편집하는 경우, 해당 변경 분이 검색용 컬럼들에도 즉시 반영되도록 고도화하였습니다.

- **Endpoint**: `PUT /v1/rooms/{roomId}/minutes`
- **동작 원리**: 프론트엔드가 수정된 전체 JSON 객체를 보내면, 시스템은 `userEditedSummaryJson` 컬럼을 갱신하는 것과 동시에 받은 JSON을 한 번 더 파싱(Deserialize)하여 `description`, `keywords`, `decisions`, `actionItems` 컬럼에 즉시 덮어씁니다.
- **기대 효과**: 수정 직후부터 즉시 수정된 내용으로 **키워드 검색** 및 **설명 검색**이 완벽하게 지원됩니다.

### 4. 회의실 삭제 연계를 위한 완전 삭제 기능 지원
미팅방(Room) 삭제 시 연계되거나, 재시작 등 기타 사유로 인해 회의록 및 기반 파일을 물리적으로 완전 삭제 처리해야 할 때 쓰일 API 엔드포인트 세트가 추가되었습니다.

- **Endpoint**: `DELETE /v1/rooms/{roomId}/minutes`
- **동작 원리**: 
  1. 원본 대화록(`transcriptS3Key`)을 조회하여 S3 저장소 객체 영구 제거
  2. 원본 요약본(`summaryS3Key`) 객체 S3 저장소 강제 해제
  3. `minutes` 데이터베이스 레코드 강제 삭제(`minutesRepository.delete`)
- **안정성**: S3상에서 삭제 중 오류/예외가 터지더라도 `catch` 되어 데이터베이스 삭제는 반드시 수행되도록 설계되어 좀비 데이터를 차지합니다.

### 5. 마이그레이션 스키마 재정립 및 분리 (V1~V3)
과거 잘못 추가되었던 데이터베이스 설정들을 걷어내고, 이번 신규 기능 추가를 안전하게 프로덕션에 배포하기 위해 마이그레이션 버전을 명확히 분리 재정립했습니다.

- **V1 (`create_minutes_table`)**: 초기 테이블 생성 스키마를 훼손하지 않고 초창기 원본 상태로 롤백 유지
- **V2 (`alter_minutes_table...`)**: 미사용 `access_scope`, `meeting_id` 필드 제거 및 `room_id` 추가 등 뼈대 구조 개편 전용
- **V3 (`add_parsed_summary_columns...`)**: 이번 업데이트의 핵심인 AI 요약물 파싱 컬럼(`description`, `keywords`, `decisions`, `action_items`) 추가 구문 전용 스크립트로 격리

이로써 운영 환경(Real DB)의 기존 데이터 충돌이나 손실 위험 없이, 신규 컬럼 변경점만 단독으로 안전하게 CI/CD 자동 배포가 가능하도록 구조화되었습니다.

---
**관련 담당자 확인용**: 본 기능은 `API_REFERENCE.md` 문서에도 갱신 등록되었습니다.
