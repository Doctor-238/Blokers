# 블로커즈 (Blokers) - JFrame Socket 기반 게임

블로커스(Blokus) 보드 게임의 멀티플레이어 Java 구현입니다. JFrame GUI와 Socket 기반 네트워크로 구현되었습니다.

## 게임 소개

블로커스는 4명의 플레이어가 각자의 색상(빨강, 파랑, 노랑, 초록)으로 20x20 보드에 조각을 배치하는 전략 보드 게임입니다.

### 주요 기능

- **멀티플레이어 지원**: 2명(1v1) 또는 4명 게임 모드
- **실시간 네트워킹**: Socket 기반 서버-클라이언트 구조
- **JFrame GUI**: 직관적인 그래픽 인터페이스
- **게임 기능**:
  - 로그인 시스템
  - 게임방 생성/참가
  - 실시간 채팅
  - 턴 타이머 (각 색상당 5분, 턴마다 10초 추가)
  - 조각 회전 기능
  - 유효한 배치 검증

## 프로젝트 구조

```
src/main/java/game/
├── BlokusServer.java     # 게임 서버 (포트 12345)
├── BlokusClient.java     # 클라이언트 GUI (로그인, 로비, 방, 게임 화면)
├── GameRoom.java         # 게임방 로직 및 상태 관리
├── ClientHandler.java    # 클라이언트 연결 처리
├── GameScreen.java       # 게임 보드 UI
├── BlokusPiece.java      # 21가지 조각 정의
└── Protocol.java         # 네트워크 프로토콜 정의
```

## 빌드 및 실행

### 필요 사항

- JDK 17 이상
- Gradle (포함된 Gradle Wrapper 사용 가능)

### 빌드

```bash
./gradlew build
```

### 서버 실행

**방법 1: 스크립트 사용 (권장)**
```bash
# Linux/Mac
./start-server.sh

# Windows
start-server.bat
```

**방법 2: Gradle 사용**
```bash
./gradlew runServer --no-daemon
```

**방법 3: JAR 직접 실행**
```bash
java -cp build/libs/Blokers-1.0-SNAPSHOT.jar game.BlokusServer
```

서버는 포트 12345에서 시작됩니다.

### 클라이언트 실행

새 터미널 창에서:

**방법 1: 스크립트 사용 (권장)**
```bash
# Linux/Mac
./start-client.sh

# Windows
start-client.bat
```

**방법 2: Gradle 사용**
```bash
./gradlew runClient --no-daemon
```

**방법 3: JAR 직접 실행**
```bash
java -cp build/libs/Blokers-1.0-SNAPSHOT.jar game.BlokusClient
```

여러 클라이언트를 실행하려면 추가 터미널 창에서 위 명령을 반복합니다.

## 게임 플레이 방법

1. **로그인**: 클라이언트를 시작하고 사용자 이름을 입력합니다
2. **로비**: 
   - 방 만들기: 새 게임방을 생성합니다
   - 방 들어가기: 기존 방에 참가합니다
3. **게임방**: 
   - 방장은 2명 또는 4명이 모이면 게임을 시작할 수 있습니다
   - 채팅으로 대화할 수 있습니다
4. **게임**: 
   - 하단 인벤토리에서 조각을 클릭하여 선택합니다
   - 'R' 키나 '회전' 버튼으로 조각을 회전시킵니다
   - 보드에 클릭하여 조각을 배치합니다
   - 우클릭으로 선택을 취소합니다
   - '턴 넘기기' 버튼으로 턴을 패스합니다

### 게임 규칙

1. **첫 번째 이동**: 각 색상의 첫 조각은 해당 색상의 시작 코너를 포함해야 합니다
   - 빨강: 왼쪽 상단 (0, 0)
   - 파랑: 오른쪽 상단 (19, 0)
   - 노랑: 오른쪽 하단 (19, 19)
   - 초록: 왼쪽 하단 (0, 19)

2. **이후 이동**: 
   - 같은 색상의 조각과 대각선으로 맞닿아야 합니다
   - 같은 색상의 조각과 변(edge)으로 맞닿으면 안 됩니다

3. **승리 조건**: 모든 플레이어가 더 이상 놓을 수 없을 때, 남은 조각이 가장 적은 플레이어가 승리합니다

## 네트워크 프로토콜

### 클라이언트 → 서버 (C2S)
- `LOGIN:<username>` - 서버에 로그인
- `CREATE_ROOM:<roomName>` - 새 게임방 생성
- `JOIN_ROOM:<roomId>` - 게임방 참가
- `LEAVE_ROOM` - 게임방 나가기
- `START_GAME` - 게임 시작 (방장만 가능)
- `PLACE:<pieceId>:<x>:<y>:<rotation>` - 조각 배치
- `PASS_TURN` - 턴 패스
- `CHAT:<message>` - 채팅 메시지

### 서버 → 클라이언트 (S2C)
- `LOGIN_SUCCESS` - 로그인 성공
- `LOGIN_FAIL:<reason>` - 로그인 실패
- `ROOM_LIST:<rooms>` - 게임방 목록
- `GAME_START:<playerCount>:<colors>` - 게임 시작
- `GAME_STATE:<board>:<currentPlayer>:<color>` - 게임 상태 업데이트
- `HAND_UPDATE:<pieces>` - 플레이어 인벤토리 업데이트
- `TIME_UPDATE:<r>,<b>,<y>,<g>` - 타이머 업데이트
- `GAME_OVER:<result>` - 게임 종료

## 기술 스택

- **Language**: Java 17
- **GUI**: Java Swing (JFrame)
- **Networking**: Java Socket API
- **Build Tool**: Gradle 8.10.2
- **Architecture**: Client-Server with multi-threading

## 개발자

이 프로젝트는 블로커스 보드 게임의 디지털 구현입니다.

## 라이선스

이 프로젝트는 교육 목적으로 개발되었습니다.
