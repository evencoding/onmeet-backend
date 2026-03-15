@echo off
REM =============================================================================
REM onmeet.bat - Onmeet Backend 로컬 실행 & 배포 통합 스크립트 (Windows)
REM Usage: scripts\onmeet.bat <command> [options]
REM =============================================================================
setlocal enabledelayedexpansion

set "PROJECT_ROOT=%~dp0.."
pushd "%PROJECT_ROOT%"

if "%~1"=="" goto :help
goto :%~1 2>nul || goto :unknown

REM =============================================================================
:infra
echo [onmeet] 인프라 서비스 시작 (DB, Redis, Kafka, MinIO, LiveKit)
call :check_docker
call :ensure_env
call :ensure_network

docker compose up -d ^
    mysql-auth redis-auth ^
    mysql-ai redis ^
    mysql-video ^
    mysql-notification ^
    postgres-file minio minio-init ^
    kafka zookeeper ^
    livekit

echo [onmeet] 인프라 시작 완료. 헬스체크 대기 중...
timeout /t 10 /nobreak >nul
echo [onmeet] IDE에서 서비스를 실행하세요.
goto :end

REM =============================================================================
:up
set "TARGET=%~2"
if "%TARGET%"=="" set "TARGET=all"
echo [onmeet] 서비스 시작: %TARGET%
call :check_docker
call :ensure_env
call :ensure_network

if "%TARGET%"=="all" (
    call :build all
    docker compose up -d --remove-orphans
) else (
    docker compose up -d %TARGET%-service
)

echo [onmeet] 서비스 시작 완료.
docker compose ps
goto :end

REM =============================================================================
:down
echo [onmeet] 모든 서비스 중지
docker compose down
echo [onmeet] 완료.
goto :end

REM =============================================================================
:build
set "TARGET=%~2"
if "%TARGET%"=="" set "TARGET=all"
echo [onmeet] Docker 이미지 빌드: %TARGET%
call :ensure_env
call :ensure_network

if "%TARGET%"=="all" (
    echo [onmeet] Kotlin/Java 서비스 빌드...
    call gradlew.bat jibDockerBuild --parallel -x test
    echo [onmeet] Go 서비스 빌드...
    if exist file-service\Dockerfile (
        docker build -t onmeet/file-service:latest file-service\
    )
) else if "%TARGET%"=="file" (
    docker build -t onmeet/file-service:latest file-service\
) else (
    call gradlew.bat :%TARGET%-service:jibDockerBuild -x test
)

echo [onmeet] 빌드 완료.
goto :end

REM =============================================================================
:logs
set "TARGET=%~2"
if "%TARGET%"=="" (
    docker compose logs -f --tail=100
) else (
    docker compose logs -f --tail=100 %TARGET%-service
)
goto :end

REM =============================================================================
:ps
docker compose ps
goto :end

REM =============================================================================
:restart
set "TARGET=%~2"
if "%TARGET%"=="" (
    echo [onmeet] 서비스 이름을 지정하세요. 예: onmeet restart auth
    goto :end
)
echo [onmeet] %TARGET%-service 재시작 중...
docker compose restart %TARGET%-service
echo [onmeet] 재시작 완료.
goto :end

REM =============================================================================
:test
set "TARGET=%~2"
if "%TARGET%"=="" set "TARGET=all"
echo [onmeet] 테스트 실행: %TARGET%

if "%TARGET%"=="all" (
    call gradlew.bat test --parallel
    pushd file-service
    go test ./...
    popd
) else if "%TARGET%"=="file" (
    pushd file-service
    go test ./...
    popd
) else (
    call gradlew.bat :%TARGET%-service:test
)

echo [onmeet] 테스트 완료.
goto :end

REM =============================================================================
:clean
echo [onmeet] 정리 중...
docker compose down -v --remove-orphans 2>nul
docker image prune -f
call gradlew.bat clean 2>nul
echo [onmeet] 정리 완료.
goto :end

REM =============================================================================
:status
echo [onmeet] 서비스 상태 확인
echo.
docker compose ps
echo.
echo [onmeet] 헬스체크
for %%s in (gateway:8080 auth:8081 ai:8082 video:8083 notification:8085 file:8086 email:8087) do (
    for /f "tokens=1,2 delims=:" %%a in ("%%s") do (
        curl -sf http://localhost:%%b/actuator/health >nul 2>&1 && (
            echo   %%a-service          UP
        ) || (
            curl -sf http://localhost:%%b/%%a/actuator/health >nul 2>&1 && (
                echo   %%a-service          UP
            ) || (
                echo   %%a-service          DOWN
            )
        )
    )
)
goto :end

REM =============================================================================
:deploy
set "VERSION=%~2"
if "%VERSION%"=="" (
    echo [onmeet] 버전을 지정하세요. 예: onmeet deploy v0.3.0
    goto :end
)
echo [onmeet] 배포 시작: %VERSION%

git pull origin develop
git checkout -b release/%VERSION% 2>nul || git checkout release/%VERSION%
git push -u origin release/%VERSION%

echo [onmeet] release 브랜치 push 완료. CI/CD 파이프라인이 자동 배포를 시작합니다.
goto :end

REM =============================================================================
:help
echo.
echo   ================================================================
echo              Onmeet Backend CLI (Windows)
echo   ================================================================
echo.
echo   Usage: scripts\onmeet.bat ^<command^> [options]
echo.
echo   로컬 개발:
echo     infra                인프라만 시작 (DB, Redis, Kafka, MinIO)
echo     up [service]         서비스 시작 (기본: all)
echo     down                 모든 서비스 중지
echo     restart ^<service^>    특정 서비스 재시작
echo     logs [service]       로그 확인
echo.
echo   빌드 ^& 테스트:
echo     build [service]      Docker 이미지 빌드 (기본: all)
echo     test [service]       테스트 실행 (기본: all)
echo.
echo   모니터링:
echo     ps                   컨테이너 상태 확인
echo     status               서비스 상태 + 헬스체크
echo.
echo   배포:
echo     deploy ^<version^>     release 브랜치 생성 및 배포 트리거
echo.
echo   정리:
echo     clean                컨테이너, 볼륨, 이미지 정리
echo.
goto :end

REM =============================================================================
:unknown
echo [onmeet] 알 수 없는 명령: %~1
goto :help

REM =============================================================================
:ensure_env
if not exist .env (
    if exist .env.example (
        copy .env.example .env >nul
        echo [onmeet] .env 파일이 없어서 .env.example을 복사했습니다.
        echo [onmeet] .env 파일을 편집하여 시크릿을 설정하세요.
    ) else (
        echo [onmeet] .env 파일이 없습니다.
        exit /b 1
    )
)
exit /b 0

:ensure_network
docker network inspect onmeet-network >nul 2>&1 || (
    echo [onmeet] Docker network 'onmeet-network' 생성 중...
    docker network create onmeet-network
)
exit /b 0

:check_docker
docker info >nul 2>&1 || (
    echo [onmeet] Docker가 실행 중이 아닙니다. Docker Desktop을 시작하세요.
    exit /b 1
)
exit /b 0

:end
popd
endlocal
