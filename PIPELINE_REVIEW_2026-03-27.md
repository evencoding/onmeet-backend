# API 파이프라인 점검 리포트 (2026-03-27)

## 1. 회의 생성/초대 정합성 점검

### API/DTO 정합성 — 정상

| 항목 | 프론트엔드 | 백엔드 | 결과 |
|------|-----------|--------|------|
| `POST /video/v1/rooms` 생성 DTO | `RoomCreateRequest` (7필드) | `RoomCreateRequest` (7필드) | ✅ 일치 |
| `POST /video/v1/rooms/schedule` 예약 DTO | `RoomScheduleRequest` (7필드) | `RoomScheduleRequest` (7필드) | ✅ 일치 |
| `POST /video/v1/rooms/{id}/invite/bulk` | `{ inviteeUserIds: number[] }` | `List<Long> inviteeUserIds` | ✅ 일치 |
| `InvitationResponse` | 6필드 (id, roomId, inviterUserId, inviteeUserId, status, createdAt) | 동일 6필드 | ✅ 일치 |
| `scheduledAt` 포맷 | ISO 8601 `"yyyy-MM-ddTHH:mm:00"` | `LocalDateTime` 역직렬화 | ✅ 호환 |

### 발견된 이슈

| # | 심각도 | 영역 | 이슈 | 상태 |
|---|--------|------|------|------|
| 1 | 🔴 Critical | 프론트 | 초대 수락/거절 UI 미구현 — `useAcceptInvitation`/`useDeclineInvitation` Hook 있으나 연결된 버튼 없음 | 초대 흐름 끊김 |
| 2 | 🔴 Critical | 프론트 | 회의실 내 초대 `InviteParticipantModal.onInvite = () => {}` — 백엔드 API 존재하나 호출 안됨 | 기능 미동작 |
| 3 | 🟡 Medium | 프론트 | 초대 목록 UI 미구현 — `useInvitations()` Hook 정의되었으나 사용하는 컴포넌트 없음 | UI 부재 |
| 4 | 🟡 Medium | 설계 | accept/join 분리 — 수락 시 상태만 ACCEPTED, 실제 입장은 별도 join API 필요 | UX 결정 필요 |
| 5 | 🟡 Medium | 백엔드 | `schedule()` 서비스에서 `type=SCHEDULED` 강제 설정 확인 필요 | 코드 확인 |
| 6 | ⚪ N/A | 양쪽 | 이메일 초대 — 프론트/백엔드 모두 미구현 (정합성 이슈 아님) | 미구현 |

### 관련 파일

**프론트엔드:**
- `client/features/dashboard/components/CreateMeetingModal.tsx` — 즉시/예약 생성 모달
- `client/features/schedule/components/MeetingBookingModal.tsx` — 예약+초대 3단계 모달
- `client/features/meeting/components/InviteParticipantModal.tsx` — 회의실 내 초대
- `client/features/meeting/api/types.ts` — DTO 타입 정의
- `client/features/meeting/hooks/useInvitation.ts` — 초대 관련 Hook

**백엔드:**
- `video-service/.../controller/room/MeetingRoomController.java` — 회의 생성 API
- `video-service/.../controller/invitation/RoomInvitationController.java` — 초대 API
- `video-service/.../service/room/MeetingRoomService.java` — 회의 비즈니스 로직
- `video-service/.../service/invitation/RoomInvitationService.java` — 초대 비즈니스 로직

---

## 2. 녹음/회의록 파이프라인 점검

### 수정 완료 (2026-03-27)

| 항목 | 문제 | 조치 |
|------|------|------|
| ai-service Flyway V4 | 마이그레이션 실패로 122회 재시작 반복 | ALTER TABLE 수동 적용 + flyway_schema_history success=1 수정 |
| LiveKit Egress 서비스 | 미배포 상태 — 녹음 불가 | `livekit/egress:latest` 신규 배포, MinIO S3 + Redis 설정 |
| LiveKit Server Redis | Egress 서비스와 통신 불가 | `livekit.yaml`에 `redis.address: redis:6379` 추가 |

### 파이프라인 현재 상태

```
① LiveKit Egress → MinIO 오디오 저장             ✅ 설정 완료
② webhook(egress_ended) → video-service           ✅ 설정 완료
③ video-service → file-service /register-s3       ✅ API 존재
④ Kafka audio.chunk.ready → ai-service STT        ✅ Consumer 연결됨
⑤ Kafka meeting.ended → Transcript 취합           ✅ Consumer 연결됨
⑥ Kafka transcript.finalized → Claude 요약        ✅ Consumer 연결됨
⑦ Minutes DB 저장                                 ✅ 엔티티/테이블 정상
```

> E2E 테스트는 브라우저에서 실제 회의 녹음으로 검증 필요

---

## 3. SSE 알림 테스트

### 테스트 결과 (2026-03-27)

- 테스트 계정: `sse-test-1774538200@test.com` (userId=7, ROLE_MANAGER)
- SSE 연결: `event:connect` 정상 수신 ✅
- 테스트 알림 전송: `MEETING_INVITATION` 타입 전송 성공 ✅
- SSE 실시간 수신: `event:notification` 정상 수신 ✅
- Cloudflare 프록시 경유 SSE 스트리밍 정상 동작 확인

---

*점검 수행: Claude Code 에이전트 팀 (frontend-analyst + backend-analyst)*
*최종 업데이트: 2026-03-27*
