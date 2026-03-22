# Onmeet Backend - GCP Deployment Guide
## GCP e2-standard-2 (8GB RAM) 단일 인스턴스 배포 가이드

본 문서는 GCP e2-standard-2 인스턴스(Ubuntu 22.04 LTS, 8GB RAM)에 Onmeet 백엔드를 OOM 없이 3개월간 안정적으로 운영하기 위한 배포 가이드입니다.

---

## 📋 목차
1. [사전 요구사항](#1-사전-요구사항)
2. [GCP 인스턴스 생성](#2-gcp-인스턴스-생성)
3. [인프라 셋업 (자동화)](#3-인프라-셋업-자동화)
4. [프로젝트 배포](#4-프로젝트-배포)
5. [메모리 최적화 상세](#5-메모리-최적화-상세)
6. [모니터링 및 운영](#6-모니터링-및-운영)
7. [트러블슈팅](#7-트러블슈팅)

---

## 1. 사전 요구사항

### 1.1 GCP 계정 및 프로젝트
- GCP 계정 (Google Cloud Platform)
- 결제 설정이 완료된 GCP 프로젝트

### 1.2 로컬 환경
- Git
- SSH 클라이언트
- 텍스트 에디터 (환경변수 설정용)

### 1.3 필수 정보
- AWS 계정 (S3, CloudFront, SES 사용)
  - AWS_ACCESS_KEY_ID
  - AWS_SECRET_ACCESS_KEY
  - S3_BUCKET_NAME
  - CLOUDFRONT_DOMAIN
- 기타 환경변수 (`.env.example` 참고)

---

## 2. GCP 인스턴스 생성

### 2.1 GCP Console에서 VM 인스턴스 생성
```bash
# 또는 gcloud CLI 사용
gcloud compute instances create onmeet-backend \
  --zone=asia-northeast3-a \
  --machine-type=e2-standard-2 \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=50GB \
  --boot-disk-type=pd-standard \
  --tags=http-server,https-server
```

### 2.2 방화벽 규칙 설정
필요한 포트 개방:
- **8080**: Gateway Service (API Gateway)
- **22**: SSH
- **9092**: Kafka (선택사항, 외부 연결 시)
- **50000-50100 (UDP)**: LiveKit WebRTC 미디어 스트림 (영상/음성)
- **7881 (TCP)**: LiveKit WebRTC TCP Fallback (선택사항)

```bash
# HTTP/HTTPS 트래픽 허용
gcloud compute firewall-rules create allow-gateway \
  --allow tcp:8080 \
  --target-tags http-server \
  --description "Allow gateway access"

# WebRTC UDP 포트 개방 (영상 데이터용)
gcloud compute firewall-rules create allow-webrtc-udp \
  --allow udp:50000-50100 \
  --description "Allow WebRTC UDP media traffic"

# WebRTC TCP 포트 개방 (Fallback용)
gcloud compute firewall-rules create allow-webrtc-tcp \
  --allow tcp:7881 \
  --description "Allow WebRTC TCP fallback"
```

### 2.3 SSH 접속
```bash
# GCP Console SSH 버튼 클릭 또는
gcloud compute ssh onmeet-backend --zone=asia-northeast3-a
```

---

## 3. 인프라 셋업 (자동화)

### 3.1 셋업 스크립트 다운로드 및 실행
VM에 접속한 후 다음 한 줄 명령으로 전체 인프라를 구성합니다:

```bash
# 프로젝트 클론 (또는 스크립트만 다운로드)
git clone <your-repository-url> ~/onmeet-backend
cd ~/onmeet-backend

# 스크립트 실행 권한 부여
chmod +x setup-gcp-infra.sh

# 인프라 셋업 실행 (Docker, Docker Compose, 8GB Swap)
./setup-gcp-infra.sh
```

**스크립트가 수행하는 작업:**
- ✅ 시스템 패키지 업데이트
- ✅ 8GB Swap 메모리 생성 및 영구 적용 (swappiness=10)
- ✅ Docker Engine 설치
- ✅ Docker Compose Plugin 설치
- ✅ 현재 사용자를 docker 그룹에 추가
- ✅ Docker daemon 메모리 최적화 설정
- ✅ 유틸리티 도구 설치 (htop, git, vim 등)

### 3.2 재로그인
Docker 그룹 변경사항 적용을 위해 **반드시 재로그인** 필요:
```bash
# 로그아웃
exit

# 다시 SSH 접속
gcloud compute ssh onmeet-backend --zone=asia-northeast3-a

# Docker 정상 동작 확인
docker run hello-world
```

### 3.3 설치 확인
```bash
# 메모리 상태 확인
free -h

# Docker 버전 확인
docker --version
docker compose version

# Swap 확인
swapon --show
```

**예상 출력:**
```
               total        used        free      shared  buff/cache   available
Mem:           7.8Gi       XXXMi       XXXGi       XXXMi       XXXMi       XXXGi
Swap:          8.0Gi          0B       8.0Gi
```

---

## 4. 프로젝트 배포

### 4.1 환경변수 설정
```bash
cd ~/onmeet-backend

# .env.example 복사
cp .env.example .env

# 환경변수 편집
vim .env  # 또는 nano .env
```

**필수 환경변수:**
```bash
# Database
DB_ROOT_PASSWORD=your_secure_password
DB_USERNAME=onmeet

# Gateway
GATEWAY_SHARED_SECRET=your_gateway_secret

# Auth Service
AUTH_ENCRYPTION_KEY=your_32_character_encryption_key

# AWS (S3, CloudFront, SES)
AWS_ACCESS_KEY_ID=your_aws_access_key
AWS_SECRET_ACCESS_KEY=your_aws_secret_key
AWS_REGION=ap-northeast-2
S3_BUCKET_NAME=your_s3_bucket
CLOUDFRONT_DOMAIN=your_cloudfront_domain

# Email (AWS SES)
SPRING_MAIL_HOST=email-smtp.ap-northeast-2.amazonaws.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_smtp_username
SPRING_MAIL_PASSWORD=your_smtp_password

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
KAFKA_BROKERS=kafka:29092

# LiveKit (WebRTC)
LIVEKIT_EXTERNAL_IP=your_gcp_instance_external_ip
LIVEKIT_API_KEY=devkey
LIVEKIT_API_SECRET=devsecret
```

### 4.2 Docker 이미지 빌드
```bash
# Gradle을 사용한 이미지 빌드 (Jib - Kotlin/Java 서비스)
./gradlew jibDockerBuild --parallel

# Go 서비스 (file-service)는 docker compose가 자동 빌드
```

**빌드 시간:** 약 3-5분 (병렬 빌드 사용)

### 4.3 서비스 시작
```bash
# Docker 네트워크 생성
docker network create onmeet-network

# 모든 서비스 시작 (백그라운드)
docker compose up -d

# 로그 확인
docker compose logs -f

# 특정 서비스 로그만 확인
docker compose logs -f gateway-service auth-service
```

### 4.4 서비스 상태 확인
```bash
# 실행 중인 컨테이너 확인
docker compose ps

# 헬스체크 확인
docker compose ps | grep healthy

# 메모리 사용량 확인
docker stats --no-stream

# API Gateway 접근 테스트
curl http://localhost:8080/actuator/health
```

**예상 응답:**
```json
{"status":"UP"}
```

---

## 5. 메모리 최적화 상세

### 5.1 적용된 메모리 제한

#### 애플리케이션 서비스 (Spring Boot / Go)
| Service | Memory Limit | Reservation |
|---------|--------------|-------------|
| gateway-service | 512M | 256M |
| auth-service | 512M | 256M |
| ai-service | 768M | 256M |
| video-service | 512M | 256M |
| notification-service | 512M | 256M |
| file-service | 512M | 256M |
| email-service | 512M | 256M |
| **소계** | **3840M (~3.8GB)** | **1792M (~1.8GB)** |

#### 인프라 컨테이너 (Database, Messaging)
| Service | Memory Limit | Reservation |
|---------|--------------|-------------|
| mysql-auth | 256M | 128M |
| mysql-ai | 256M | 128M |
| mysql-video | 256M | 128M |
| mysql-notification | 256M | 128M |
| postgres-file | 256M | 128M |
| redis-auth | 128M | 64M |
| kafka | 1024M (1G) | 512M |
| zookeeper | 256M | 128M |
| **소계** | **2688M (~2.6GB)** | **1344M (~1.3GB)** |

#### 총 메모리 할당
- **Total Limits**: ~6.4GB
- **Total Reservations**: ~3.1GB
- **Physical RAM**: 8GB
- **Swap**: 8GB
- **여유 메모리**: ~1.6GB (시스템 프로세스용)

### 5.2 Spring Boot JVM 메모리 자동 조정

Spring Boot는 Docker 컨테이너 메모리 제한을 자동 감지하여 JVM 힙 크기를 조정합니다:

```yaml
# Docker Compose의 memory limit
deploy:
  resources:
    limits:
      memory: 512M
```

**JVM이 자동으로 적용하는 힙 크기:**
- Container Limit 512M → JVM Max Heap: ~256M-384M
- Container Limit 768M → JVM Max Heap: ~384M-576M

**수동 JVM 옵션 (필요 시):**
```yaml
environment:
  - JAVA_OPTS=-Xmx256m -Xms128m -XX:MaxMetaspaceSize=128m
```

### 5.3 Swap 메모리 최적화

**Swappiness 설정: 10**
- `vm.swappiness=10`: Swap 사용을 최소화하고 물리 RAM을 우선 사용
- 기본값 60보다 훨씬 낮아 성능 저하 방지
- OOM이 발생하기 전에만 Swap을 사용하도록 설정

```bash
# 현재 swappiness 확인
cat /proc/sys/vm/swappiness

# 실시간 변경 (재부팅 시 초기화)
sudo sysctl vm.swappiness=10

# 영구 적용 (setup-gcp-infra.sh에서 자동 설정됨)
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
```

### 5.4 모니터링 스택 비활성화

메모리 절약을 위해 **observability** 스택(Prometheus, Grafana 등)을 비활성화했습니다:

```yaml
# docker-compose.yml
include:
  # ...
  # - observability/docker-compose.yml  # 주석 처리됨
```

**절약된 메모리:** 약 1-2GB

필요 시 다음 명령으로 재활성화 가능:
```bash
# docker-compose.yml 편집하여 주석 해제 후
docker compose up -d
```

---

## 6. 모니터링 및 운영

### 6.1 메모리 모니터링

```bash
# 실시간 메모리 모니터링
watch -n 2 free -h

# Docker 컨테이너별 리소스 사용량
docker stats

# 메모리 사용량 상위 컨테이너
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}" | sort -k 2 -h

# 시스템 전체 프로세스 모니터링
htop
```

### 6.2 로그 관리

```bash
# 모든 서비스 로그 (마지막 100줄)
docker compose logs --tail=100

# 실시간 로그 추적
docker compose logs -f

# 특정 서비스 로그
docker compose logs -f gateway-service auth-service

# 로그 디스크 사용량 확인
docker system df
```

**Docker 로그 자동 정리 설정 (setup-gcp-infra.sh에서 이미 설정됨):**
```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
```
- 각 컨테이너당 최대 30MB (10MB × 3개 파일)만 보관

### 6.3 헬스체크

```bash
# Gateway 헬스체크
curl http://localhost:8080/actuator/health

# 각 서비스별 헬스체크
curl http://localhost:8081/auth/actuator/health  # Auth Service
curl http://localhost:8082/ai/actuator/health    # AI Service
curl http://localhost:8083/video/actuator/health # Video Service
# ...
```

### 6.4 정기 백업

**데이터베이스 백업 스크립트:**
```bash
#!/bin/bash
# backup-databases.sh

BACKUP_DIR="/home/$USER/backups"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

# MySQL 백업
docker exec mysql-auth mysqldump -u root -p${DB_ROOT_PASSWORD} auth_db > $BACKUP_DIR/auth_db_$DATE.sql
docker exec mysql-ai mysqldump -u root -p${DB_ROOT_PASSWORD} ai_db > $BACKUP_DIR/ai_db_$DATE.sql
# ... 다른 데이터베이스

# PostgreSQL 백업
docker exec postgres-file pg_dump -U ${DB_USERNAME} file_db > $BACKUP_DIR/file_db_$DATE.sql

# 7일 이상 된 백업 삭제
find $BACKUP_DIR -name "*.sql" -mtime +7 -delete
```

**크론탭 설정 (매일 새벽 2시):**
```bash
crontab -e

# 추가
0 2 * * * /home/$USER/onmeet-backend/backup-databases.sh
```

---

## 7. 트러블슈팅

### 7.1 OOM (Out of Memory) 발생 시

**증상:**
```
Error: cannot allocate memory
Container exited (137)  # SIGKILL due to OOM
```

**해결 방법:**
```bash
# 1. 메모리 사용량이 높은 컨테이너 확인
docker stats --no-stream | sort -k 7 -h

# 2. 불필요한 컨테이너 중지
docker compose stop <service-name>

# 3. 메모리 캐시 정리
sync; echo 3 | sudo tee /proc/sys/vm/drop_caches

# 4. Swap 사용 확인
free -h
swapon --show

# 5. 필요 시 특정 서비스 메모리 제한 조정
# docker-compose.yml 편집 후
docker compose up -d --force-recreate <service-name>
```

### 7.2 컨테이너가 시작되지 않음

```bash
# 컨테이너 상태 확인
docker compose ps

# 로그 확인
docker compose logs <service-name>

# 헬스체크 실패 시 의존성 확인
docker compose ps | grep unhealthy

# 네트워크 확인
docker network ls
docker network inspect onmeet-network

# 재시작
docker compose restart <service-name>
```

### 7.3 데이터베이스 연결 실패

```bash
# MySQL 컨테이너 접속하여 직접 확인
docker exec -it mysql-auth mysql -u root -p${DB_ROOT_PASSWORD}

# PostgreSQL 컨테이너 접속
docker exec -it postgres-file psql -U ${DB_USERNAME} -d file_db

# 네트워크 연결 테스트
docker exec auth-service ping mysql-auth
```

### 7.4 Kafka 연결 문제

```bash
# Kafka 상태 확인
docker compose logs kafka zookeeper

# Kafka 토픽 리스트 확인
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Zookeeper 연결 확인
docker exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092
```

### 7.5 디스크 공간 부족

```bash
# Docker 디스크 사용량 확인
docker system df

# 사용하지 않는 리소스 정리
docker system prune -a --volumes

# 로그 파일 크기 확인
du -sh /var/lib/docker/containers/*

# 오래된 백업 삭제
find /home/$USER/backups -mtime +7 -delete
```

### 7.6 성능 저하

```bash
# CPU 사용률 확인
top
htop

# I/O 대기 확인
iostat -x 1

# 메모리 압박 확인
vmstat 1

# Swap 사용률이 높으면 메모리 부족
free -h

# 네트워크 상태
netstat -tuln
```

---

## 8. 서비스 관리 명령어 요약

### 8.1 시작/중지/재시작
```bash
# 모든 서비스 시작
docker compose up -d

# 특정 서비스만 시작
docker compose up -d gateway-service auth-service

# 모든 서비스 중지
docker compose down

# 재시작 (설정 변경 시)
docker compose restart

# 특정 서비스 재시작
docker compose restart auth-service

# 강제 재생성
docker compose up -d --force-recreate
```

### 8.2 이미지 관리
```bash
# 이미지 빌드
./gradlew jibDockerBuild --parallel

# 이미지 확인
docker images | grep onmeet

# 사용하지 않는 이미지 삭제
docker image prune -a
```

### 8.3 로그
```bash
# 전체 로그
docker compose logs

# 특정 서비스 로그
docker compose logs auth-service

# 실시간 로그
docker compose logs -f

# 마지막 100줄
docker compose logs --tail=100
```

---

## 9. 외부 접근 설정 (선택사항)

### 9.1 도메인 연결

**GCP 외부 IP 확인:**
```bash
gcloud compute instances describe onmeet-backend \
  --zone=asia-northeast3-a \
  --format='get(networkInterfaces[0].accessConfigs[0].natIP)'
```

**DNS A 레코드 추가:**
```
api.yourdomain.com  →  <GCP_EXTERNAL_IP>
```

### 9.2 HTTPS 설정 (Nginx + Let's Encrypt)

```bash
# Nginx 설치
sudo apt install nginx certbot python3-certbot-nginx -y

# Nginx 설정
sudo vim /etc/nginx/sites-available/onmeet

# SSL 인증서 발급
sudo certbot --nginx -d api.yourdomain.com
```

**Nginx 설정 예시:**
```nginx
server {
    listen 80;
    server_name api.yourdomain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 10. 비용 최적화 팁

### 10.1 자동 스냅샷 설정
```bash
# 디스크 스냅샷 스케줄 생성 (주 1회)
gcloud compute resource-policies create snapshot-schedule weekly-backup \
  --region asia-northeast3 \
  --max-retention-days 14 \
  --on-source-disk-delete keep-auto-snapshots \
  --weekly-schedule-from-file weekly-schedule.yaml
```

### 10.2 인스턴스 스케줄링
개발/테스트 환경의 경우 야간에 인스턴스를 중지하여 비용 절감:
```bash
# 인스턴스 중지
gcloud compute instances stop onmeet-backend --zone=asia-northeast3-a

# 인스턴스 시작
gcloud compute instances start onmeet-backend --zone=asia-northeast3-a
```

---

## 11. 참고 자료

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [GCP Compute Engine Pricing](https://cloud.google.com/compute/pricing)
- [Spring Boot Docker Best Practices](https://spring.io/guides/topicals/spring-boot-docker)
- [MySQL Docker Official Image](https://hub.docker.com/_/mysql)
- [PostgreSQL Docker Official Image](https://hub.docker.com/_/postgres)

---

## 12. 긴급 연락처 및 지원

- **GitHub Issues**: `<your-repository-url>/issues`
- **Slack Channel**: `#onmeet-backend-support`
- **On-call Engineer**: +82-10-XXXX-XXXX

---

**마지막 업데이트:** 2026-02-28
**작성자:** Claude Code (DevOps Agent)
**버전:** 1.0.0
