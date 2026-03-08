# 📬 Notification Service 기술 문서

> OnMeet 프로젝트의 알림 마이크로서비스입니다.  
> 실시간(SSE) + 모바일 푸시(FCM) + 비동기 이벤트(Kafka) 기반의 통합 알림 시스템을 제공합니다.

---

## 목차
1. [서비스 개요](#1-서비스-개요)
2. [아키텍처](#2-아키텍처)
3. [핵심 기능](#3-핵심-기능)
4. [데이터 모델 (Entity)](#4-데이터-모델-entity)
5. [API 명세](#5-api-명세)
6. [알림 타입 & 템플릿](#6-알림-타입--템플릿)
7. [안정성 개선 (Reliability)](#7-안정성-개선-reliability)
8. [설정 (Configuration)](#8-설정-configuration)
9. [패키지 구조](#9-패키지-구조)

---

## 1. 서비스 개요

| 항목 | 내용 |
|------|------|
| **서비스명** | notification-service |
| **포트** | 8085 |
| **Context Path** | `/notification` |
| **주요 의존성** | Spring Boot 3.3.5, Spring Data JPA, Spring Kafka, Firebase Admin SDK |
| **DB** | MySQL (`notification_db`) |
| **메시지 브로커** | Apache Kafka |

---

## 2. 아키텍처

### 전체 흐름도

```
┌──────────────┐     Kafka Topic        ┌──────────────────────┐
│ video-service │ ───────────────────▶  │  notification-service │
│  (Producer)   │  notification-send    │   (Consumer)          │
└──────────────┘       -topic           └──────────┬───────────┘
                                                   │
                                        ┌──────────▼───────────┐
                                        │  NotificationService  │
                                        │  .send()              │
                                        └──────────┬───────────┘
                                                   │
                                  ┌────────────────┼────────────────┐
                                  ▼                ▼                ▼
                           ┌──────────┐    ┌──────────┐    ┌──────────┐
                           │   SSE    │    │   FCM    │    │    DB    │
                           │ (실시간)  │    │ (푸시)   │    │ (저장)   │
                           └──────────┘    └──────────┘    └──────────┘
```

### 알림 발송 플로우

1. **다른 마이크로서비스**(video-service 등)가 Kafka 토픽(`notification.send`)에 이벤트 발행
2. **NotificationEventConsumer**가 이벤트를 수신하여 JSON → `NotificationRequestDto`로 역직렬화
3. **NotificationService.send()** 호출:
   - `NotificationTemplate`에서 알림 타입에 맞는 제목/본문 생성
   - `Notification` + `NotificationRecipient` 엔티티를 DB에 저장
   - **SSE**: 해당 유저에게 연결된 모든 SseEmitter로 실시간 전송
   - **FCM**: 해당 유저의 등록된 모든 디바이스로 푸시 알림 전송

---

## 3. 핵심 기능

### 3.1 실시간 알림 (SSE)

- 클라이언트가 `/notification/v1/sse/subscribe`에 GET 요청 → SSE 연결
- **멀티탭/멀티디바이스 지원**: 동일 유저의 여러 SSE 연결을 `ConcurrentHashMap`으로 관리
- 30초 간격 **Heartbeat** 전송으로 연결 유지 (`NotificationScheduler`)
- 연결 끊김 시 자동으로 Emitter 제거

### 3.2 모바일 푸시 알림 (FCM)

- Firebase Cloud Messaging 기반
- 디바이스별 토큰 관리 (등록/해제/Upsert)
- 유효하지 않은 토큰(`UNREGISTERED`, `INVALID_ARGUMENT`) 자동 삭제
- Firebase 미설정 시 graceful skip (개발 환경 대응)

### 3.3 알림 조회/관리

| 기능 | 설명 |
|------|------|
| 목록 조회 | 내 알림을 페이징(최신순)으로 조회 |
| 미읽음 수 | `readAt IS NULL` 기준 카운트 |
| 단건 읽음 | 특정 알림을 읽음 처리 (`readAt` 갱신) |
| 전체 읽음 | 내 모든 미읽음 알림을 일괄 읽음 처리 |
| 단건 삭제 | 특정 알림 삭제 (본인 알림만) |
| 전체 삭제 | 내 모든 알림 삭제 |

### 3.4 알림 설정

- 사용자별 알림 수신 설정 관리 (`NotificationSetting`)
- 알림 타입별 ON/OFF 가능

### 3.5 예약 알림

- `scheduledAt` 필드가 설정된 알림은 즉시 발송하지 않고 DB에 저장
- `ScheduledNotificationProcessor`가 주기적으로 스캔하여 시간이 된 알림을 발송

---

## 4. 데이터 모델 (Entity)

### ERD

```
┌─────────────────────┐       ┌──────────────────────────┐
│     Notification     │       │  NotificationRecipient    │
├─────────────────────┤       ├──────────────────────────┤
│ noti_id (PK)        │──1:N─▶│ id (PK)                  │
│ type (Enum)         │       │ noti_id (FK)             │
│ status (Enum)       │       │ user_id                  │
│ title               │       │ read_at                  │
│ body                │       │ sent_at                  │
│ deeplink            │       │ created_at               │
│ created_at          │       └──────────────────────────┘
│ scheduled_at        │
│ resource_type       │       ┌──────────────────────────┐
│ dedupe_key (UNIQUE) │       │       FcmToken            │
│ resource_id         │       ├──────────────────────────┤
│ actor_user_id       │       │ id (PK)                  │
│ recipients (1:N)    │       │ user_id                  │
└─────────────────────┘       │ token                    │
                              │ device_id                │
┌─────────────────────┐       │ device_type              │
│ NotificationSetting  │       │ created_at               │
├─────────────────────┤       └──────────────────────────┘
│ id (PK)             │
│ user_id (UNIQUE)    │
│ is_push_enabled     │
│ is_meeting_invite.. │
│ is_meeting_start..  │
│ is_meeting_remind.. │
│ is_minutes_compl..  │
│ is_system_notice..  │
│ is_do_not_disturb.. │
│ dnd_start_time      │
│ dnd_end_time        │
└─────────────────────┘

┌─────────────────────┐
│ NotificationStream   │
├─────────────────────┤
│ id (PK)             │
│ user_id             │
│ stream_id           │
│ connected_at        │
└─────────────────────┘
```

### 주요 Enum

| Enum | 값 |
|------|-----|
| `NotificationStatus` | `PENDING`, `SENT`, `FAILED` |
| `NotificationType` | `MEETING_INVITATION`, `INVITATION_ACCEPTED`, `INVITATION_DECLINED`, `INVITATION_CANCELLED`, `PARTICIPANT_KICKED`, `WAITING_ROOM_ADMITTED`, `WAITING_ROOM_REJECTED`, `PARTICIPANT_JOINED_NOTIFY`, `MEETING_STARTED`, `MEETING_CREATED`, `MEETING_TODAY`, `SCHEDULE_CREATED`, `SCHEDULE_CHANGED`, `SCHEDULE_CANCELLED`, `MEETING_REMINDER`, `TEAM_MEMBER_ADDED`, `SYSTEM`, `EVENT` |
| `ResourceType` | 리소스 종류 (회의, 팀 등) |

---

## 5. API 명세

### SSE 구독 (Server-Sent Events)

| Method | Endpoint | 설명 |
|--------|----------|------|
| `GET` | `/notification/v1/sse/subscribe` | SSE 실시간 알림 구독 |

#### 💡 SSE 인증 및 연결 방식
- **일반 REST API**와 달리 브라우저의 `EventSource`는 기본적으로 추가 HTTP Header(Authorization 등)를 지원하지 않기 때문에, API Gateway를 거치는 구조에서는 보통 클라이언트가 **Query Parameter**로 토큰을 전달하거나, API Gateway 자체 설정에 따라 쿠키 세션을 사용하도록 구성합니다.
- 프론트엔드 연결 예시:
  ```javascript
  const token = 'Bearer ...'; 
  const eventSource = new EventSource(`/notification/v1/sse/subscribe?token=${token}`);
  ```
- **수신 이벤트 포맷**:
  클라이언트가 연결하면 아래 포맷으로 데이터가 수신됩니다. (이벤트명: `notification`)
  ```http
  event: notification
  id: 42
  data: {"id":42,"type":"MEETING_INVITATION","title":"회의 초대","body":"홍길동님이 주간회의 회의에 초대했습니다.","deeplink":"/meeting/abc123","createdAt":"2026-03-07T11:00:00","scheduledAt":null,"resourceType":"MEETING","dedupeKey":"invite_abc123_1","resourceId":"abc123","actorUserId":5,"isRead":false}
  ```

### 알림 조회/관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| `GET` | `/notification/v1/notifications` | 알림 목록 조회 (페이징, 최신순) |
| `GET` | `/notification/v1/notifications/unread/count` | 미읽음 알림 수 조회 |
| `PATCH` | `/notification/v1/notifications/{notificationId}/read` | 단건 읽음 처리 |
| `PATCH` | `/notification/v1/notifications/read/all` | 전체 읽음 처리 |
| `DELETE` | `/notification/v1/notifications/{notificationId}` | 단건 삭제 |
| `DELETE` | `/notification/v1/notifications/all` | 전체 삭제 |

### FCM 토큰 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/notification/v1/fcm/token` | FCM 토큰 등록 |
| `DELETE` | `/notification/v1/fcm/token` | FCM 토큰 해제 |

#### 💡 요청 DTO (FCM 토큰 등록 시)
```json
{
  "token": "fMcR3gT...(Firebase에서 발급한 토큰)",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceType": "WEB" // "WEB", "ANDROID", "IOS" 중 택1
}
```
- **deviceId**: 한 사용자가 여러 기기에서 로그인할 수 있으므로, 디바이스를 식별하는 고유값(Web의 경우 브라우저 핑거프린트나 별도 UUID 등)을 함께 보내어 디바이스별 토큰 관리가 가능하도록 해야 합니다.

### 알림 설정 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| `GET` | `/notification/v1/settings/{userId}` | 알림 설정 조회 |
| `POST` | `/notification/v1/settings/{userId}` | 알림 설정 업데이트 |

---

## 5-1. 공통 응답 및 에러 포맷 (참고사항)

### 알림 DTO 공통 스펙 (`NotificationResponseDto`)
알림 목록 조회(`GET /v1/notifications`) 및 SSE `data` 필드에 포함되는 DTO 스펙은 다음과 같습니다.
```json
{
  "id": 42,
  "type": "MEETING_INVITATION",
  "title": "회의 초대",
  "body": "홍길동님이 주간회의 회의에 초대했습니다.",
  "deeplink": "/meeting/abc123",
  "createdAt": "2026-03-07T11:00:00",
  "scheduledAt": null,
  "resourceType": "MEETING",
  "dedupeKey": "invite_abc123_1",
  "resourceId": "abc123",
  "actorUserId": 5,
  "isRead": false
}
```
> 목록 조회 시 Spring Data JPA `Page` 객체 형식으로 래핑되어 내려갑니다 (`content`, `totalPages`, `totalElements` 등 포함).

### 전역 에러 포맷 (`ExceptionResponse`)
성공(200 OK) 이외에 400(잘못된 요청), 401(인증 실패), 500(서버 에러) 발생 시 공통 포맷으로 응답합니다. 프론트엔드에서는 `code`와 `message`를 확인해 적절하게 예외 처리를 수행할 수 있습니다.
```json
{
  "code": "COMMON-ERR-XXX", // 내부 에러 코드 (ex: TOKEN-INVALID)
  "message": "권한이 없습니다.", // 사용자 정의 에러 메시지
  "status": 401             // HTTP 상태 코드
}
```

---

## 6. 알림 타입 & 템플릿

각 `NotificationType`에 대응하는 메시지 템플릿이 `NotificationTemplate` enum에 정의되어 있습니다.

### 템플릿 플레이스홀더

| 변수 | 설명 |
|------|------|
| `{senderName}` | 알림을 유발한 사용자 이름 (`actorUserId`로 조회) |
| `{receiverName}` | 알림을 받는 사용자 이름 (`userId`로 조회) |
| `{title}` | 알림 제목 (보통 방/회의 이름) |
| `{body}` | 원본 알림 본문 |

### 주요 템플릿 예시

| 타입 | 제목 | 본문 템플릿 |
|------|------|-------------|
| `MEETING_INVITATION` | 회의 초대 | `{senderName}님이 {title} 회의에 초대했습니다.` |
| `INVITATION_ACCEPTED` | 초대 수락 | `{senderName}님이 회의 초대를 수락했습니다.` |
| `PARTICIPANT_JOINED_NOTIFY` | 참가자 입장 | `{senderName}님이 {title} 회의에 참가했습니다.` |
| `MEETING_STARTED` | 회의 시작 | `{title} 회의가 시작되었습니다.` |
| `SCHEDULE_CREATED` | 일정 생성 | `{senderName}님이 {title} 예약 회의를 생성했습니다.` |
| `PARTICIPANT_KICKED` | 회의 퇴장 | `{title} 회의에서 퇴장되었습니다.` |

---

## 7. Kafka 비동기 이벤트 수신

### Kafka Consumer

| 항목 | 내용 |
|------|------|
| **파일** | `consumer/NotificationEventConsumer.java` |
| **토픽** | `notification.send` |
| **Consumer Group** | `notification-service` |
| **Container Factory** | `kafkaListenerContainerFactory` |
| **동작** | Kafka 메시지 수신 → JSON 역직렬화 → `notificationService.send()` 호출 |

**장점:**
- 다른 마이크로서비스와의 **동기 REST 호출 제거** → Cascading Failure 방지
- notification-service가 다운되더라도 Kafka에 메시지가 보관되어 **복구 후 자동 처리**

> **참고**: 알림 재처리(`@EnableRetry`) 및 30일 경과 알림 자동 정리(`NotificationCleanupScheduler`)가 백엔드에 기본 구현되어 안정성을 보장합니다.

---

## 8. 설정 (Configuration)

### application.yml 주요 설정

```yaml
server:
  port: 8085
  servlet:
    context-path: /notification

spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: notification-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "com.onmeet.notification.dto,com.onmeet.*"
        spring.json.value.default.type: com.onmeet.notification.dto.NotificationRequestDto

onmeet:
  notification:
    retention-days: 30    # 알림 보관 기간 (일)
```

### 인프라 요구 사항

| 서비스 | 용도 | 기본 주소 |
|--------|------|-----------|
| MySQL | 알림 데이터 저장 | `localhost:3311` |
| Kafka | 비동기 이벤트 수신 | `localhost:9092` |
| Firebase | 모바일 푸시 알림 | (serviceAccountKey.json 필요) |
| Zipkin | 분산 트레이싱 | `http://zipkin:9411` |

---

## 9. 패키지 구조

```
com.onmeet.notification
├── NotificationApplication.java          # 메인 (@EnableScheduling, @EnableJpaAuditing)
├── config/
│   ├── AuthServiceProperties.java        # auth-service 연결 설정
│   ├── DevSecurityConfig.java            # 개발 환경 보안 설정
│   ├── FirebaseConfig.java               # Firebase 초기화
│   ├── RestTemplateConfig.java           # RestTemplate 빈
│   └── SecurityConfig.java               # Spring Security 설정
├── consumer/
│   └── NotificationEventConsumer.java    # Kafka Consumer (topic: notification.send)
├── controller/
│   ├── FcmTokenController.java           # FCM 토큰 등록/해제 API
│   ├── NotificationController.java       # 알림 조회/관리 API
│   ├── NotificationSettingController.java # 알림 설정 API
│   └── SseController.java               # SSE 실시간 구독 API
├── dto/
│   ├── FcmTokenRequestDto.java           # FCM 토큰 요청
│   ├── NotificationRequestDto.java       # 알림 발송 요청
│   ├── NotificationResponseDto.java      # 알림 응답
│   └── NotificationSettingDto.java       # 알림 설정
├── entity/
│   ├── FcmToken.java                     # FCM 디바이스 토큰
│   ├── Notification.java                 # 알림 본체
│   ├── NotificationRecipient.java        # 알림 수신자
│   ├── NotificationSetting.java          # 알림 설정
│   └── NotificationStream.java           # SSE 연결 정보
├── exception/
│   └── GlobalExceptionHandler.java       # 전역 예외 처리
├── infra/
│   └── AuthServiceClient.java            # auth-service 사용자 조회
├── repository/
│   ├── FcmTokenRepository.java
│   ├── NotificationRecipientRepository.java
│   ├── NotificationRepository.java
│   ├── NotificationSettingRepository.java
│   └── NotificationStreamRepository.java
├── scheduler/
│   ├── NotificationScheduler.java         # Heartbeat 스케줄러
│   └── ScheduledNotificationProcessor.java # 예약 알림 처리
├── service/
│   ├── FcmService.java                    # FCM 푸시 서비스
│   ├── NotificationQueryService.java      # 알림 조회/관리 서비스
│   ├── NotificationService.java           # 핵심 알림 발송 서비스
│   └── NotificationSettingService.java    # 알림 설정 서비스
└── type/
    ├── NotificationStatus.java            # PENDING, SENT, FAILED
    ├── NotificationTemplate.java          # 알림 메시지 템플릿
    ├── NotificationType.java              # 18개 알림 타입
    └── ResourceType.java                  # 리소스 종류
```
