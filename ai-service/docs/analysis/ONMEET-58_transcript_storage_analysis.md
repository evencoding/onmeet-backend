# Transcript 원본 JSON: S3 vs DB 저장 방식 비교 분석

**작성일**: 2026-03-21
**이슈**: ONMEET-58 회의록 원본 데이터 DB 전환 검토

---

## 현재 구조 파악 (코드 기반)

`TranscriptBuilderService.finalizeMeeting()` 기준:
1. Redis에 쌓인 **VOICE + CHAT 이벤트**를 시간순으로 병합
2. → `TranscriptDocument` 객체로 조립
3. → `storageClient.writeText(s3Key, ...)` 로 **S3에 JSON 업로드**
4. → `Minutes` 엔티티에는 **S3 Key URL 경로(`transcriptS3Key`)만** 저장

---

## DB 전환 시 변경 포인트

| 변경 대상 | 변경 내용 |
|--|--|
| `Minutes` 엔티티 | `transcriptS3Key` 컬럼 대신 `transcript_id` 컬럼으로 교체 |
| `TranscriptBuilderService` | `storageClient.writeText()` 제거, 대신 DB에 직접 저장 |
| `SummaryWorkerService` | S3에서 파일 다운로드 대신 DB에서 직접 조회 |
| `MinutesService.delete()` | S3 Object 삭제 로직 제거 |
| `TranscriptFinalizedEvent` | `transcriptS3Key` 필드 → `transcriptId`로 대체 |
| **DB 마이그레이션 (V4)** | `transcript`, `transcript_event` 테이블 신규 생성, `minutes.transcript_s3_key` 교체 |
| **S3 의존 제거** | `StorageClient` 호출 및 `StorageKeyFactory.transcriptKey()` 관련 제거 |

---

## S3 유지 vs DB 전환 비교

| 항목 | S3 유지 (현재) | DB 전환 (ONMEET-58) |
|--|--|--|
| **코드 복잡도** | S3 업로드/다운로드 경로 별도 관리 필요 | 단순 JPA save/findById로 해결, 코드 간소화 |
| **네트워크 레이턴시** | S3 업로드/다운로드 왕복 통신 필요 (~50~200ms 추가) | DB에서 직접 조회, 레이턴시 없음 |
| **파일 관리 비용** | S3 스토리지 오브젝트 저장 비용 발생 (건당 과금) | MySQL 스토리지 비용만 발생 (공유) |
| **삭제 처리** | S3 Object 별도 삭제 API 호출 필요 | `minutes` 레코드 삭제 1회로 연계 삭제 완료 |
| **분량 제한** | 사실상 무제한 | MySQL `LONGTEXT` 최대 4GB (실용적 문제 없음) |
| **초대용량 회의** | 아주 긴 회의도 안전하게 보관 | DB 부하 주의 필요 (장시간 회의 多 시) |
| **운영 편의성** | S3 콘솔로 파일 직접 열람 | DB 쿼리로 바로 내용 조회 가능 |
| **로컬 개발 환경** | S3 Mock/MinIO 환경 구성 필요 | 별도 구성 없이 로컬 DB만으로 개발 가능 |

---

## 결론 및 권고 (저장 방식)

**현재 OnMeet 스케일(MVP~초기 프로덕션)에서는 DB 전환이 유리합니다.**

- 1~2시간 회의의 Transcript JSON은 **보통 수십~수백 KB** 수준으로, MySQL `LONGTEXT(4GB)` 한도에 여유가 많습니다.
- S3 왕복 통신이 없어지므로 **회의 종료 → 요약 완료까지의 전체 파이프라인 처리 시간이 단축**됩니다.
- S3 의존성 제거로 **로컬 개발 환경 구성이 단순화**됩니다.
- 코드 변경 포인트가 명확하고 범위가 제한적이어서 **마이그레이션 리스크가 낮습니다.**

> ⚠️ **향후 검토 시점**: 동시 회의 수가 수백 개를 넘거나 회의 시간이 상시 3시간 이상인 케이스가 많아진다면, DB 부하가 증가할 수 있어 그 시점에 S3 방식으로 재전환하는 것이 적합합니다.

---

## Transcript 엔티티 DB 컬럼 설계안

### `transcript` 테이블 (상위 헤더)

