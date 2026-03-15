#!/usr/bin/env bash
# =============================================================================
# onmeet.sh - Onmeet Backend 로컬 실행 & 배포 통합 스크립트
# Usage: ./scripts/onmeet.sh <command> [options]
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Colors (ANSI - works on Mac/Linux/Windows Terminal/Git Bash)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log()   { echo -e "${GREEN}[onmeet]${NC} $*"; }
warn()  { echo -e "${YELLOW}[onmeet]${NC} $*"; }
error() { echo -e "${RED}[onmeet]${NC} $*" >&2; }
info()  { echo -e "${CYAN}[onmeet]${NC} $*"; }

# =============================================================================
# Environment Setup
# =============================================================================
ensure_env() {
    if [ ! -f .env ]; then
        if [ -f .env.example ]; then
            cp .env.example .env
            warn ".env 파일이 없어서 .env.example을 복사했습니다."
            warn ".env 파일을 편집하여 시크릿을 설정하세요."
        else
            error ".env 파일이 없습니다. .env.example을 참고하여 생성하세요."
            exit 1
        fi
    fi
}

ensure_network() {
    if ! docker network inspect onmeet-network >/dev/null 2>&1; then
        log "Docker network 'onmeet-network' 생성 중..."
        docker network create onmeet-network
    fi
}

check_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        error "Docker가 설치되어 있지 않습니다."
        exit 1
    fi
    if ! docker info >/dev/null 2>&1; then
        error "Docker 데몬이 실행 중이 아닙니다. Docker Desktop을 시작하세요."
        exit 1
    fi
}

# =============================================================================
# Commands
# =============================================================================

cmd_infra() {
    info "인프라 서비스 시작 (DB, Redis, Kafka, MinIO, LiveKit)"
    check_docker
    ensure_env
    ensure_network

    docker compose up -d \
        mysql-auth redis-auth \
        mysql-ai redis \
        mysql-video \
        mysql-notification \
        postgres-file minio minio-init \
        kafka zookeeper \
        livekit

    log "인프라 서비스 시작 완료. 헬스체크 대기 중..."
    sleep 5

    local healthy=0
    local max=60
    local attempt=0
    while [ $healthy -lt 1 ] && [ $attempt -lt $max ]; do
        attempt=$((attempt + 1))
        if docker compose ps --format '{{.Status}}' mysql-auth 2>/dev/null | grep -q "healthy"; then
            healthy=1
        else
            printf "\r  MySQL 헬스체크 대기 중... (%d/%d)" "$attempt" "$max"
            sleep 2
        fi
    done
    echo ""

    if [ $healthy -eq 1 ]; then
        log "인프라 준비 완료. IDE에서 서비스를 실행하세요."
    else
        warn "일부 인프라가 아직 시작 중입니다. 'docker compose ps'로 확인하세요."
    fi
}

cmd_up() {
    local target="${1:-all}"
    info "서비스 시작: $target"
    check_docker
    ensure_env
    ensure_network

    case "$target" in
        all)
            cmd_build
            docker compose up -d --remove-orphans
            ;;
        auth|gateway|ai|video|notification|email|file)
            docker compose up -d "${target}-service"
            ;;
        *)
            error "알 수 없는 서비스: $target"
            error "사용 가능: all, auth, gateway, ai, video, notification, email, file"
            exit 1
            ;;
    esac

    log "서비스 시작 완료."
    docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || docker compose ps
}

cmd_down() {
    info "모든 서비스 중지"
    docker compose down
    log "완료."
}

cmd_build() {
    local target="${1:-all}"
    info "Docker 이미지 빌드: $target"
    ensure_env
    ensure_network

    case "$target" in
        all)
            log "Kotlin/Java 서비스 빌드 (Jib)..."
            ./gradlew jibDockerBuild --parallel -x test
            log "Go 서비스 빌드 (file-service)..."
            if [ -f file-service/Dockerfile ]; then
                docker build -t onmeet/file-service:latest file-service/
            fi
            ;;
        file)
            docker build -t onmeet/file-service:latest file-service/
            ;;
        auth|gateway|ai|video|notification|email)
            ./gradlew ":${target}-service:jibDockerBuild" -x test
            ;;
        *)
            error "알 수 없는 서비스: $target"
            exit 1
            ;;
    esac

    log "빌드 완료."
}

cmd_logs() {
    local target="${1:-}"
    if [ -z "$target" ]; then
        docker compose logs -f --tail=100
    else
        docker compose logs -f --tail=100 "${target}-service" 2>/dev/null \
            || docker compose logs -f --tail=100 "$target"
    fi
}

cmd_ps() {
    docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || docker compose ps
}

cmd_restart() {
    local target="${1:?서비스 이름을 지정하세요 (예: auth, gateway, video)}"
    info "${target}-service 재시작 중..."
    docker compose restart "${target}-service"
    log "재시작 완료."
}

