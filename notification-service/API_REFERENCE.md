# Notification Service API Reference

**Version**: v0.1.0
**Last Updated**: 2026-03-08
**Status**: Production

실시간 알림(SSE) 및 알림 이력 관리를 담당하는 서비스입니다.

## 기본 정보 (Base Info)
- **Base URL**: `/notification` (Gateway 경유 시)
- **Port**: `8085`

## SSE (Server-Sent Events)

### 1. SSE 알림 구독 (Subscribe)
현재 인증된 사용자의 실시간 알림 스트림에 구독합니다.

- **URL**: `/notification/v1/sse/subscribe`
- **Method**: `GET`
- **Auth**: Required
- **Content-Type**: `text/event-stream`

#### Headers
| Header | Type | Required | Description |
|---|---|---|---|
| `X-User-Id` | Long | Yes | 사용자 ID (Gateway에서 자동 주입) |

#### Response
- **Status**: `200 OK`
- **Body**: SSE 스트림
```text
data: {"id": 1, "message": "New notification", "type": "INFO", ...}

data: {"id": 2, "message": "Another notification", "type": "ALERT", ...}
```

#### Notes
- 로그인 후 프론트엔드에서 이 엔드포인트를 호출하여 SSE 연결을 유지해야 합니다
- 멀티탭/멀티디바이스 지원 - 동일 사용자의 다중 연결 허용
- 연결이 끊어지면 자동 재연결 권장

---

## 알림 관리 (Notification Management)

### 2. 내 알림 목록 조회 (Get My Notifications)
현재 로그인된 사용자의 알림 목록을 최신순으로 페이징하여 조회합니다.

- **URL**: `/notification/v1/notifications`
- **Method**: `GET`
- **Auth**: Required

#### Headers
| Header | Type | Required | Description |
|---|---|---|---|
| `X-User-Id` | Long | Yes | 사용자 ID (Gateway에서 자동 주입) |

#### Query Parameters
| Param | Type | Required | Default | Description |
|---|---|---|---|---|
| `page` | Integer | No | 0 | 페이지 번호 (0부터 시작) |
| `size` | Integer | No | 20 | 페이지당 항목 수 |
| `sort` | String | No | createdAt,DESC | 정렬 기준 |

#### Response
- **Status**: `200 OK`
- **Body**: `Page<NotificationResponseDto>`
```json
{
  "content": [
    {
      "id": 1,
      "type": "MEETING_INVITE",
      "title": "회의 초대",
      "message": "Team meeting at 3 PM",
      "isRead": false,
      "createdAt": "2026-03-08T10:00:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 50,
  "totalPages": 3
}
```

---

### 3. 미읽음 알림 수 조회 (Get Unread Count)
현재 로그인된 사용자의 읽지 않은 알림 개수를 반환합니다.

- **URL**: `/notification/v1/notifications/unread/count`
- **Method**: `GET`
- **Auth**: Required

#### Headers
| Header | Type | Required | Description |
|---|---|---|---|
| `X-User-Id` | Long | Yes | 사용자 ID (Gateway에서 자동 주입) |

#### Response
- **Status**: `200 OK`
- **Body**:
```json
{
  "unreadCount": 5
}
```

---

### 4. 알림 단건 읽음 처리 (Mark As Read)
특정 알림 ID를 읽음 처리합니다.

- **URL**: `/notification/v1/notifications/{notificationId}/read`
- **Method**: `PATCH`
- **Auth**: Required

#### Path Parameters
| Param | Type | Required | Description |
|---|---|---|---|
| `notificationId` | Long | Yes | 알림 ID |

#### Headers
| Header | Type | Required | Description |
|---|---|---|---|
| `X-User-Id` | Long | Yes | 사용자 ID (Gateway에서 자동 주입) |

#### Response
- **Status**: `200 OK`

---

### 5. 모든 알림 읽음 처리 (Mark All As Read)
현재 로그인된 사용자의 모든 알림을 읽음 처리합니다.

- **URL**: `/notification/v1/notifications/read/all`
- **Method**: `PATCH`
- **Auth**: Required

#### Headers
| Header | Type | Required | Description |
|---|---|---|---|
| `X-User-Id` | Long | Yes | 사용자 ID (Gateway에서 자동 주입) |

#### Response
- **Status**: `200 OK`
- **Body**:
```json
{
  "updatedCount": 10
}
```

---

### 6. 알림 단건 삭제 (Delete Notification)
특정 알림 ID를 삭제합니다.

- **URL**: `/notification/v1/notifications/{notificationId}`
- **Method**: `DELETE`
- **Auth**: Required

#### Path Parameters
| Param | Type | Required | Description |
|---|---|---|---|
| `notificationId` | Long | Yes | 알림 ID |

#### Headers
| Header | Type | Required | Description |
|---|---|---|---|
| `X-User-Id` | Long | Yes | 사용자 ID (Gateway에서 자동 주입) |

#### Response
- **Status**: `200 OK`

---

### 7. 모든 알림 삭제 (Delete All Notifications)
현재 로그인된 사용자의 모든 알림을 삭제합니다.

- **URL**: `/notification/v1/notifications/all`
- **Method**: `DELETE`
- **Auth**: Required

#### Headers
| Header | Type | Required | Description |
|---|---|---|---|
| `X-User-Id` | Long | Yes | 사용자 ID (Gateway에서 자동 주입) |

#### Response
- **Status**: `200 OK`

---

## 에러 코드 (Error Codes)

| Status Code | Description |
|---|---|
| `400 Bad Request` | 잘못된 요청 데이터 |
| `401 Unauthorized` | 인증 실패 |
| `404 Not Found` | 알림을 찾을 수 없음 |
| `500 Internal Server Error` | 서버 내부 오류 |
