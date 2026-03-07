# Gateway Service Routes Reference

API Gateway의 라우팅 규칙 및 포트 매핑 정보입니다.
Base URL: `http://localhost:8080`

## 라우팅 테이블 (Routing Table)

| Path Prefix | Service | Target URL | 설명 |
|---|---|---|---|
| `/auth/**` | **Auth Service** | `http://auth-service:8081` | 인증, 토큰, 회원 관리 |
| `/ai/**` | **AI Service** | `http://ai-service:8082` | AI 요약, 분석 |
| `/video/**` | **Video Service** | `http://video-service:8083` | 화상 회의 |
| `/chat/**` | **Chat Service** | `http://chat-service:8084` | 채팅 |
| `/notification/**` | **Notification** | `http://notification-service:8085` | 알림 (SSE) |
| `/file/**` | **File Service** | `http://file-service:8086` | 파일 관리 |
| `/image/**` | **File Service** | `http://image-service:8086` | 이미지 관리 (File Service로 라우팅) |

## 필터 (Filters)
- **UserHeaderFilter**: 인증된 요청의 헤더(`X-User-Id` 등)를 다운스트림 서비스로 전달합니다.
