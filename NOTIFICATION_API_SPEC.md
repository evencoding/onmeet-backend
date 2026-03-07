# OnMeet 알림 서비스 API 명세서

> **Base URL:** `/notification`
> **인증:** Gateway에서 JWT 파싱 후 `X-User-Id` 헤더 자동 주입

---

## 1. 알림 조회/관리 API

### 1-1. 내 알림 목록 조회

```
GET /notification/v1/notifications?page=0&size=20
```

**Headers:** `X-User-Id: {userId}` (Gateway 자동 주입)

**Response (200):**
```json
{
  "content": [
    {
      "id": 1,
      "type": "MEETING_INVITATION",
      "title": "정호준님이 제품 로드맵 리뷰 회의에 초대했습니다.",
      "body": null,
      "deeplink": "/meeting/123",
      "createdAt": "2026-03-07T08:00:00",
      "resourceType": "MEETING",
      "resourceId": "123",
      "actorUserId": 7
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

---

### 1-2. 미읽음 알림 수 조회

```
GET /notification/v1/notifications/unread-count
```

**Response (200):**
```json
{
  "unreadCount": 5
}
```

---

### 1-3. 단건 읽음 처리

```
PATCH /notification/v1/notifications/{id}/read
```

**Response:** `200 OK`

---

### 1-4. 전체 읽음 처리

```
PATCH /notification/v1/notifications/read-all
```

**Response (200):**
```json
{
  "updatedCount": 5
}
```

---

### 1-5. 단건 알림 삭제

```
DELETE /notification/v1/notifications/{id}
```

**Response:** `204 No Content`

---

### 1-6. 전체 알림 삭제

```
DELETE /notification/v1/notifications
```

**Response:** `204 No Content`

---

## 2. SSE 실시간 알림 (EventSource)

### 2-1. SSE 구독

```
GET /notification/v1/sse/subscribe
Content-Type: text/event-stream
```

**Headers:** `X-User-Id: {userId}` (Gateway 자동 주입)

**SSE 이벤트 형식:**
```
event: notification
data: {"id":1,"type":"MEETING_INVITATION","title":"정호준님이 제품 로드맵 리뷰 회의에 초대했습니다.","body":null,"deeplink":"/meeting/123","createdAt":"2026-03-07T08:00:00","resourceType":"MEETING","resourceId":"123","actorUserId":7}

