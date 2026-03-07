# 릴리즈 체크리스트

## 릴리즈 준비 체크리스트

새 버전을 배포하기 전에 다음 항목을 확인하세요.

### 버전 결정

- [ ] 변경사항 유형 확인
  - 새로운 기능 추가 → 마이너 버전 증가 (0.1.0 → 0.2.0)
  - 버그 수정 → 패치 버전 증가 (0.2.0 → 0.2.1)
- [ ] 다음 버전 번호 결정: `v_____._____._____`

### 코드 준비

- [ ] 모든 기능 개발 완료 및 develop 브랜치에 병합
- [ ] 로컬에서 테스트 통과 확인
  ```bash
  ./gradlew test
  cd file-service && go test ./...
  ```
- [ ] 코드 리뷰 완료
- [ ] CHANGELOG.md 업데이트 (선택)

### 환경 확인

- [ ] GitHub Secrets 7개 모두 등록 확인
- [ ] GCP 서버 정상 작동 확인
- [ ] Docker Hub 계정 정상 확인

---

## 릴리즈 실행 단계

### 1️⃣ 릴리즈 브랜치 생성

```bash
# develop 브랜치 최신화
git checkout develop
git pull origin develop

# 릴리즈 브랜치 생성 (버전 번호 수정)
git checkout -b release/v0.1.0

# 버전 준비 커밋 (선택사항)
git commit --allow-empty -m "chore: prepare release v0.1.0"

# 원격 브랜치에 푸시
git push origin release/v0.1.0
```

### 2️⃣ GitHub Actions 자동 배포 확인

```bash
# GitHub Actions 페이지에서 워크플로우 실행 확인
# https://github.com/your-org/onmeet-backend/actions

# 체크 항목:
# ✅ detect-changes 완료
# ✅ build-and-push 완료
# ✅ deploy 완료
# ✅ health check 성공
```

### 3️⃣ QA 및 테스트

```bash
# GCP 서버에서 배포 상태 확인
ssh user@gcp-ip
cd ~/onmeet-backend
docker compose ps

# 서비스 로그 확인
docker compose logs -f gateway-service
docker compose logs -f auth-service

# 헬스체크
curl http://localhost:8080/actuator/health

# 주요 API 테스트
curl http://localhost:8080/auth/api/v1/health
```

**QA 체크리스트**:
- [ ] 모든 서비스 정상 실행
- [ ] 헬스체크 통과
- [ ] 주요 API 정상 동작
- [ ] 데이터베이스 마이그레이션 성공
- [ ] 인증/인가 정상 작동

### 4️⃣ 버그 수정 (필요 시)

```bash
# 릴리즈 브랜치에서 수정
git checkout release/v0.1.0

# 버그 수정 작업...
vim auth-service/src/...

# 커밋 및 푸시 (자동 재배포됨)
git add .
git commit -m "fix(auth): resolve login timeout issue"
git push origin release/v0.1.0

# GitHub Actions에서 자동 재배포 확인
```

### 5️⃣ 릴리즈 완료 (main 병합)

```bash
# main 브랜치로 병합
git checkout main
git pull origin main
git merge --no-ff release/v0.1.0

# 버전 태그 생성
git tag -a v0.1.0 -m "Release v0.1.0 - Initial MVP"

# main 브랜치 및 태그 푸시
git push origin main
git push origin --tags
```

### 6️⃣ develop 브랜치 업데이트

```bash
# develop 브랜치로 역병합 (수정사항 반영)
git checkout develop
git pull origin develop
git merge --no-ff release/v0.1.0
git push origin develop
```

### 7️⃣ 정리 (선택사항)

```bash
# 릴리즈 브랜치 삭제
git branch -d release/v0.1.0
git push origin --delete release/v0.1.0

# 로컬 브랜치 정리
git fetch --prune
```

---

## 핫픽스 (긴급 수정) 체크리스트

프로덕션에서 긴급 버그 발견 시:

### 1️⃣ 핫픽스 브랜치 생성

```bash
# main 브랜치에서 핫픽스 브랜치 생성
git checkout main
git pull origin main
git checkout -b hotfix/v0.1.1

# 버그 수정...
git add .
git commit -m "fix(auth): critical security patch"
git push origin hotfix/v0.1.1
```

### 2️⃣ 테스트 및 검증

```bash
# 로컬에서 철저히 테스트
./gradlew test
# ...
```

### 3️⃣ main 병합 및 배포

```bash
# main 병합
git checkout main
git merge --no-ff hotfix/v0.1.1
git tag -a v0.1.1 -m "Hotfix v0.1.1 - Security patch"
git push origin main --tags

# release 브랜치 생성하여 자동 배포
git checkout -b release/v0.1.1
git push origin release/v0.1.1
# GitHub Actions에서 자동 배포
```

### 4️⃣ develop 업데이트

```bash
# develop에도 반영
git checkout develop
git merge --no-ff hotfix/v0.1.1
git push origin develop

# 정리
git branch -d hotfix/v0.1.1
git push origin --delete hotfix/v0.1.1
```

---

## 롤백 체크리스트

배포 후 문제 발생 시:

### 방법 1: 이전 버전 릴리즈 브랜치 재배포

```bash
# 이전 버전 릴리즈 브랜치로 체크아웃
git checkout release/v0.0.9

# 강제 푸시 (자동 재배포)
git push origin release/v0.0.9 --force
```

### 방법 2: GCP 서버에서 수동 롤백

```bash
# GCP 서버 접속
ssh user@gcp-ip
cd ~/onmeet-backend

# 이전 버전 이미지로 변경
export DOCKER_REGISTRY=your-username
docker compose pull
docker compose down

# .env 파일에서 버전 지정 또는 수동 pull
docker pull ${DOCKER_REGISTRY}/auth-service:v0.0.9
docker tag ${DOCKER_REGISTRY}/auth-service:v0.0.9 ${DOCKER_REGISTRY}/auth-service:latest

docker compose up -d
```

---

## 릴리즈 후 작업

- [ ] 릴리즈 노트 작성 (GitHub Releases)
- [ ] 팀에 배포 완료 알림
- [ ] 모니터링 대시보드 확인 (24시간)
- [ ] 로그 확인 및 이슈 트래킹
- [ ] 다음 릴리즈 계획 수립

---

## 빠른 참조

### 현재 버전 확인
```bash
git tag -l | sort -V | tail -1
```

### Docker Hub 이미지 확인
```bash
# 웹 브라우저
https://hub.docker.com/r/your-username/auth-service/tags

# CLI
curl -s "https://registry.hub.docker.com/v2/repositories/your-username/auth-service/tags" | jq -r '.results[].name'
```

### 서비스별 버전 확인 (GCP 서버)
```bash
docker compose images
```

---

**참고 문서**:
- [GIT_WORKFLOW.md](./GIT_WORKFLOW.md) - 상세 Git 워크플로우
- [CI_CD_SETUP_GUIDE.md](./CI_CD_SETUP_GUIDE.md) - CI/CD 설정 가이드
- [GITHUB_SECRETS_SETUP.md](./GITHUB_SECRETS_SETUP.md) - GitHub Secrets 설정

**마지막 업데이트**: 2026-03-01