cmd_test() {
    local target="${1:-all}"
    info "테스트 실행: $target"

    case "$target" in
        all)
            ./gradlew test --parallel
            (cd file-service && go test ./...)
            ;;
        file)
            (cd file-service && go test ./...)
            ;;
        auth|gateway|ai|video|notification|email)
            ./gradlew ":${target}-service:test"
            ;;
        *)
            error "알 수 없는 서비스: $target"
            exit 1
            ;;
    esac

    log "테스트 완료."
}

cmd_clean() {
    info "정리 중..."
    docker compose down -v --remove-orphans 2>/dev/null || true
    docker image prune -f
    ./gradlew clean 2>/dev/null || true
    log "정리 완료."
}

cmd_status() {
    info "서비스 상태 확인"
    echo ""
    cmd_ps
    echo ""

    info "헬스체크"
    local services=("gateway:8080" "auth:8081" "ai:8082" "video:8083" "notification:8085" "file:8086" "email:8087")
    for svc in "${services[@]}"; do
        local name="${svc%%:*}"
        local port="${svc##*:}"
        local status
        if curl -sf "http://localhost:${port}/actuator/health" >/dev/null 2>&1; then
            status="${GREEN}UP${NC}"
        elif curl -sf "http://localhost:${port}/${name}/actuator/health" >/dev/null 2>&1; then
            status="${GREEN}UP${NC}"
        else
            status="${RED}DOWN${NC}"
        fi
        printf "  %-20s %b\n" "${name}-service" "$status"
    done
}

cmd_deploy() {
    local version="${1:?버전을 지정하세요 (예: v0.3.0)}"
    info "배포 시작: $version"

    # 현재 브랜치 확인
    local current_branch
    current_branch=$(git branch --show-current)

    if [ "$current_branch" != "develop" ]; then
        warn "현재 브랜치: $current_branch (develop이 아닙니다)"
        read -rp "develop으로 checkout 할까요? (y/N): " yn
        case "$yn" in
            [Yy]*) git checkout develop && git pull origin develop ;;
            *) error "develop 브랜치에서 실행하세요."; exit 1 ;;
        esac
    else
        git pull origin develop
    fi

    # release 브랜치 생성 및 push
    local branch="release/${version}"
    if git rev-parse --verify "$branch" >/dev/null 2>&1; then
        warn "브랜치 '$branch'가 이미 존재합니다."
        read -rp "기존 브랜치를 사용할까요? (y/N): " yn
        case "$yn" in
            [Yy]*) git checkout "$branch" && git merge develop && git push origin "$branch" ;;
            *) error "다른 버전을 지정하세요."; exit 1 ;;
        esac
    else
        git checkout -b "$branch"
        git push -u origin "$branch"
        log "release 브랜치 push 완료. CI/CD 파이프라인이 자동 배포를 시작합니다."
    fi

    info "배포 상태 확인: gh run list --branch $branch"
}

cmd_help() {
    cat <<'HELP'

  ╔══════════════════════════════════════════════════════╗
  ║           Onmeet Backend CLI                        ║
  ╚══════════════════════════════════════════════════════╝

  Usage: ./scripts/onmeet.sh <command> [options]

  로컬 개발:
    infra                인프라만 시작 (DB, Redis, Kafka, MinIO)
                         → IDE에서 서비스를 직접 실행할 때 사용
    up [service]         서비스 시작 (기본: all)
                         예: up, up auth, up video
    down                 모든 서비스 중지
    restart <service>    특정 서비스 재시작
                         예: restart auth
    logs [service]       로그 확인 (기본: 전체)
                         예: logs, logs auth

  빌드 & 테스트:
    build [service]      Docker 이미지 빌드 (기본: all)
                         예: build, build auth, build file
    test [service]       테스트 실행 (기본: all)
                         예: test, test auth, test file

  모니터링:
    ps                   컨테이너 상태 확인
    status               서비스 상태 + 헬스체크

  배포:
    deploy <version>     release 브랜치 생성 및 배포 트리거
                         예: deploy v0.3.0

  정리:
    clean                컨테이너, 볼륨, 이미지 정리

HELP
}

# =============================================================================
# Main
# =============================================================================
main() {
    local cmd="${1:-help}"
    shift || true

    case "$cmd" in
        infra)    cmd_infra "$@" ;;
        up)       cmd_up "$@" ;;
        down)     cmd_down "$@" ;;
        build)    cmd_build "$@" ;;
        logs)     cmd_logs "$@" ;;
        ps)       cmd_ps "$@" ;;
        restart)  cmd_restart "$@" ;;
        test)     cmd_test "$@" ;;
        clean)    cmd_clean "$@" ;;
        status)   cmd_status "$@" ;;
        deploy)   cmd_deploy "$@" ;;
        help|-h|--help) cmd_help ;;
        *)
            error "알 수 없는 명령: $cmd"
            cmd_help
            exit 1
            ;;
    esac
}

main "$@"
