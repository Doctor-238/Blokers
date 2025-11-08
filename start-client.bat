@echo off
REM 블로커즈 클라이언트 시작 스크립트 (Windows)

echo ================================================
echo   블로커즈 (Blokers) 게임 클라이언트
echo ================================================
echo.
echo 클라이언트를 시작합니다...
echo 서버 주소: localhost:12345
echo.

cd /d "%~dp0"

REM JAR 파일이 없으면 빌드
if not exist "build\libs\Blokers-1.0-SNAPSHOT.jar" (
    echo 프로젝트를 빌드합니다...
    call gradlew.bat build --no-daemon
    echo.
)

java -cp build\libs\Blokers-1.0-SNAPSHOT.jar game.BlokusClient
