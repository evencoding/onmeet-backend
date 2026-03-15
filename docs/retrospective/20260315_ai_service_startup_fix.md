# 회고: ai-service 기동 실패 원인 분석 및 수정

**날짜**: 2026-03-15
**브랜치**: release/v0.2.1
**관련 커밋**: e7eb44c, c052567, a747808, 94dd566

---

## 1. 발생한 문제

ai-service가 Docker 환경에서 기동 시 연쇄적으로 3가지 에러가 발생하며 시작에 실패했다.

### 에러 1: Schema-validation 실패
```
Schema-validation: missing column [id] in table [minutes]
```

**원인**: `yesung1215`가 커밋 `b701ecb`에서 Minutes Entity를 리팩토링하면서 Flyway 마이그레이션을 추가하지 않음.

| 항목 | Entity (변경 후) | DB (Flyway V1) |
|------|-----------------|----------------|
| PK | `id` (Long, AUTO_INCREMENT) | `meeting_id` (VARCHAR(64)) |
| 회의 참조 | `roomId` (Long) | 없음 |
| 접근 범위 | 삭제됨 | `access_scope` (VARCHAR(16)) |

**조치**: V2 Flyway 마이그레이션 추가, `application.yml`에 `jpa.hibernate.ddl-auto: validate` 명시

### 에러 2: SummarizerClient 빈 없음
```
No qualifying bean of type 'com.onmeet.ai.pipeline.nlp.SummarizerClient'
```

**원인**: docker 프로필에서 `SummarizerClient` 구현체가 하나도 활성화되지 않음.
- `ClaudeSummarizerClient`: `@ConditionalOnProperty(name = "anthropic.api-key")` → API 키 미설정으로 비활성
- `DummySummarizerClient`: `@Profile({"local","test"})` → docker 프로필에서 비활성

**조치**: `application.yml`에 `anthropic` 프로퍼티 추가, `docker-compose.yml`에 `ANTHROPIC_API_KEY` 환경변수 전달 추가

### 에러 3: anthropic.version 프로퍼티 미해결
```
Could not resolve placeholder 'anthropic.version' in value "${anthropic.version}"
```

**원인**: `application.yml`에 anthropic 설정을 추가했지만, Docker 이미지가 이전 빌드(설정 미포함)를 사용 중이었음. CI/CD에서 변경 감지 실패로 이미지 재빌드가 안 됨.

**조치**: CI/CD 변경 감지 로직 개선 (아래 별도 항목)

---

## 2. 부수적으로 발견된 문제

### 다른 서비스 환경변수 누락
전체 서비스 점검에서 추가 누락 발견:

| 서비스 | 누락 항목 | 심각도 |
|--------|----------|--------|
| email-service | `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REFRESH_TOKEN` | 기동 실패 |
| video-service | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka 연결 실패 |
| notification-service | `FIREBASE_CREDENTIALS_JSON` (.env 템플릿 누락) | 운영자 실수 유발 |

**조치**: 각 서비스 docker-compose.yml 및 .env.example, .env.production.template에 누락 항목 추가

### ai-service 전체 로직 점검 결과
Entity/Repository/Service/Controller의 `roomId` 전환은 완료되었으나, video-service와의 Kafka 연동에서 추가 불일치 발견:

- Kafka 토픽명 불일치 (하이픈 vs 점 표기)
- ChatMessageEvent.seq 필드 누락
- MinutesService.regenerate()의 readText(String) 미구현
- SttWorkerService NPE 위험

→ 이 항목들은 ai-service 담당자(cutekang)와 video-service 담당자가 별도 협의 필요

---

## 3. CI/CD 변경 감지 실패

### 문제
`deploy.yml`에서 `git diff HEAD~1 HEAD`로 변경 감지 → 2개 커밋을 한 번에 push하면 마지막 커밋만 비교되어 첫 번째 커밋의 ai-service 변경을 누락

### 해결
`dorny/paths-filter@v3` 도입으로 교체:
- 기존: 100줄 shell script + `HEAD~1` 비교 (멀티 커밋 취약)
- 변경: YAML 선언적 필터 + `github.event.before` 기반 전체 범위 비교
- 추가: `workflow_dispatch`(수동 트리거) 시 전체 서비스 빌드/배포 지원

### 검토한 대안

| 솔루션 | 채택 여부 | 이유 |
|--------|----------|------|
| dorny/paths-filter | 채택 | 검증된 액션, 낮은 복잡도, YAML 선언 |
| tj-actions/changed-files | 미채택 | 과거 보안 취약점 이력 |
| git merge-base | 미채택 | release 브랜치에서 불필요한 재빌드 가능성 |
| 항상 전체 빌드 | 미채택 | CI 분(minutes) 소모 과다 |
| Turborepo/Nx/Bazel | 미채택 | Java/Kotlin 생태계에 부적합 또는 과도한 도입 비용 |

---

## 4. 변경 의도 분석

### meetingId → roomId 변경
- **작업자**: yesung1215 (커밋 b701ecb)
- **의도**: video-service의 `MeetingRoom.id` (Long)와 ID 체계 통일, 1:N 회의록 지원
- **상태**: 코드 전환 완료, Flyway 마이그레이션만 누락

### accessScope 삭제
- **의도**: 접근 제어를 video-service의 방 참가자 기반으로 처리하는 것이 MSA 책임 분리에 적합
- **상태**: 사용처 없음 확인, 완전 삭제 완료

---

## 5. 교훈 및 개선 방향

### 프로세스
1. **Entity 변경 시 Flyway 마이그레이션 동시 작성 필수** — 다른 서비스 코드를 수정할 때 해당 서비스 담당자에게 리뷰 요청
2. **환경변수 추가 시 체크리스트**: application.yml → docker-compose.yml → .env.example → .env.production.template 4곳 동시 반영
3. **CI/CD 파이프라인은 멀티 커밋 push 시나리오 테스트 필수**

### 기술
1. `@ConditionalOnProperty` + `@Profile` 조합 시 모든 프로필에서 빈이 하나도 없는 상황 방지 (fallback 구현체 필요)
2. Hibernate `ddl-auto` 기본값을 명시적으로 `validate`로 선언하여 스키마 불일치 조기 감지
3. Docker 이미지 재빌드 없이 설정만 변경하는 경우에도 CI/CD 트리거 확인

---

## 6. 커밋 이력

| 커밋 | 내용 |
|------|------|
| e7eb44c | V2 Flyway 마이그레이션 추가, ddl-auto validate 명시 |
| c052567 | anthropic API 설정 추가, 서비스별 환경변수 누락 보완 |
| a747808 | .env.example, .env.production.template 환경변수 추가 |
| 94dd566 | dorny/paths-filter로 CI/CD 변경 감지 개선 |
