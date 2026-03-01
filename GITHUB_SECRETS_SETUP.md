# GitHub Secrets 설정 매뉴얼

## 개요
이 문서는 `onmeet-backend` 프로젝트의 GitHub Actions CI/CD 파이프라인 실행에 필요한 GitHub Secrets 설정 방법을 안내합니다.

## GitHub Secrets 설정 위치
1. GitHub 저장소 페이지로 이동
2. **Settings** 탭 클릭
3. 좌측 메뉴에서 **Secrets and variables** → **Actions** 클릭
4. **New repository secret** 버튼 클릭

## 필수 Secret 목록

### 1. Docker Hub 인증 정보

#### `DOCKER_USERNAME`
- **설명**: Docker Hub 사용자명 (또는 조직명)
- **형식**: 문자열
- **예시**: `mycompany` 또는 `john.doe`
- **획득 방법**:
  - Docker Hub 계정이 없다면 https://hub.docker.com 에서 회원가입
  - 로그인 후 우측 상단 프로필에서 사용자명 확인

#### `DOCKER_PASSWORD`
- **설명**: Docker Hub 액세스 토큰 (패스워드 대신 토큰 사용 권장)
- **형식**: 문자열 (토큰)
- **예시**: `dckr_pat_abcdef1234567890abcdef1234567890`
- **획득 방법**:
  1. Docker Hub 로그인 (https://hub.docker.com)
  2. 우측 상단 프로필 클릭 → **Account Settings**
  3. 좌측 메뉴에서 **Security** 클릭
  4. **New Access Token** 클릭
  5. Description 입력 (예: `GitHub Actions CI/CD`)
  6. Access permissions: **Read, Write, Delete** 선택
  7. **Generate** 클릭 후 토큰 복사 (재확인 불가하므로 반드시 저장)

**⚠️ 보안 주의사항**: 실제 패스워드 대신 Access Token 사용을 강력히 권장합니다.

---

### 2. GCP 서버 SSH 연결 정보

#### `GCP_HOST`
- **설명**: GCP 인스턴스의 외부 IP 주소
- **형식**: IP 주소
- **예시**: `34.64.123.456`
- **획득 방법**:
  1. GCP Console (https://console.cloud.google.com) 로그인
  2. **Compute Engine** → **VM instances** 이동
  3. 해당 인스턴스의 **External IP** 열에서 IP 주소 확인

#### `GCP_USERNAME`
- **설명**: GCP 인스턴스 SSH 접속 사용자명
- **형식**: 문자열
- **예시**: `ubuntu`, `admin`, `your_username`
- **기본값**:
  - Ubuntu 이미지: `ubuntu`
  - Debian 이미지: `admin`
  - CentOS/RHEL: `centos` 또는 `root`
- **확인 방법**: GCP Console에서 VM 인스턴스 SSH 접속 시 표시되는 사용자명

#### `GCP_SSH_KEY`
- **설명**: GCP 인스턴스 접속용 SSH Private Key (전체 내용)
- **형식**: 여러 줄의 텍스트 (BEGIN ~ END 포함)
- **예시**:
  ```
  -----BEGIN OPENSSH PRIVATE KEY-----
  b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAABlwAAAAdzc2gtcn
  NhAAAAAwEAAQAAAYEA1234...
  ... (중간 생략) ...
  -----END OPENSSH PRIVATE KEY-----
  ```
- **생성 방법**:

  **방법 1: 기존 키 사용**
  1. 로컬 터미널에서 `cat ~/.ssh/id_rsa` 실행
  2. 출력 내용 전체를 복사 (BEGIN부터 END까지)

  **방법 2: 새 키 생성**
  1. 로컬에서 실행:
     ```bash
     ssh-keygen -t rsa -b 4096 -C "github-actions-deploy" -f ~/.ssh/gcp_deploy_key
     ```
  2. Public Key를 GCP 인스턴스에 등록:
     ```bash
     # Public key 내용 확인
     cat ~/.ssh/gcp_deploy_key.pub

     # GCP 인스턴스에 SSH 접속 후 실행
     echo "public-key-내용" >> ~/.ssh/authorized_keys
     chmod 600 ~/.ssh/authorized_keys
     ```
  3. Private Key를 GitHub Secret에 등록:
     ```bash
     cat ~/.ssh/gcp_deploy_key
     ```

**⚠️ 보안 주의사항**: Private Key는 절대 외부에 노출되어서는 안 됩니다. GitHub Secrets는 암호화되어 저장됩니다.

#### `GCP_SSH_PORT`
- **설명**: GCP 인스턴스 SSH 포트 번호
- **형식**: 숫자
- **기본값**: `22`
- **예시**: `22`, `2222` (커스텀 포트 사용 시)
- **확인 방법**:
  - 기본 SSH 포트 변경하지 않았다면 `22`
  - 커스텀 포트 사용 시 `/etc/ssh/sshd_config` 파일에서 `Port` 설정 확인

#### `GCP_PROJECT_PATH`
- **설명**: GCP 서버 내 프로젝트 배포 경로 (절대 경로)
- **형식**: 절대 경로 문자열
- **예시**: `/home/ubuntu/onmeet-backend`, `/opt/onmeet-backend`
- **설정 방법**:
  1. GCP 인스턴스에 SSH 접속
  2. 프로젝트를 배포할 디렉토리 생성:
     ```bash
     mkdir -p ~/onmeet-backend
     cd ~/onmeet-backend
     pwd  # 절대 경로 확인 후 Secret에 등록
     ```
  3. docker-compose.yml 등 필요한 파일을 해당 경로에 배치

**📌 참고**: 이 경로에는 반드시 `docker-compose.yml` 파일이 존재해야 합니다.

---

## Secrets 설정 체크리스트

설정 완료 여부를 체크하세요:

- [ ] `DOCKER_USERNAME` - Docker Hub 사용자명
- [ ] `DOCKER_PASSWORD` - Docker Hub 액세스 토큰
- [ ] `GCP_HOST` - GCP 인스턴스 IP 주소
- [ ] `GCP_USERNAME` - GCP SSH 사용자명
- [ ] `GCP_SSH_KEY` - SSH Private Key
- [ ] `GCP_SSH_PORT` - SSH 포트 (기본 22)
- [ ] `GCP_PROJECT_PATH` - 프로젝트 배포 경로

---

## 설정 검증 방법

### 1. Secrets 등록 확인
GitHub 저장소 → Settings → Secrets and variables → Actions에서 7개 Secret이 모두 등록되어 있는지 확인

### 2. Docker Hub 연결 테스트
로컬 터미널에서:
```bash
echo "YOUR_DOCKER_PASSWORD" | docker login -u YOUR_DOCKER_USERNAME --password-stdin
```
→ `Login Succeeded` 메시지 확인

### 3. GCP SSH 연결 테스트
로컬 터미널에서:
```bash
ssh -i ~/.ssh/your_private_key -p YOUR_SSH_PORT YOUR_USERNAME@YOUR_GCP_IP
```
→ 정상 접속 확인

### 4. GitHub Actions 테스트
1. `main` 브랜치에 작은 변경사항 push
2. GitHub 저장소 → **Actions** 탭에서 워크플로우 실행 확인
3. 각 Job의 로그를 확인하여 에러 없이 완료되는지 검증

---

## GCP 운영 서버 초기 설정

GitHub Actions로 자동 배포하기 전에 GCP 서버에서 다음 작업을 완료해야 합니다.

### 1. Docker 설치
```bash
# Docker 설치 (Ubuntu 기준)
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin

# Docker 서비스 시작
sudo systemctl start docker
sudo systemctl enable docker

# 현재 사용자를 docker 그룹에 추가 (sudo 없이 docker 명령어 실행)
sudo usermod -aG docker $USER
newgrp docker

# 설치 확인
docker --version
docker compose version
```

### 2. 프로젝트 디렉토리 설정
```bash
# 프로젝트 디렉토리 생성
mkdir -p ~/onmeet-backend
cd ~/onmeet-backend

# 프로젝트 파일 복사 (로컬에서 실행)
# 방법 1: Git clone
git clone https://github.com/your-org/onmeet-backend.git ~/onmeet-backend

# 방법 2: rsync로 필요한 파일만 복사
rsync -av --exclude='.git' --exclude='**/build' --exclude='**/target' \
  /path/to/local/onmeet-backend/ user@gcp-ip:~/onmeet-backend/
```

### 3. 환경변수 파일 설정
```bash
cd ~/onmeet-backend

# 템플릿 복사
cp .env.production.template .env

# .env 파일 편집
nano .env  # 또는 vim .env
```

**필수 설정 항목:**
```bash
# Docker Hub 사용자명 (GitHub Secrets의 DOCKER_USERNAME과 동일)
DOCKER_REGISTRY=your-dockerhub-username

# 데이터베이스 비밀번호
DB_ROOT_PASSWORD=your-secure-password

# 보안 키들 (강력한 랜덤 문자열 사용)
GATEWAY_SHARED_SECRET=$(openssl rand -base64 32)
AUTH_ENCRYPTION_KEY=$(openssl rand -hex 16)

# AWS 설정
AWS_ACCESS_KEY_ID=your-aws-key
AWS_SECRET_ACCESS_KEY=your-aws-secret
S3_BUCKET_NAME=your-bucket-name
CLOUDFRONT_DOMAIN=your-cloudfront-domain.cloudfront.net
```

### 4. Docker 네트워크 생성
```bash
# onmeet-network 생성 (services 간 통신용)
docker network create onmeet-network
```

### 5. 인프라 서비스 먼저 시작
```bash
cd ~/onmeet-backend

# Kafka, Zookeeper 먼저 시작
docker compose -f kafka/docker-compose.yml up -d

# 헬스체크 대기 (약 30초)
sleep 30

# 데이터베이스 및 Redis 시작
docker compose up -d mysql-auth redis-auth postgres-file \
  mysql-ai mysql-video mysql-chat mysql-notification
```

### 6. Docker Hub 로그인
```bash
# GitHub Actions에서 사용하는 것과 동일한 자격증명 사용
echo "your-docker-token" | docker login -u your-dockerhub-username --password-stdin
```

### 7. 초기 이미지 Pull (선택사항)
```bash
# GitHub Actions에서 빌드된 이미지 수동 pull (테스트용)
docker compose pull

# 모든 서비스 시작
docker compose up -d

# 상태 확인
docker compose ps
```

### 8. 방화벽 설정
```bash
# GCP Firewall에서 다음 포트 허용
# - 8080: Gateway (외부 접근용)
# - 22: SSH (GitHub Actions 접근용)

# 내부 포트는 127.0.0.1로 바인딩되어 외부 접근 불가 (보안)
```

### 9. 자동 시작 설정 (선택사항)
```bash
# systemd 서비스 생성으로 서버 재부팅 시 자동 시작
sudo nano /etc/systemd/system/onmeet.service
```

```ini
[Unit]
Description=Onmeet Backend Services
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/home/ubuntu/onmeet-backend
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose down
User=ubuntu

[Install]
WantedBy=multi-user.target
```

```bash
# 서비스 활성화
sudo systemctl daemon-reload
sudo systemctl enable onmeet.service
sudo systemctl start onmeet.service
```

---

## 트러블슈팅

### Docker Hub 로그인 실패
**증상**: `unauthorized: incorrect username or password`
**해결**:
- `DOCKER_PASSWORD`에 실제 패스워드가 아닌 Access Token을 사용했는지 확인
- Docker Hub에서 토큰 재생성 후 Secret 업데이트

### SSH 연결 실패
**증상**: `Permission denied (publickey)`
**해결**:
1. SSH Public Key가 GCP 인스턴스의 `~/.ssh/authorized_keys`에 등록되어 있는지 확인
2. Private Key 복사 시 전체 내용(BEGIN/END 포함)이 복사되었는지 확인
3. GCP 방화벽에서 SSH 포트(기본 22)가 열려 있는지 확인

### 경로 관련 에러
**증상**: `No such file or directory: docker-compose.yml`
**해결**:
- `GCP_PROJECT_PATH`에 설정된 경로가 실제 존재하는지 확인
- 해당 경로에 `docker-compose.yml` 파일이 있는지 확인
- 절대 경로를 사용했는지 확인 (상대 경로 X)

### 이미지 Pull 실패
**증상**: `unauthorized: authentication required`
**해결**:
- GCP 서버에서 Docker Hub 로그인이 정상적으로 되었는지 확인
- 이미지 이름이 `DOCKER_USERNAME/onmeet/service-name` 형식인지 확인

---

## 보안 Best Practices

1. **최소 권한 원칙**: Docker Hub 토큰은 필요한 권한만 부여
2. **정기적 갱신**: SSH Key와 Docker Hub 토큰을 주기적으로 교체
3. **접근 제한**: GCP 방화벽 규칙으로 GitHub Actions IP 대역만 허용 (선택)
4. **감사 로그**: GitHub Actions 실행 로그를 정기적으로 검토
5. **Secret 분리**: Production/Staging 환경별로 별도 Secret 관리

---

## 추가 참고 자료

- [GitHub Actions Secrets 공식 문서](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Docker Hub Access Tokens](https://docs.docker.com/docker-hub/access-tokens/)
- [GCP SSH Key 관리](https://cloud.google.com/compute/docs/connect/add-ssh-keys)
- [appleboy/ssh-action 문서](https://github.com/appleboy/ssh-action)

---

## 문의
설정 중 문제가 발생하면 DevOps 팀에 문의하세요.

**마지막 업데이트**: 2026-02-28
