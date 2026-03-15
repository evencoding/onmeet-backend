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

REM develop 브랜치로 전환
for /f "tokens=*" %%b in ('git branch --show-current') do set "CURRENT_BRANCH=%%b"
if not "!CURRENT_BRANCH!"=="develop" (
    echo [onmeet] 현재 브랜치: !CURRENT_BRANCH! - develop으로 전환합니다.
    git checkout develop
)
git pull origin develop

if "%VERSION%"=="" goto :deploy_auto
goto :deploy_execute

:deploy_auto
REM --- 자동 버전 감지 ---
git fetch --prune origin >nul 2>&1

REM 최신 release 브랜치에서 버전 추출 (PowerShell로 정렬)
set "LATEST=0.0.0"
for /f "tokens=*" %%v in ('powershell -NoProfile -Command "git branch -r --list 'origin/release/v*' | ForEach-Object { $_.Trim() -replace '.*release/v','' } | Sort-Object { [version]$_ } | Select-Object -Last 1" 2^>nul') do (
    set "LATEST=%%v"
)

REM 커밋 분석으로 bump 타입 결정
set "BUMP=patch"
git log --oneline -20 > "%TEMP%\onmeet_commits.txt" 2>nul
findstr /i /r "^[a-f0-9].*feat" "%TEMP%\onmeet_commits.txt" >nul 2>&1 && set "BUMP=minor"
del "%TEMP%\onmeet_commits.txt" 2>nul

REM 버전 계산
for /f "tokens=1,2,3 delims=." %%a in ("!LATEST!") do (
    set "MAJOR=%%a"
    set "MINOR=%%b"
    set "PATCH=%%c"
)

if "!BUMP!"=="minor" (
    set /a "NEXT_MINOR=!MINOR!+1"
    set "NEXT_PATCH=v!MAJOR!.!NEXT_MINOR!.0"
    set "NEXT_PATCH_VER=v!MAJOR!.!MINOR!.!PATCH!"
    set /a "PATCH_INC=!PATCH!+1"
    set "NEXT_PATCH_VER=v!MAJOR!.!MINOR!.!PATCH_INC!"
    set "NEXT_VER=v!MAJOR!.!NEXT_MINOR!.0"
) else (
    set /a "PATCH_INC=!PATCH!+1"
    set "NEXT_PATCH_VER=v!MAJOR!.!MINOR!.!PATCH_INC!"
    set /a "NEXT_MINOR=!MINOR!+1"
    set "NEXT_VER=!NEXT_PATCH_VER!"
)

echo.
echo [onmeet] 현재 최신 버전: v!LATEST!
echo [onmeet] 커밋 분석 결과: !BUMP! bump 추천
echo.
echo   1^) !NEXT_PATCH_VER!  (patch - 버그 수정, 소규모 변경)
echo   2^) v!MAJOR!.!NEXT_MINOR!.0  (minor - 기능 추가, 리팩토링)
echo   3^) 직접 입력
echo.

if "!BUMP!"=="minor" (
    set "DEFAULT_CHOICE=2"
) else (
    set "DEFAULT_CHOICE=1"
)
set /p "CHOICE=선택 [!DEFAULT_CHOICE!]: "
if "!CHOICE!"=="" set "CHOICE=!DEFAULT_CHOICE!"

if "!CHOICE!"=="1" (
    set "VERSION=!NEXT_PATCH_VER!"
) else if "!CHOICE!"=="2" (
    set "VERSION=v!MAJOR!.!NEXT_MINOR!.0"
) else if "!CHOICE!"=="3" (
    set /p "VERSION=버전 입력 (예: v0.4.0): "
    if "!VERSION!"=="" (
        echo [onmeet] 버전이 입력되지 않았습니다.
        goto :end
    )
) else (
    echo [onmeet] 잘못된 선택입니다.
    goto :end
)

:deploy_execute
REM v 접두사 보정
if not "!VERSION:~0,1!"=="v" set "VERSION=v!VERSION!"

echo [onmeet] 배포 시작: !VERSION!
git checkout -b release/!VERSION! 2>nul || git checkout release/!VERSION!
git push -u origin release/!VERSION!

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
echo     deploy [version]     release 브랜치 생성 및 배포 트리거
echo                          버전 생략 시 자동 감지
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
