# Email Service API Reference

**Version**: v0.2.0
**Last Updated**: 2026-03-14
**Status**: Production

이메일 발송 기능을 담당하는 서비스입니다. Kafka Consumer 기반으로 동작하며, REST API는 헬스 체크 용도로만 사용됩니다.

**Tech Stack**: Java 17, Spring Boot 3.3.5, Gmail SMTP (OAuth2/XOAUTH2), Thymeleaf, Kafka, Lombok

## 기본 정보 (Base Info)

- **Port**: `8087`
- **Context Path**: `/email`
- **Email Provider**: Gmail SMTP (OAuth2 XOAUTH2 인증)
- **이메일 발송 방식**: Kafka 이벤트 드리븐 (`email-send-topic`)

## Endpoints

### 1. 헬스 체크 (Health Check)

서비스 상태를 확인합니다.

- **URL**: `/email/v1/health`
- **Method**: `GET`
- **Auth**: None

#### Response

- **Status**: `200 OK`
- **Body**: `String`

```text
Email Service is running
```

## Kafka Consumer

### email-send-topic

이메일 발송 요청을 수신하는 Kafka 토픽입니다.

- **Topic**: `email-send-topic`
- **Group ID**: `email-service-group`
- **Payload**: JSON (UTF-8)

#### Payload Schema

```json
{
  "to": "recipient@example.com",
  "subject": "이메일 제목",
  "templateName": "company-invitation",
  "variables": {
    "key": "value"
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `to` | String | Y | 수신자 이메일 주소 |
| `subject` | String | Y | 이메일 제목 |
| `templateName` | String | Y | Thymeleaf 템플릿 이름 (화이트리스트 검증) |
| `variables` | Map<String, Object> | N | 템플릿 렌더링에 사용할 변수 |

### 지원 템플릿 (Allowed Templates)

`templateName`은 아래 3가지 값만 허용됩니다. 그 외 값은 Path Traversal 방지를 위해 거부됩니다.

#### company-invitation

회사 워크스페이스 초대 이메일입니다.

| 변수 | 설명 |
|------|------|
| `companyName` | 초대된 회사명 |
| `invitationCode` | 가입 초대 코드 |

**예시 payload:**
```json
{
  "to": "user@example.com",
  "subject": "[Onmeet] 회사 초대 코드 안내",
  "templateName": "company-invitation",
  "variables": {
    "companyName": "Acme Corp",
    "invitationCode": "ABC123"
  }
}
```

#### guest-invitation

화상회의 게스트 초대 이메일입니다.

| 변수 | 설명 |
|------|------|
| `hostName` | 초대한 호스트 이름 |
| `roomName` | 화상회의 방 이름 |

**예시 payload:**
```json
{
  "to": "guest@example.com",
  "subject": "[Onmeet] 화상회의 초대",
  "templateName": "guest-invitation",
  "variables": {
    "hostName": "홍길동",
    "roomName": "분기별 전략 회의"
  }
}
```

#### temporary-password

임시 비밀번호 발급 이메일입니다.

| 변수 | 설명 |
|------|------|
| `userName` | 수신자 이름 |
| `temporaryPassword` | 발급된 임시 비밀번호 |

**예시 payload:**
```json
{
  "to": "user@example.com",
  "subject": "[Onmeet] 임시 비밀번호 발급 안내",
  "templateName": "temporary-password",
  "variables": {
    "userName": "김철수",
    "temporaryPassword": "Tmp@1234"
  }
}
```

## 에러 처리

에러 발생 시 `ErrorResponse` 형식으로 응답합니다.

```json
{
  "code": "ERROR_CODE",
  "status": 400,
  "message": "에러 메시지",
  "timestamp": 1710000000000
}
```

### 주요 에러 코드 (EmailErrorCode)

| 코드 | 설명 |
|------|------|
| `INVALID_TEMPLATE` | 허용되지 않은 templateName 요청 |
| `CREDENTIALS_MISSING` | Gmail OAuth2 자격증명 미설정 |
| `TOKEN_REFRESH_FAILED` | Google OAuth2 토큰 갱신 실패 |
| `TOKEN_RESPONSE_INVALID` | Google 토큰 응답에 access_token 누락 |
| `TOKEN_NETWORK_ERROR` | Google 토큰 엔드포인트 네트워크 오류 |
| `OAUTH2_TOKEN_CONFIG_FAILED` | JavaMailSender OAuth2 토큰 설정 실패 |
| `SEND_FAILED` | 이메일 발송 실패 |
