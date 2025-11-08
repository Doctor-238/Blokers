#!/bin/bash
# 블로커즈 서버 시작 스크립트

echo "================================================"
echo "  블로커즈 (Blokers) 게임 서버"
echo "================================================"
echo ""
echo "서버를 시작합니다..."
echo "포트: 12345"
echo ""
echo "종료하려면 Ctrl+C를 누르세요."
echo ""

cd "$(dirname "$0")"

# JAR 파일이 없으면 빌드
if [ ! -f "build/libs/Blokers-1.0-SNAPSHOT.jar" ]; then
    echo "프로젝트를 빌드합니다..."
    ./gradlew build --no-daemon
    echo ""
fi

java -cp build/libs/Blokers-1.0-SNAPSHOT.jar game.BlokusServer
