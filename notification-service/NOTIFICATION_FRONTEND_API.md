# 📡 OnMeet 알림 API 명세서 (프론트엔드용)

> notification-service의 **SSE 실시간 알림** 및 **알림 관련 REST API** 명세입니다.  
> 프론트엔드에서 `EventSource`를 사용하여 실시간 알림을 구현할 때 참고하세요.

---

## 목차
1. [SSE 실시간 알림 (EventSource)](#1-sse-실시간-알림-eventsource)
2. [알림 조회/관리 API](#2-알림-조회관리-api)
3. [알림 설정 API](#3-알림-설정-api)
4. [FCM 토큰 관리 API](#4-fcm-토큰-관리-api)
5. [알림 타입 목록](#5-알림-타입-목록)
6. [프론트엔드 구현 예시](#6-프론트엔드-구현-예시)

---

## 1. SSE 실시간 알림 (EventSource)

### 1.1 연결

```
GET /notification/v1/sse/subscribe
```

| 항목 | 내용 |
|------|------|
| **Content-Type** | `text/event-stream` |
| **인증** | Gateway가 `X-User-Id` 헤더를 자동 주입 |
| **멀티탭 지원** | ✅ 동일 유저가 여러 탭/디바이스에서 동시 구독 가능 |
| **타임아웃** | 서버에서 30초 간격 Heartbeat 전송하여 연결 유지 |

### 1.2 수신 이벤트

#### `notification` 이벤트 (알림 수신)

SSE 이벤트 이름: **`notification`**

```
event: notification
id: 42
data: {"id":42,"type":"MEETING_INVITATION","title":"회의 초대","body":"홍길동님이 주간회의 회의에 초대했습니다.","deeplink":"/meeting/abc123","createdAt":"2026-03-07T11:00:00","scheduledAt":null,"resourceType":"MEETING","dedupeKey":"invite_abc123_1","resourceId":"abc123","actorUserId":5}
```

#### `connect` 이벤트 (연결 확인)

연결 성공 시 서버가 즉시 전송합니다.

```
event: connect
data: connected
```

#### `heartbeat` 이벤트 (연결 유지)

30초마다 서버가 전송합니다. 프론트에서 별도 처리 불필요.

```
event: heartbeat
data: ping
```

### 1.3 알림 데이터 스키마 (`NotificationResponseDto`)

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

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | `number` | 알림 수신 고유 ID (알림 읽음/삭제 시 이 ID 사용) |
| `type` | `string` | 알림 타입 (아래 [5. 알림 타입 목록](#5-알림-타입-목록) 참고) |
| `title` | `string` | 알림 제목 |
| `body` | `string` | 알림 본문 (한국어 렌더링됨) |
| `deeplink` | `string \| null` | 클릭 시 이동할 경로 |
| `createdAt` | `string (ISO 8601)` | 알림 생성 시간 |
| `scheduledAt` | `string \| null` | 예약 발송 시간 (즉시 발송이면 null) |
| `resourceType` | `string` | 리소스 종류 (`MEETING`, `TEAM` 등) |
| `dedupeKey` | `string \| null` | 중복 방지 키 |
| `resourceId` | `string` | 관련 리소스 ID (회의 ID 등) |
| `actorUserId` | `number \| null` | 알림을 유발한 사용자 ID |
| `isRead` | `boolean` | **알림 읽음 여부** (false: 안 읽음, true: 읽음) |

---

## 2. 알림 조회/관리 API

> ⚠️ 모든 API는 Gateway를 통해 접근하며, `X-User-Id` 헤더가 자동 주입됩니다.

### 2.1 내 알림 목록 조회

```
GET /notification/v1/notifications?page=0&size=20
```

**Response** (Page 응답):
```json
{
  "content": [
    {
      "id": 42,
      "type": "MEETING_INVITATION",
      "title": "회의 초대",
      "body": "홍길동님이 주간회의 회의에 초대했습니다.",
      "deeplink": "/meeting/abc123",
      "createdAt": "2026-03-07T11:00:00",
      "resourceType": "MEETING",
      "resourceId": "abc123",
      "actorUserId": 5,
      "isRead": false
    }
  ],
  "totalElements": 50,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

### 2.2 미읽음 알림 수 조회

```
GET /notification/v1/notifications/unread/count
```

**Response:**
```json
{
  "unreadCount": 5
}
```

### 2.3 단건 읽음 처리

```
PATCH /notification/v1/notifications/{notificationId}/read
```

**Response:** `200 OK`

### 2.4 전체 읽음 처리

```
PATCH /notification/v1/notifications/read/all
```

**Response:**
```json
{
  "updatedCount": 5
}
```

### 2.5 단건 삭제

```
DELETE /notification/v1/notifications/{notificationId}
```

**Response:** `200 OK`

### 2.6 전체 삭제

```
DELETE /notification/v1/notifications/all
```

**Response:** `200 OK`

---

## 3. 알림 설정 API

### 3.1 내 알림 설정 조회

```
GET /notification/v1/settings/{userId}
```

**Response:**
```json
{
  "isMeetingNotification": true,
  "isMinutesCompletedNotification": true,
  "isTeamNotification": true
}
```

### 3.2 알림 설정 업데이트

```
POST /notification/v1/settings/{userId}
```

**Request Body:**
```json
{
  "isMeetingNotification": true,
  "isMinutesCompletedNotification": false,
  "isTeamNotification": true
}
```

**Response:** `200 OK`

| 필드 | 타입 | 설명 |
|------|------|------|
| `isMeetingNotification` | `boolean` | 회의 알림 (초대 및 변경사항) |
| `isMinutesCompletedNotification` | `boolean` | 회의록 완성 알림 |
| `isTeamNotification` | `boolean` | 팀 알림 (멤버 추가, 팀 설정 변경) |

---

## 4. FCM 토큰 관리 API

### 4.1 FCM 토큰 등록

```
POST /notification/v1/fcm/token
```

**Request Body:**
```json
{
  "token": "fMcR3gT...(Firebase에서 발급한 토큰)",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceType": "WEB"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `token` | `string` | Firebase에서 발급받은 FCM 토큰 |
| `deviceId` | `string` | 기기 고유 식별자 (UUID 등) |
| `deviceType` | `string` | 기기 종류: `WEB`, `ANDROID`, `IOS` |

**Response:** `200 OK`

### 4.2 FCM 토큰 해제

```
DELETE /notification/v1/fcm/token?token=fMcR3gT...
```

**Response:** `200 OK`

---

## 5. 알림 타입 목록

프론트엔드에서 `type` 필드를 기반으로 아이콘, 색상, 클릭 동작 등을 분기할 때 사용하세요.

| type | 카테고리 | 설명 | 예시 메시지 |
|------|----------|------|-------------|
| `MEETING_INVITATION` | 초대 | 회의 초대 | `{이름}님이 {회의} 회의에 초대했습니다.` |
| `PARTICIPANT_JOINED_NOTIFY` | 참가 | 참가자 입장 | `{이름}님이 {회의} 회의에 참가했습니다.` |
| `PARTICIPANT_KICKED` | 참가 | 회의 퇴장 | `{회의} 회의에서 퇴장되었습니다.` |
| `WAITING_ROOM_ADMITTED` | 참가 | 입장 허용 | `{회의} 대기실에서 입장이 허용되었습니다.` |
| `WAITING_ROOM_REJECTED` | 참가 | 입장 거절 | `{회의} 대기실에서 입장이 거절되었습니다.` |
| `MEETING_STARTED` | 회의 | 회의 시작 | `{회의} 회의가 시작되었습니다.` |
| `MEETING_CREATED` | 회의 | 회의 생성 | `{이름}님이 {회의} 회의를 생성했습니다.` |
| `MEETING_TODAY` | 회의 | 오늘 회의 | `{회의} 회의가 오늘 예정되어 있습니다.` |
| `MEETING_REMINDER` | 회의 | 리마인더 | `{회의} 회의가 곧 시작됩니다.` |
| `SCHEDULE_CREATED` | 일정 | 일정 생성 | `{이름}님이 {회의} 예약 회의를 생성했습니다.` |
| `SCHEDULE_CHANGED` | 일정 | 일정 변경 | `{회의} 회의 일정이 변경되었습니다.` |
| `SCHEDULE_CANCELLED` | 일정 | 일정 취소 | `{회의} 회의가 취소되었습니다.` |
| `TEAM_MEMBER_ADDED` | 팀 | 멤버 추가 | `{이름}님이 팀에 새 멤버를 추가했습니다.` |
| `SYSTEM` | 시스템 | 시스템 알림 | (커스텀 메시지) |
| `EVENT` | 이벤트 | 이벤트 알림 | (커스텀 메시지) |

---

## 6. 프론트엔드 구현 예시

### 6.1 SSE 연결 (JavaScript / TypeScript)

```javascript
// SSE 연결 설정
const eventSource = new EventSource('/notification/v1/sse/subscribe', {
  // Gateway가 인증 헤더를 자동 주입하므로 별도 설정 불필요
});

// ✅ 연결 성공
eventSource.addEventListener('connect', (event) => {
  console.log('SSE 연결 성공:', event.data);
});

// ✅ 알림 수신
eventSource.addEventListener('notification', (event) => {
  const notification = JSON.parse(event.data);
  console.log('새 알림:', notification);

  // 알림 UI 갱신
  showNotificationToast(notification);
  updateUnreadBadge();
});

// ✅ Heartbeat (별도 처리 불필요, 연결 유지용)
eventSource.addEventListener('heartbeat', () => {
  // 연결 유지 확인 (선택적 로깅)
});

// ❌ 에러 처리 & 재연결
eventSource.onerror = (error) => {
  console.error('SSE 연결 에러:', error);
  eventSource.close();

  // 3초 후 자동 재연결
  setTimeout(() => {
    connectSSE();
  }, 3000);
};
```

### 6.2 알림 타입별 UI 분기 예시

```javascript
function getNotificationIcon(type) {
  const icons = {
    MEETING_INVITATION: '📩',
    PARTICIPANT_JOINED_NOTIFY: '👋',
    MEETING_STARTED: '🎬',
    MEETING_REMINDER: '⏰',
    SCHEDULE_CREATED: '📅',
    SCHEDULE_CHANGED: '🔄',
    SCHEDULE_CANCELLED: '🚫',
    PARTICIPANT_KICKED: '🚪',
    SYSTEM: '⚙️',
  };
  return icons[type] || '🔔';
}

function handleNotificationClick(notification) {
  if (notification.deeplink) {
    // deeplink가 있으면 해당 페이지로 이동
    router.push(notification.deeplink);
  }

  // 읽음 처리 API 호출
  fetch(`/notification/v1/notifications/${notification.id}/read`, {
    method: 'PATCH'
  });
}
```

### 6.3 React 훅 예시

```typescript
import { useEffect, useState, useCallback } from 'react';

interface Notification {
  id: number;
  type: string;
  title: string;
  body: string;
  deeplink: string | null;
  createdAt: string;
  resourceType: string;
  resourceId: string;
  actorUserId: number | null;
  isRead: boolean;
}

export function useNotificationSSE() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const es = new EventSource('/notification/v1/sse/subscribe');

    es.addEventListener('connect', () => setConnected(true));

    es.addEventListener('notification', (event) => {
      const data: Notification = JSON.parse(event.data);
      setNotifications(prev => [data, ...prev]);
    });

    es.onerror = () => {
      setConnected(false);
      es.close();
      // 재연결 로직
      setTimeout(() => {
        // 컴포넌트가 마운트 상태인지 체크 후 재연결
      }, 3000);
    };

    return () => es.close();
  }, []);

  return { notifications, connected };
}
```

---

## ⚠️ 주의사항

1. **SSE는 단방향 통신**입니다. 클라이언트 → 서버로 메시지를 보낼 수 없습니다.
2. **브라우저 탭마다 별도 SSE 연결**이 생성됩니다. 서버에서 멀티 연결을 지원합니다.
3. **네트워크 끊김 시 자동 재연결** 로직을 프론트에서 구현해야 합니다. (`EventSource`는 기본적으로 자동 재연결하지만, 커스텀 로직 추가를 권장합니다.)
4. **`deeplink` 필드**를 활용하여 알림 클릭 시 관련 페이지로 라우팅하세요.
5. **알림 타입(`type`) 기반으로 UI를 분기**하세요 (아이콘, 색상, 동작 등).