```sql
CREATE TABLE transcript (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id         BIGINT          NOT NULL,           -- 회의방 ID (INDEX)
    transcript_id   VARCHAR(64)     NOT NULL UNIQUE,    -- UUID (Minutes FK 연결용)
    version         INT             NOT NULL DEFAULT 1, -- 재생성 버전 추적
    status          VARCHAR(32)     NOT NULL,           -- PENDING / COMPLETED / FAILED
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,

    INDEX idx_room_id (room_id)
);
```

### `transcript_event` 테이블 (자식, 1:N 정규화)

```sql
CREATE TABLE transcript_event (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transcript_id    VARCHAR(64)  NOT NULL,          -- transcript 테이블 FK
    event_id         VARCHAR(64)  NOT NULL,          -- 이벤트 고유 UUID (messageId or segmentId)
    type             VARCHAR(8)   NOT NULL,          -- 'VOICE' | 'CHAT'
    actor_id         VARCHAR(128) NOT NULL,          -- 발화자/채팅 발신자 ID
    seq              BIGINT       NOT NULL,          -- 시간 내 순서
    timestamp        DATETIME(6)  NOT NULL,          -- 이벤트 발생 시각
    text             TEXT         NOT NULL,          -- 발화/채팅 텍스트
    segment_start_ms BIGINT       NULL,              -- VOICE 전용
    segment_end_ms   BIGINT       NULL,              -- VOICE 전용

    INDEX idx_transcript_id (transcript_id)
);
```

### `Minutes` 엔티티 변경 사항

| 기존 컬럼 | 변경 후 |
|--|--|
| `transcript_s3_key VARCHAR(512)` | 제거 |
| _(없음)_ | `transcript_id VARCHAR(64)` 추가 (transcript 테이블 논리 참조) |

---

## DB 정규화 적용 타당성

### `events_json LONGTEXT` 단일 컬럼 vs `transcript_event` 자식 테이블

| 항목 | `events_json` 단일 컬럼 | `transcript_event` 자식 테이블 |
|--|--|--|
| **구현 속도** | 빠름 | 상대적으로 느림 (배치 Insert 필요) |
| **쿼리 유연성** | 개별 이벤트 조회 불가 | 화자별·타입별 조회, 통계 집계 가능 |
| **향후 기능 확장** | 개별 이벤트 수정/삭제 불가 | 이벤트 단위 수정·삭제 가능 |
| **데이터 정합성** | JSON 파싱 오류 위험 존재 | DB 타입 제약으로 정합성 보장 |
| **이후 마이그레이션 비용** | 나중에 분리 시 데이터 재삽입 필요 | 처음부터 정규화되어 추가 비용 없음 |

### 권고 (정규화)

**ONMEET-58에서 처음부터 `transcript_event` 자식 테이블로 정규화하여 구현하는 것을 권고합니다.**

- 나중에 `events_json` 컬럼을 테이블로 쪼개려면 `ALTER TABLE` + 데이터 마이그레이션이 훨씬 복잡해집니다.
- `TranscriptDocument.Event` 필드가 이미 명확하게 정의되어 있어 자식 테이블 컬럼으로 바로 매핑이 가능합니다.
- 배치 Insert(`saveAll` + `spring.jpa.properties.hibernate.jdbc.batch_size` 설정) 주의 사항만 잡으면 구현 난이도는 낮은 수준입니다.

### 주요 수정 포인트 (정규화 포함 기준)

| 변경 대상 | 변경 내용 |
|--|--|
| `TranscriptBuilderService` | `storageClient.writeText()` 제거 → `transcriptRepo.save()` + `transcriptEventRepo.saveAll()` 배치 Insert |
| `SummaryWorkerService` | S3 다운로드 제거 → `transcriptEventRepo.findAllByTranscriptIdOrderBySeqAsc()` 후 `TranscriptRenderer`에 전달 |
| `TranscriptRenderer` | `List<TranscriptEvent>` 입력 지원하도록 오버로드 추가 |
| 신규 엔티티 | `Transcript`, `TranscriptEvent` 2개 추가 |
| 신규 Repository | `TranscriptRepository`, `TranscriptEventRepository` 2개 추가 |
| Migration V4 | `transcript`, `transcript_event` 테이블 생성, `minutes.transcript_s3_key` 컬럼 교체 |
