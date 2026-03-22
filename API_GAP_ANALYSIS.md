# API 연결 문제 분석 리포트

분석일: 2026-03-14
대상: onmeet-frontend (develop) / onmeet-backend (develop)

---

## 1. 백엔드에만 존재하는 API (프론트엔드 미사용)

### auth-service

| HTTP 메서드 | API 경로 | 설명 | 비고 |
|------------|---------|------|------|
| POST | /auth/v1/password/find | 비밀번호 찾기 | 프론트 페이지 미구현 |

### ai-service (전체 미연동)

| HTTP 메서드 | API 경로 | 설명 | 비고 |
|------------|---------|------|------|
| GET | /ai/v1/me | AI 서비스 상태 확인 | 프론트 미사용 |
| GET | /ai/v1/minutes/{roomId} | 회의록 조회 | 프론트 미사용 |
| POST | /ai/v1/minutes/{roomId}/regenerate | 회의록 재생성 | 프론트 미사용 |
| PATCH | /ai/v1/minutes/{roomId} | 회의록 수정 | 프론트 미사용 |
| GET | /ai/v1/minutes/{roomId}/transcript | 트랜스크립트 조회 | 프론트 미사용 |

### file-service (전체 미연동)

| HTTP 메서드 | API 경로 | 설명 | 비고 |
|------------|---------|------|------|
| POST | /file/v1/upload | 파일 업로드 | auth-service 경유 처리 |
| POST | /file/v1/upload-async | 비동기 파일 업로드 | auth-service 경유 처리 |
| POST | /file/v1/profile/default | 기본 프로필 이미지 생성 | auth-service 경유 처리 |
| GET | /file/v1/render/:fileId | 파일 렌더링 | auth-service 경유 처리 |
| GET | /file/v1/:fileId | 파일 정보 조회 | auth-service 경유 처리 |
| DELETE | /file/v1/:fileId | 파일 삭제 | auth-service 경유 처리 |
| DELETE | /file/v1/me/profile | 내 프로필 삭제 | auth-service 경유 처리 |

### video-service

| HTTP 메서드 | API 경로 | 설명 | 비고 |
|------------|---------|------|------|
| GET (SSE) | /video/api/rooms/{roomId}/waiting/sse/participant | 대기실 SSE (참가자) | 프론트 미사용 |
| GET (SSE) | /video/api/rooms/{roomId}/waiting/sse/host | 대기실 SSE (호스트) | 프론트 미사용 |

### 내부 전용 API (정상 - 프론트 호출 불필요)

