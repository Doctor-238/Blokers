# 블로커즈 (Blokers) 아키텍처 설명

## 시스템 개요

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│  BlokusClient   │◄───────►│  BlokusServer   │◄───────►│  BlokusClient   │
│    (Player 1)   │  Socket │  (Port 12345)   │  Socket │    (Player 2)   │
│   JFrame GUI    │         │   Multi-thread  │         │   JFrame GUI    │
└─────────────────┘         └─────────────────┘         └─────────────────┘
         │                          │                            │
         │                          │                            │
         ▼                          ▼                            ▼
   ClientReceiver              GameRoom(s)                 ClientReceiver
   (수신 스레드)               (게임 로직)                  (수신 스레드)
```

## 클래스 구조

### 서버 측 (Server-Side)

#### BlokusServer.java
- **역할**: 메인 서버 클래스
- **기능**:
  - ServerSocket(12345) 관리
  - 클라이언트 연결 수락
  - 게임방 생성/관리
  - 로비 플레이어 관리
- **주요 메소드**:
  - `startServer()`: 서버 시작
  - `createRoom()`: 게임방 생성
  - `joinRoom()`: 게임방 참가
  - `broadcastRoomListToLobby()`: 로비에 방 목록 전송

#### ClientHandler.java
- **역할**: 각 클라이언트 연결 처리 (Thread)
- **기능**:
  - 클라이언트별 Socket 관리
  - 메시지 수신/송신
  - 프로토콜 파싱 및 처리
- **주요 메소드**:
  - `handleMessage()`: 클라이언트 메시지 처리
  - `handleLogin()`: 로그인 처리
  - `handlePlaceBlock()`: 블록 배치 처리

#### GameRoom.java
- **역할**: 개별 게임방 관리
- **기능**:
  - 게임 상태 관리 (20x20 보드)
  - 플레이어 핸드 관리
  - 턴 관리 및 타이머
  - 게임 규칙 검증
- **주요 메소드**:
  - `startGame()`: 게임 시작
  - `handlePlaceBlock()`: 블록 배치 검증 및 처리
  - `isValidMove()`: 이동 유효성 검사
  - `advanceTurn()`: 턴 진행

### 클라이언트 측 (Client-Side)

#### BlokusClient.java
- **역할**: 메인 클라이언트 JFrame
- **기능**:
  - CardLayout으로 4개 화면 전환
  - 서버 연결 관리
  - 메시지 송수신
- **화면 구성**:
  1. **LoginScreen**: 이름 입력
  2. **LobbyScreen**: 방 목록, 방 생성/참가
  3. **RoomScreen**: 대기실, 채팅, 게임 시작
  4. **GameScreen**: 게임 플레이

#### GameScreen.java
- **역할**: 게임 보드 UI 및 상호작용
- **레이아웃**:
  ```
  ┌────────────────────────────────────────────┐
  │  [정보] 턴, 내 색상, 타이머  │  [조작] 회전, 패스  │
  ├──────┬───────────────────────┬──────────────┤
  │      │                       │              │
  │ 채팅 │   20x20 게임 보드     │              │
  │      │   (500x500 픽셀)      │              │
  │      │                       │              │
  ├──────┴───────────────────────┴──────────────┤
  │          블록 인벤토리 (스크롤)              │
  │       [토글 버튼] (1v1 모드 전용)            │
  └────────────────────────────────────────────┘
  ```
- **기능**:
  - 보드 렌더링
  - 고스트 조각 표시
  - 마우스 상호작용
  - 블록 회전 (R 키)
  - 유효성 로컬 체크

#### BlokusPiece.java
- **역할**: 블로커스 조각 정의
- **특징**:
  - 21가지 고유 조각 (I1~I5, L3~L5, T4, T5, O4, Z4, Z5, F5, N, P, U, V5, W, X, Y)
  - 회전 기능 (90도 시계방향)
  - 좌표 계산 및 변환
- **데이터**:
  - `SHAPE_DATA`: 각 조각의 원본 모양 (2D 배열)
  - `shape`: 현재 회전 상태의 모양
  - `color`: 조각 색상 (1=Red, 2=Blue, 3=Yellow, 4=Green)

### 통신 (Communication)

#### Protocol.java
- **역할**: 네트워크 프로토콜 상수 정의
- **형식**: `COMMAND:DATA`
- **분류**:
  - C2S (Client to Server): LOGIN, CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, START_GAME, PLACE, PASS_TURN, CHAT, etc.
  - S2C (Server to Client): LOGIN_SUCCESS, LOGIN_FAIL, ROOM_LIST, GAME_START, GAME_STATE, HAND_UPDATE, TIME_UPDATE, etc.

## 게임 플로우

### 1. 연결 및 로그인
```
Client → Server: LOGIN:<username>
Server → Client: LOGIN_SUCCESS (또는 LOGIN_FAIL)
Server → Lobby: ROOM_LIST (자동 전송)
```

### 2. 방 생성/참가
```
Client → Server: CREATE_ROOM:<roomName>
Server → Client: JOIN_SUCCESS:<roomId>:<roomName>
Server → All in Room: ROOM_UPDATE:<players>
```

### 3. 게임 시작
```
Client(Host) → Server: START_GAME
Server → All Players: GAME_START:<playerCount>:<colors>
Server → Each Player: HAND_UPDATE:<pieces>
Server → All Players: GAME_STATE:<board>:<currentPlayer>:<color>
```

### 4. 턴 진행
```
[매 턴마다]
Server → All: TIME_UPDATE:<r>,<b>,<y>,<g>

