# Git Workflow & Versioning Strategy

## 브랜치 전략

```
develop (개발)
   │
   ├─► feat/ONMEET-XX (기능 개발)
   │
   └─► release/v0.x.0 (릴리즈 준비) ──► main (프로덕션 안정 버전)
              │
              └─► CI/CD 배포 자동 실행
```

### 브랜치 설명

| 브랜치 | 용도 | 보호 규칙 |
|--------|------|-----------|
| `main` | 프로덕션 안정 버전 (배포 완료된 코드) | Protected, PR only |
| `release/v0.x.0` | 릴리즈 준비 및 QA (배포 트리거) | CI/CD 자동 배포 |
| `develop` | 개발 통합 브랜치 | Default branch |
| `feat/ONMEET-XX` | 기능 개발 브랜치 | develop으로 PR |

## 버전 관리 (Semantic Versioning)

### 현재 단계: Pre-release (0.x.y)

```
0.1.0 - 초기 MVP
0.2.0 - 새로운 기능 추가
0.2.1 - 버그 픽스
0.3.0 - 다음 기능 릴리즈
...
1.0.0 - 정식 출시 (Production Ready)
```

### 버전 증가 규칙

- **`0.x.0`** - 마이너 버전 증가
  - 새로운 기능 추가
  - API 변경 (breaking change 가능)
  - 주요 개선사항

- **`0.x.y`** - 패치 버전 증가
  - 버그 수정
  - 작은 개선
  - 보안 패치

- **`1.0.0`** - 메이저 버전 (정식 출시)
  - 프로덕션 준비 완료
  - API 안정화
  - 고객 배포 시작

## 릴리즈 프로세스

### 1. 새 릴리즈 준비

```bash
# develop 브랜치에서 시작
git checkout develop
git pull origin develop

# 릴리즈 브랜치 생성 (예: v0.2.0)
git checkout -b release/v0.2.0

# 버전 정보 업데이트 (선택사항)
# - build.gradle의 version 수정
# - CHANGELOG.md 작성

git add .
git commit -m "chore: prepare release v0.2.0"
git push origin release/v0.2.0
```

### 2. 자동 배포 실행

```bash
# release/* 브랜치에 push하면 GitHub Actions가 자동으로:
# 1. 변경된 서비스 감지
# 2. Docker 이미지 빌드 (태그: v0.2.0, latest)
# 3. Docker Hub에 푸시
# 4. GCP 서버에 자동 배포
```

### 3. QA 및 테스트

```bash
# 릴리즈 브랜치에서 버그 수정
git checkout release/v0.2.0
# 버그 수정 작업...
git add .
git commit -m "fix: critical bug in auth service"
git push origin release/v0.2.0
# → 자동으로 재배포됨
```

### 4. 릴리즈 완료

```bash
# main 브랜치로 병합 (프로덕션 안정 버전으로 등록)
git checkout main
git merge --no-ff release/v0.2.0
git tag -a v0.2.0 -m "Release v0.2.0"
git push origin main --tags

# develop 브랜치로 역병합 (수정사항 반영)
git checkout develop
git merge --no-ff release/v0.2.0
git push origin develop

# 릴리즈 브랜치 삭제 (선택사항)
git branch -d release/v0.2.0
git push origin --delete release/v0.2.0
```

## 핫픽스 프로세스 (긴급 수정)

```bash
# main 브랜치에서 핫픽스 브랜치 생성
git checkout main
git checkout -b hotfix/v0.2.1

# 버그 수정
git add .
git commit -m "fix(auth): critical security patch"

# main에 병합 및 태그
git checkout main
git merge --no-ff hotfix/v0.2.1
git tag -a v0.2.1 -m "Hotfix v0.2.1"
git push origin main --tags

# develop에도 반영
git checkout develop
git merge --no-ff hotfix/v0.2.1
git push origin develop

# 핫픽스 브랜치 삭제
git branch -d hotfix/v0.2.1
```

## CI/CD 배포 조건

### 자동 배포 트리거

- `release/*` 브랜치에 Push
- 예: `release/v0.1.0`, `release/v0.2.0`

### 배포 결과

- Docker 이미지 태그:
  - `username/service-name:v0.2.0` (버전 태그)
  - `username/service-name:latest` (최신 태그)

## 브랜치 보호 규칙 (GitHub Settings)

### `main` 브랜치

- ✅ Require pull request before merging
- ✅ Require status checks to pass
- ✅ Require branches to be up to date
- ✅ Require linear history
- ✅ Do not allow bypassing the above settings

### `develop` 브랜치

- ✅ Require pull request before merging
- ✅ Require status checks to pass

## 커밋 메시지 컨벤션

```
type(scope): subject

body (선택)

footer (선택)
```

### Type

- `feat`: 새로운 기능
- `fix`: 버그 수정
- `chore`: 빌드/설정 변경
- `docs`: 문서 변경
- `refactor`: 코드 리팩토링
- `test`: 테스트 추가/수정
- `style`: 코드 포맷팅

### 예시

```bash
feat(auth): add OAuth2 login support
fix(video): resolve WebRTC connection timeout
chore: upgrade Spring Boot to 3.3.5
docs: update API documentation
refactor(chat): improve message handling logic
test(ai): add unit tests for summarization
```

## 버전 히스토리 예시

```
v0.1.0 (2026-03-01) - Initial MVP
  - 기본 인증 기능
  - 화상회의 기능
  - 채팅 기능

v0.2.0 (2026-03-15) - AI 기능 추가
  - AI 회의록 요약
  - STT 기능

v0.2.1 (2026-03-18) - 버그 수정
  - 인증 만료 시간 수정
  - 채팅 메시지 손실 해결

v0.3.0 (2026-04-01) - 파일 관리 개선
  - 대용량 파일 지원
  - MinIO 통합

v1.0.0 (2026-06-01) - 정식 출시
  - 모든 기능 안정화
  - 프로덕션 준비 완료
```

## FAQ

### Q: 언제 마이너 버전을 올리나요?
**A:** 새로운 기능을 추가하거나 API를 변경할 때 (0.1.0 → 0.2.0)

### Q: 언제 패치 버전을 올리나요?
**A:** 버그 수정이나 작은 개선을 할 때 (0.2.0 → 0.2.1)

### Q: 언제 메이저 버전을 올리나요?
**A:** 정식 출시할 때만 (0.x.x → 1.0.0). 그 전까지는 0.x.x 유지

### Q: release 브랜치는 언제 삭제하나요?
**A:** main과 develop에 병합 완료 후 삭제 가능 (선택사항)

### Q: 여러 릴리즈를 동시에 진행할 수 있나요?
**A:** 가능하지만 권장하지 않음. 한 번에 하나의 릴리즈만 진행

---

**마지막 업데이트**: 2026-03-01