event: heartbeat
data: ping
```

### 프론트엔드 연동 코드 (React)

```javascript
// SSE 연결 (EventSource)
const connectSSE = (token) => {
  const eventSource = new EventSource(
    '/notification/v1/sse/subscribe',
    {
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );

  // 알림 수신
  eventSource.addEventListener('notification', (event) => {
    const notification = JSON.parse(event.data);
    console.log('새 알림:', notification);

    // 알림 목록에 추가
    addNotification(notification);

    // 미읽음 카운트 갱신
    fetchUnreadCount();
  });

  // 하트비트 (연결 유지)
  eventSource.addEventListener('heartbeat', () => {
    console.log('SSE heartbeat received');
  });

  // 에러 처리 (자동 재연결)
  eventSource.onerror = (err) => {
    console.error('SSE error:', err);
    eventSource.close();
    setTimeout(() => connectSSE(token), 3000); // 3초 후 재연결
  };

  return eventSource;
};

// 미읽음 카운트 조회
const fetchUnreadCount = async () => {
  const res = await fetch('/notification/v1/notifications/unread-count');
  const data = await res.json();
  return data.unreadCount;  // 예: 5
};

// 알림 목록 조회 (페이징)
const fetchNotifications = async (page = 0) => {
  const res = await fetch(`/notification/v1/notifications?page=${page}&size=20`);
  return await res.json();
};

// 단건 읽음 처리
const markAsRead = async (notificationId) => {
  await fetch(`/notification/v1/notifications/${notificationId}/read`, {
    method: 'PATCH'
  });
};

// 전체 읽음 처리
const markAllAsRead = async () => {
  const res = await fetch('/notification/v1/notifications/read-all', {
    method: 'PATCH'
  });
  const data = await res.json();
  return data.updatedCount;
};

// 단건 삭제
const deleteNotification = async (notificationId) => {
  await fetch(`/notification/v1/notifications/${notificationId}`, {
    method: 'DELETE'
  });
};

// 전체 삭제
const deleteAllNotifications = async () => {
  await fetch('/notification/v1/notifications', {
    method: 'DELETE'
  });
};
```

> **참고:** `EventSource`는 기본적으로 GET 요청만 지원하며, 커스텀 헤더는 보낼 수 없습니다.
> Gateway가 JWT에서 `X-User-Id`를 자동 주입하므로, 프론트에서는 쿠키/세션 기반 인증으로 Gateway를 통해 접근하면 됩니다.
> 만약 커스텀 헤더가 필요하면 `fetch` API의 `ReadableStream`이나 `eventsource-polyfill` 라이브러리를 사용하세요.

---

## 3. FCM 푸시 토큰 관리

### 3-1. FCM 토큰 등록

```
POST /notification/v1/fcm/token
```

**Request Body:**
```json
{
  "token": "firebase_fcm_token_string",
  "deviceType": "WEB"
}
```

**Response:** `200 OK`

---

### 3-2. FCM 토큰 해제

```
DELETE /notification/v1/fcm/token?token={fcm_token}
```

**Response:** `200 OK`

---

## 4. 알림 설정

### 4-1. 알림 설정 조회

```
GET /notification/v1/settings/{userId}
```

**Response (200):**
```json
{
  "meetingNotification": true,
  "systemNotification": true,
  "marketingNotification": false
}
```

---

### 4-2. 알림 설정 업데이트

```
POST /notification/v1/settings/{userId}
```

**Request Body:**
```json
{
  "meetingNotification": true,
  "systemNotification": true,
  "marketingNotification": false
}
```

**Response:** `200 OK`

---

## 5. NotificationType 목록

| Type | 설명 | 템플릿 |
|---|---|---|
| `MEETING_INVITATION` | 회의 초대 | {senderName}님이 {title} 회의에 초대했습니다. |
| `INVITATION_ACCEPTED` | 초대 수락 | {senderName}님이 회의 초대를 수락했습니다. |
| `INVITATION_DECLINED` | 초대 거절 | {senderName}님이 회의 초대를 거절했습니다. |
| `INVITATION_CANCELLED` | 초대 취소 | {title} 회의 초대가 취소되었습니다. |
| `PARTICIPANT_KICKED` | 퇴장 | {title} 회의에서 퇴장되었습니다. |
| `WAITING_ROOM_APPROVED` | 대기실 승인 | {title} 회의 입장이 승인되었습니다. |
| `WAITING_ROOM_REJECTED` | 대기실 거절 | {title} 회의 입장이 거절되었습니다. |
| `PARTICIPANT_JOINED` | 참가자 입장 | {senderName}님이 {title} 회의에 입장했습니다. |
| `MEETING_STARTED` | 회의 시작 | {title} 회의가 시작되었습니다. |
| `MEETING_ENDED` | 회의 종료 | {title} 회의가 종료되었습니다. |
| `MEETING_RESCHEDULED` | 일정 변경 | {title} 회의 일정이 변경되었습니다. |
| `MEETING_CANCELLED` | 회의 취소 | {title} 회의가 취소되었습니다. |
| `MEETING_REMINDER` | 리마인더 | {title} 회의가 곧 시작됩니다. |
| `SYSTEM` | 시스템 알림 | {body} |
| `EVENT` | 이벤트 알림 | {body} |

---

## 6. 내부 서비스용 API (서비스 간 통신)

### 알림 발송 (video-service → notification-service)

```
POST /notification/internal/send
X-Gateway-Secret: {shared_secret}
```

**Request Body:**
```json
{
  "userId": 42,
  "actorUserId": 7,
  "type": "MEETING_INVITATION",
  "title": "주간회의",
  "body": null,
  "resourceType": "MEETING",
  "resourceId": "123",
  "deeplink": "/meeting/123"
}
```