[플레이어 액션]
Client → Server: PLACE:<pieceId>:<x>:<y>:<rotation>
Server → All: GAME_STATE (보드 업데이트)
Server → Player: HAND_UPDATE (핸드 업데이트)

또는

Client → Server: PASS_TURN
Server → All: GAME_STATE (다음 턴)
```

### 5. 게임 종료
```
[게임 종료 조건 만족]
Server → All: GAME_OVER:<result>
Server: removeRoom()
Server → All: (로비로 복귀)
```

## 게임 규칙 구현

### 1. 첫 번째 이동
- `isFirstMoveForColor` Map으로 각 색상의 첫 이동 추적
- 시작 코너 좌표 검증:
  - Red(1): (0, 0)
  - Blue(2): (19, 0)
  - Yellow(3): (19, 19)
  - Green(4): (0, 19)

### 2. 이후 이동
- **대각선 접촉 필수**: 같은 색상과 대각선으로 맞닿아야 함
- **변 접촉 금지**: 같은 색상과 변으로 맞닿으면 안 됨
- 검증 로직: `GameRoom.isValidMove()`

### 3. 타이머 시스템
- 각 색상당 초기 5분 (300초)
- 턴 시작 시 10초 추가
- 시간 초과 시 해당 색상 비활성화
- `Timer` 및 `TimerTask`로 구현

### 4. 승리 조건
- 모든 플레이어가 패스하거나 더 이상 놓을 수 없을 때
- 점수 계산:
  - 남은 조각 크기 합산 (낮을수록 좋음)
  - 모든 조각 사용 시 -15점 보너스

## 멀티스레딩

### 서버
- **메인 스레드**: ServerSocket.accept() 루프
- **ClientHandler 스레드**: 각 클라이언트당 1개
- **Timer 스레드**: 각 게임방당 1개 (턴 타이머)

### 클라이언트
- **메인 스레드**: JFrame 이벤트 디스패치 스레드 (EDT)
- **ClientReceiver 스레드**: 서버 메시지 수신 전용

### 동기화
- `Collections.synchronizedList()`: 플레이어 리스트
- `Collections.synchronizedMap()`: 게임 데이터
- `synchronized` 메소드: 게임방 상태 변경

## 데이터 포맷 예시

### ROOM_LIST
```
ROOM_LIST:[1,MyRoom,2/4];[2,Game2,3/4]
```

### GAME_STATE
```
GAME_STATE:0,0,0,1,0,...:PlayerName (Red):1
         └─ 400개 숫자 (20x20)
```

### HAND_UPDATE
```
HAND_UPDATE:I1/1,I2/1,L3/1,T4/1,...
           └─ pieceId/color 쌍
```

### TIME_UPDATE
```
TIME_UPDATE:285,300,295,310
           └─ Red, Blue, Yellow, Green 초
```

## 확장 가능성

현재 아키텍처는 다음과 같은 확장이 가능합니다:

1. **데이터베이스 연동**: 플레이어 통계, 전적 저장
2. **관전 모드**: 게임 중 관전자 지원
3. **리플레이 시스템**: 게임 기록 저장 및 재생
4. **AI 플레이어**: 봇 추가
5. **음향 효과**: 효과음 및 배경음악
6. **향상된 UI**: 애니메이션, 테마
7. **매치메이킹**: 자동 방 매칭
8. **랭킹 시스템**: ELO 레이팅

## 테스트 시나리오

### 로컬 테스트
1. 서버 실행: `./start-server.sh`
2. 클라이언트 1 실행: `./start-client.sh`
3. 클라이언트 2 실행: `./start-client.sh`
4. 각 클라이언트에서 다른 이름으로 로그인
5. 한 클라이언트에서 방 생성
6. 다른 클라이언트에서 방 참가
7. 방장이 게임 시작
8. 턴제 게임 진행

### 예상되는 테스트 케이스
- ✅ 중복 이름 로그인 방지
- ✅ 방 이름 중복 방지
- ✅ 게임 중 방 나가기 방지
- ✅ 잘못된 블록 배치 방지
- ✅ 턴 외 블록 배치 방지
- ✅ 타이머 동작 확인
- ✅ 채팅 기능
- ✅ 게임 종료 및 점수 계산

## 성능 고려사항

- **메모리**: 각 게임방당 약 2-3MB (보드, 플레이어 핸드 등)
- **네트워크**: 턴당 약 5-10KB 데이터 전송
- **CPU**: 주로 I/O 대기, CPU 사용량 낮음
- **동시 접속**: 현재 구조로 수백 명 동시 접속 가능

## 알려진 제한사항

1. 서버는 단일 인스턴스만 지원 (분산 처리 미지원)
2. 연결 끊김 시 게임 중단 (재접속 미지원)
3. 로컬 네트워크 또는 직접 연결 필요 (NAT 트래버설 미지원)
4. 보안: 평문 통신 (암호화 미지원)
5. 데이터 지속성: 서버 재시작 시 데이터 소실

## 결론

이 프로젝트는 교육 목적의 완전한 멀티플레이어 보드 게임 구현입니다. 
Java Swing과 Socket API를 활용하여 네트워크 게임 프로그래밍의 
핵심 개념(클라이언트-서버 아키텍처, 프로토콜 설계, 멀티스레딩, 
동기화)을 실습할 수 있습니다.
