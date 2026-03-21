# OnMeet API 테스트 이슈 추적 (v0.4.8)

> 기준: `onmeet-test-report.html` 실측 테스트 (2026-03-21)
> 작성일: 2026-03-22
> **최종 업데이트: 2026-03-22 — 전 섹션 완료**

---

## Section 1: Auth Service — 비밀번호 관련 (Backend) — PR #79 Merged

| # | 심각도 | 이슈 | 엔드포인트 | 상태 |
|---|--------|------|-----------|------|
| B1 | **500 CRITICAL** | 비밀번호 변경 시 서버 내부 오류 (DTO 필드명 불일치 `oldPassword`→`currentPassword`) | `PUT /auth/v1/member/me/password` | [x] |
| B2 | **401 BUG** | 비밀번호 찾기 API Gateway 화이트리스트 누락 | `POST /auth/v1/password/find` | [x] |

---

## Section 2: Video Service — 회의 생성/예약 (Backend) — PR #82 Merged

| # | 심각도 | 이슈 | 엔드포인트 | 상태 |
|---|--------|------|-----------|------|
| B3 | **500 CRITICAL** | 즉시 회의 생성 전체 실패 (`@EnableJpaAuditing` 누락) | `POST /video/v1/rooms` | [x] |
| B4 | **500 CRITICAL** | 회의 예약 생성 전체 실패 (동일 원인 + auth URL config 키 불일치) | `POST /video/v1/rooms/schedule` | [x] |

---

## Section 3: Notification Service — 서비스 DOWN (Backend/Infra) — PR #80 Merged

| # | 심각도 | 이슈 | 엔드포인트 | 상태 |
|---|--------|------|-----------|------|
| B5 | **DOWN** | Notification 서비스 actuator 경로 이중화로 기동 불가 | `/notification/v1/*` 전체 | [x] |
| B6 | **404** | Gateway에서 notification actuator 라우트 누락 | `/notification/actuator/health` | [x] |

---

## Section 4: API 정합성 — 프론트-백엔드 불일치 (Backend) — PR #81 Merged

> 출처: 기존 분석 `project_api_mismatch_v0.4.md`

### P0 — 즉시 수정

| # | 이슈 | 영향 | 상태 |
|---|------|------|------|
| F1 | 채팅 토큰 조회 엔드포인트 — 이미 존재 확인 (`ChatIntegrationController`) | 이슈 아님 | [x] |
| F2 | 방 코드로 회의방 조회 응답 타입 — 호환 가능 (additive), 프론트 타입 업데이트 | 프론트 PR #22에서 수정 | [x] |

### P1 — 기능 오동작

| # | 이슈 | 영향 | 상태 |
|---|------|------|------|
| F3 | 알림 전체 읽음 처리 — 백엔드 이미 Map 반환 중 | 이슈 아님 | [x] |
| F4 | 알림 미읽음 수 조회 — 백엔드 타입별 Map으로 변경 (PR #81) | 프론트 호환 완료 | [x] |
| F5 | 멤버 초대 응답 — 프론트 `Promise<number[]>`로 수정 (PR #22) | 프론트에서 수정 | [x] |

### P2 — 경미

| # | 이슈 | 영향 | 상태 |
|---|------|------|------|
| F6 | 화면 공유 응답 — 프론트 `ScreenShareResponse` 타입으로 수정 (PR #22) | 프론트에서 수정 | [x] |
| F7 | 게스트 초대 roomId — 프론트 `number` 타입으로 수정 (PR #22) | 프론트에서 수정 | [x] |

### 기능 누락

| # | 이슈 | 상태 |
|---|------|------|
| F8 | 단일 멤버 초대 (역할 지정) — 프론트 구현 완료 (PR #23) | [x] |
| F9 | 회사 정보 수정 — 프론트 구현 완료 (PR #23) | [x] |

---

## Section 5: 팀 생성 UX + 프론트 정합성 (Frontend) — PR #22 Merged

| # | 이슈 | 상태 |
|---|------|------|
| F10 | 팀 생성 모달 MANAGER 역할 시 memberIds/leaderId 자동 포함 | [x] |

---

## 완료 기준

- [x] Section 1 (Auth 비밀번호) — PR #79 merged
- [x] Section 2 (Video 회의) — PR #82 merged
- [x] Section 3 (Notification DOWN) — PR #80 merged
- [x] Section 4 (API 정합성 Backend) — PR #81 merged
- [x] Section 5 (Frontend 정합성 + 팀 생성 UX) — PR #22 merged (onmeet-frontend)
- [x] `onmeet-test-report.html` 삭제

## 전체 완료

- [x] F8: 단일 멤버 초대 UI 프론트 구현 — PR #23 merged (onmeet-frontend)
- [x] F9: 회사 정보 수정 UI 프론트 구현 — PR #23 merged (onmeet-frontend)