| 서비스 | 엔드포인트 수 | 용도 |
|--------|-------------|------|
| auth-service | 9개 | /v1/internal/*, /internal/* (서비스 간 통신) |
| video-service | 2개 | /rooms/internal/chat-token, /webhook/livekit |

---

## 2. 경로/구조 불일치

| 심각도 | 서비스 | 이슈 | 상세 |
|--------|-------|------|------|
| 높음 | video-service | Gateway 라우트 불일치 | Gateway: `Path=/video/v1/**`, 백엔드 컨트롤러: `/api/rooms/**`. context-path `/video` 적용 시 실제 서블릿 경로가 Gateway 라우트와 불일치할 수 있음 |
| 높음 | notification | X-User-Id 헤더 누락 | `getNotificationSettings(userId)`, `updateNotificationSettings(userId)` 호출 시 `notiFetch` 두 번째 인자(userId) 미전달로 X-User-Id 헤더 없이 요청 |

---

## 3. 프론트엔드 UI 미연결 (버튼/액션 존재, API 미호출)

### 심각도: 높음

| 파일 | 문제 | 관련 API |
|------|------|----------|
| `auth/pages/InviteMembers.tsx:55` | `actualInviteToken` 미정의 변수 사용 -> 런타임 ReferenceError | inviteMember |
| `auth/pages/InviteMembers.tsx:61-80` | "완료" 버튼 클릭 시 `console.log`만 실행 후 navigate | inviteMember |
| `schedule/components/MeetingBookingModal.tsx:135-136` | 회의 예약 시 `await new Promise(setTimeout(500))` + `console.log` -> fake async | scheduleRoom |
| `dashboard/components/CreateMeetingModal.tsx:23-27` | 회의 생성 폼 submit 시 `console.log`만 -> 모달만 닫힘 | createRoom |
| `settings/pages/MyPage.tsx:78-80` | 프로필 저장 버튼 -> `setEditMode(false)`만 호출 | updateProfile |
| `settings/pages/MyPage.tsx:487-489` | 계정 삭제 확인 -> `logout()` + navigate만, 실제 삭제 없음 | withdraw |
| `settings/pages/CompanyManagement.tsx:205-208` | 회사 정보 수정 저장 -> 로컬 상태만 업데이트 | 백엔드 API 없음 |
| `settings/pages/CompanyManagement.tsx:215-229` | 사원 권한(매니저) 토글 -> 로컬 상태만 변경 | activateUser / deactivateUser |
| `settings/pages/CompanyManagement.tsx:231-237` | 팀 리더 변경 -> 로컬 상태만 변경 | assignLeader / delegateLeader |
| `settings/pages/CompanyManagement.tsx:248-267` | 팀 생성 승인 -> 로컬 상태만 변경 | approveTeam |
| `settings/pages/CompanyManagement.tsx:269-271` | 팀 생성 거절 -> 로컬 상태만 변경 | rejectTeam |

### 심각도: 중간

| 파일 | 문제 |
|------|------|
| `settings/pages/MyPage.tsx:439` | "설정 및 보안" 탭 비밀번호 변경 버튼 -> onClick 핸들러 없음 |
| `team/pages/TeamBoard.tsx:189` | "회의 생성" 버튼 -> onClick 핸들러 없음 |
| `settings/pages/CompanyManagement.tsx:572` | "팀 생성" 버튼 -> onClick 핸들러 없음 |
| `schedule/pages/Schedule.tsx:307-309` | 일별 상세 "회의 참여" 버튼 -> onClick 핸들러 없음 |
| `dashboard/pages/Summary.tsx:554-566` | 다운로드 메뉴 "음성 파일", "로우 텍스트", "전체 회의록" 3개 버튼 전부 onClick 없음 |
| `dashboard/pages/Summary.tsx:344-346` | 전체 회의록 "저장" 버튼 -> editingMeetingId 초기화만, API 저장 없음 |
| `dashboard/pages/Summary.tsx:467-478` | 음성 재생 플레이어 -> 상태만 변경, 실제 Audio API 미연결 |

### 심각도: 낮음

| 파일 | 문제 |
|------|------|
| `dashboard/pages/Summary.tsx:311-313` | "태그 추가" 버튼 -> onClick 핸들러 없음 |

---

## 4. 임의/하드코딩 데이터 (API 미연동)

| 파일 | 문제 | 사용해야 할 API |
|------|------|---------------|
| `dashboard/components/OngoingMeetings.tsx:24-140` | 진행 중/예정 회의 목록 전부 하드코딩 | listRooms |
| `dashboard/components/OngoingMeetings.tsx:142-149` | `Math.random()`으로 남은 시간 계산 | getRoomStats |
| `dashboard/components/RecentMeetings.tsx:38-122` | 최근 회의 목록 하드코딩 (베트남어 dummy text 포함) | listRoomHistory |
| `dashboard/pages/Summary.tsx:47-197` | 회의 내역 전체(`allMeetings`) mock 데이터 | ai-service minutes API |
| `schedule/pages/Schedule.tsx:34-196` | 일정 페이지 회의 목록 전체 하드코딩 | listScheduledRooms |
| `team/pages/TeamBoard.tsx:27-145` | 팀별 회의 보드 전체 하드코딩 | listRooms (팀 필터) |
| `settings/pages/CompanyManagement.tsx:71-187` | 회사 정보, 사원 목록, 팀 목록, 팀 생성 요청 전부 하드코딩 | getAllEmployees, manager API |
| `schedule/components/MeetingBookingModal.tsx:41-82` | 참여자 검색 `mockEmployees` 하드코딩 | getAllEmployees |
| `settings/pages/MyPage.tsx:53-60` | phone, position, team, bio 초기값 하드코딩 | getMe |
| `team/pages/Team.tsx:4-8` | teamNames 맵 하드코딩 (marketing, product, design만) | 백엔드 팀 목록 API |

---

## 5. 통계 요약

| 항목 | 수 |
|------|---|
| 백엔드 전체 API | 122개 |
| 프론트엔드 API 호출 | 103개 |
| 백엔드에만 존재 (내부 API 제외) | 15개 |
| 프론트엔드 미연결 버튼/액션 | 15개 |
| 하드코딩 데이터 페이지 | 10개 |
| 잘못된 호출 (경로/헤더) | 2개 |
| 런타임 에러 (ReferenceError) | 1개 |

---

## 6. 우선 수정 권장 사항

### 즉시 수정 필요

1. **InviteMembers.tsx** - `actualInviteToken` ReferenceError로 런타임 크래시 발생
2. **notification/api.ts** - settings API의 X-User-Id 헤더 누락으로 인증 실패
3. **MyPage.tsx 프로필 저장/계정 삭제** - 사용자가 저장/삭제했다고 인지하지만 실제로는 미동작

### 단기 수정 권장

4. **CompanyManagement.tsx 전체** - 관리자 기능(사원 관리, 팀 승인/거절, 리더 임명) 전부 API 미연동
5. **CreateMeetingModal, MeetingBookingModal** - 회의 생성/예약 핵심 기능 API 미연결
6. **Dashboard 하드코딩 데이터** - OngoingMeetings, RecentMeetings, Summary 페이지

### 중기 수정 권장

7. **ai-service 연동** - 회의록 조회/수정/재생성 API 프론트 연결
8. **Schedule, TeamBoard 페이지** - 하드코딩 -> API 연동
9. **video-service Gateway 라우트 검증** - `/video/v1/**` vs `/api/rooms/**` 불일치 확인
