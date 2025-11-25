package game;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 개별 게임방을 관리하는 클래스.
 */
public class GameRoom {
    // 방 정보 변수
    private int roomId;
    private String roomName;
    private ClientHandler host;
    private BlokusServer server;

    // 플레이어 관리 변수
    private List<ClientHandler> players = new ArrayList<>();

    // 게임 상태 플래그 변수
    private boolean gameStarted = false;
    // 게임 판 상태 확인 변수 숫자로 색상 확인
    private int[][] board = new int[20][20];

    // 플레이어가 가지고 있는 블록 리스트
    private Map<ClientHandler, List<BlokusPiece>> playerHands = new HashMap<>();
    // 플레이어가 담당하는 색상 복수가 될 수 있으니 int 배열
    private Map<ClientHandler, int[]> playerColors = new HashMap<>();
    // 시작 시 모든 색상에 대해 첫수를 뒀는지 판단하는 변수 Integer = 컬러, Boolean = 색상
    private Map<Integer, Boolean> isFirstMoveForColor = new HashMap<>();

    //게임 시작 시의 플레이어 수
    private int playerCountOnStart = 0;
    // 현재 턴에 해당하는 색상 인덱스 변수
    private int currentPlayerTurnIndex = 0;
    // 실제 색 번호 변수
    private int currentTurnColor;
    // 패스 횟수, 게임 종료 판단에 사용
    private int passCount = 0;

    // 타이머 관련 변수
    private static final int INITIAL_TIME_SECONDS = 300; // 5분
    private static final int TIME_BONUS_SECONDS = 10; // 턴 시작 시 10초 추가

    // 1초마다 시간 update 하는 변수
    private Timer gameTimer;
    private TimerTask currentTimerTask;

    // 남은 시간 변수
    private Map<Integer, Integer> remainingTime = new HashMap<>();
    // 시간 초과된 색상 저장 변수
    private Map<Integer, Boolean> isTimedOut = new HashMap<>();

    public GameRoom(int roomId, String roomName, ClientHandler host, BlokusServer server) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.host = host;
        this.server = server;
    }

    // 방 안 유저 확인 로직
    public synchronized boolean isPlayerInRoom(String username) {
        // 방 안 플레이어에 한해서
        for (ClientHandler player : players) {
            // 같은 이름을 가진 유저가 있다면
            if (player.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    // 플레이어 추가 로직
    public synchronized void addPlayer(ClientHandler player) {
        //플레이어가 4명보다 적으며 게임 시작을 안했을 시
        if (!players.contains(player) && players.size() < 4 && !gameStarted) {
            players.add(player);
            player.setCurrentRoom(this);
            broadcastRoomUpdate();
        }
    }

    // 플레이어 제거 로직
    public synchronized boolean removePlayer(ClientHandler player) {

        boolean wasHost = player.equals(host);
        players.remove(player);
        player.setCurrentRoom(null);

        // 게임 중일 시
        if (gameStarted) {
            broadcastMessage("SYSTEM_MSG" + ":" + player.getUsername() + "님이 게임 중 나갔습니다. (패배 처리)");
            // 만약 플레이어가 2보다 줄어든다면
            if (players.size() < 2) {
                // 강제 종료
                handleGameOver(true);
            }
        }

        // 플레이어가 모두 비었다면
        if (players.isEmpty()) {
            return true; // 방 제거 신호
        }

        // 플레이어가 호스트이면서 모든 플레이어가 나간것이 아니라면
        if (wasHost && !players.isEmpty()) {
            //첫번째 플레이어를 호스트로
            host = players.get(0);
            broadcastMessage("SYSTEM_MSG" + ":" + host.getUsername() + "님이 새 방장이 되었습니다.");
        }

        // 게임 시작 전
        if (!gameStarted) {
            broadcastRoomUpdate();
        }
        return false;
    }

    // 게임 시작 로직
    public synchronized void startGame(ClientHandler starter) {
        // 호스트가 아니라면
        if (!starter.equals(host)) {
            starter.sendMessage("SYSTEM_MSG" + ":방장만 게임을 시작할 수 있습니다.");
            return;
        }
        // 게임이 시작했다면
        if (gameStarted) {
            starter.sendMessage("SYSTEM_MSG" + ":이미 게임이 시작되었습니다.");
            return;
        }
        // 플레이어가 2명이나 4명이 아니라면
        if (players.size() != 2 && players.size() != 4) {
            starter.sendMessage("SYSTEM_MSG" + ":2명 또는 4명일 때만 시작할 수 있습니다.");
            return;
        }

        // 변수 초기화 : 게임 중, 초기 플레이어 명 수, 게임 판, 패스 횟수 0
        gameStarted = true;
        playerCountOnStart = players.size();
        this.board = new int[20][20];
        this.passCount = 0;

        // 4가지 색의 기본 시간 할당 및 시간 초과 초기화
        for (int i = 1; i <= 4; i++) {
            remainingTime.put(i, INITIAL_TIME_SECONDS);
            isTimedOut.put(i, false);
        }

        this.gameTimer = new Timer();

        // 플레이어들의 색상 및 블록 초기화
        // playerColors의 값을 2인용, 4인용에 따라 구별해서 할당
        initializePlayerHandsAndColors();

        // 각 플레이어에게 게임 시작 및 본인 색 정보 전송
        for (int i = 0; i < players.size(); i++) {
            ClientHandler player = players.get(i);

            String myColorsStr = "";
            int[] colors = playerColors.get(player);
            for (int c : colors) {
                myColorsStr += c + ",";
            }
            myColorsStr = myColorsStr.substring(0, myColorsStr.length() - 1);

            player.sendMessage("GAME_START" + ":" + playerCountOnStart + ":" + myColorsStr);
            sendHandUpdate(player);
        }

        currentPlayerTurnIndex = -1;
        advanceTurn();
    }

    // 플레이어 강퇴 로직
    public synchronized void kickPlayer(ClientHandler kicker, String targetUsername) {
        // 방장이 아니라면
        if (!kicker.equals(host)) {
            kicker.sendMessage("SYSTEM_MSG" + ":방장만 강퇴할 수 있습니다.");
            return;
        }
        // 방장 자신이 타겟이라면
        if (kicker.getUsername().equals(targetUsername)) {
            kicker.sendMessage("SYSTEM_MSG" + ":자기 자신을 강퇴할 수 없습니다.");
            return;
        }
        // 게임을 시작했다면
        if (gameStarted) {
            kicker.sendMessage("SYSTEM_MSG" + ":게임 시작 후에는 강퇴할 수 없습니다.");
            return;
        }

        ClientHandler target = null;
        // 현재 방의 플레이어에 한하여
        for (ClientHandler p : players) {
            // 타겟과 이름이 같다면
            if (p.getUsername().equals(targetUsername)) {
                target = p;
                break;
            }
        }

        if (target != null) {
            target.sendMessage("KICKED");
            removePlayer(target);
            server.addClientToLobby(target);
            broadcastMessage("SYSTEM_MSG" + ":" + targetUsername + "님이 강퇴당했습니다.");
        } else {
            kicker.sendMessage("SYSTEM_MSG" + ":해당 유저를 찾을 수 없습니다.");
        }
    }

    // 블록 놓기 로직
    public synchronized void handlePlaceBlock(ClientHandler player, String data) {
        //시간 초과 시
        if (isTimedOut.get(currentTurnColor)) {
            player.sendMessage("INVALID_MOVE" + ":시간이 초과되어 이 색상으로는 놓을 수 없습니다.");
            return;
        }
        // 4인용 2인용에 따라서 그리고 현재 턴의 플레이어만 놓기 가능
        if (playerCountOnStart == 4) {
            if (!players.get(currentPlayerTurnIndex).equals(player)) {
                player.sendMessage("INVALID_MOVE" + ":당신의 턴이 아닙니다.");
                return;
            }
        } else {
            if (!players.get(currentPlayerTurnIndex % 2).equals(player)) {
                player.sendMessage("INVALID_MOVE" + ":당신의 턴이 아닙니다.");
                return;
            }
        }

        //  ex) p5:10:5:3으로 데이터 받을 시
        String[] parts = data.split(":");
        if (parts.length < 4) {
            player.sendMessage("INVALID_MOVE" + ":잘못된 요청입니다.");
            return;
        }

        // p5 = pieceId 어떤 조각
        String pieceId = parts[0];

        // x, y는 보드의 좌표
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);

        // rotation은 회전의 횟수를 의미ㅏㅎㄴ다
        int rotation = Integer.parseInt(parts[3]);

        BlokusPiece pieceToPlace = null;
        // 플레이어의 조각 모음 할당
        List<BlokusPiece> hand = playerHands.get(player);
        for (BlokusPiece piece : hand) {
            //피스 아이디가 같으며 색상이 같을 시
            if (piece.getId().equals(pieceId) && piece.getColor() == this.currentTurnColor) {
                pieceToPlace = new BlokusPiece(piece);
                break;
            }
        }
        // 피스가 비었을 시
        if (pieceToPlace == null) {
            player.sendMessage("INVALID_MOVE" + ":해당 조각을(ID:" + pieceId + ", Color:" + currentTurnColor + ") 가지고 있지 않거나 이미 사용했습니다.");
            return;
        }
        // 지정된 만큼 회전
        for (int i = 0; i < rotation; i++) pieceToPlace.rotate();
        // isValidMove의 유효성 검사로 불가능 할 시에 return
        if (!isValidMove(pieceToPlace, x, y, this.currentTurnColor)) {
            player.sendMessage("INVALID_MOVE" + ":놓을 수 없는 위치입니다. (규칙 위반)");
            return;
        }

        //블록 배치
        placePieceOnBoard(pieceToPlace, x, y);
        // 해당 색은 더이상 첫 수가 아니게 된다.
        isFirstMoveForColor.put(this.currentTurnColor, false);

        // 블록제거 파트
        BlokusPiece originalPiece = null;
        // 현재 플레이어가 가지고 있는 블록에 한해
        for (BlokusPiece piece : hand) {
            // pieceId와 동일한 블록을 찾아
            if (piece.getId().equals(pieceId) && piece.getColor() == this.currentTurnColor) {
                originalPiece = piece;
                break;
            }
        }
        // 블록 목록에서 제거
        hand.remove(originalPiece);
        sendHandUpdate(player);

        this.passCount = 0;
        // 턴 넘김
        advanceTurn();
    }

    // 수동 턴 넘기기 로직
    public synchronized void handlePassTurn(ClientHandler player) {
        //플레이어가 null이 아니며
        if (player != null) {
            //플레이어가 4명일 시
            if (playerCountOnStart == 4) {
                //플레이어의 턴이 아니면
                if (!players.get(currentPlayerTurnIndex).equals(player)) {
                    player.sendMessage("INVALID_MOVE" + ":당신의 턴이 아닙니다.");
                    return;
                }
            // 플레이어가 2명일 시
            } else {
                //플레이어의 턴이 아니면
                if (!players.get(currentPlayerTurnIndex % 2).equals(player)) {
                    player.sendMessage("INVALID_MOVE" + ":당신의 턴이 아닙니다.");
                    return;
                }
            }
        }
        // 패스 카운트를 1 증가
        this.passCount++;

        // 아무도 블록을 둘 수 없는 상황이 오면
        if (checkGameOver()) {
            //게임 종료
            handleGameOver(false);
        } else {
            // 턴 넘김
            advanceTurn();
        }
    }

    // 블록의 유효성 검사 로직
    private boolean isValidMove(BlokusPiece piece, int x, int y, int color) {
        // 색의 시간이 timeout 됬다면
        if (isTimedOut.get(color)) {
            return false;
        }

        //조각의 좌표 변수
        List<Point> pieceCoords = piece.getPoints();
        //현재 컬러가 첫수인지 아닌지 판단하는 변수
        boolean isFirstMove = isFirstMoveForColor.get(color);
        //대각선 접촉이 있는지 없는지 판단하는 변수
        boolean cornerTouch = false;

        // 색상에 따라 지정된 좌표에 시작 점을 설정
        Point startCorner = null;
        if (color == 1) startCorner = new Point(0, 0);       // Red
        else if (color == 2) startCorner = new Point(19, 0); // Blue
        else if (color == 3) startCorner = new Point(19, 19); // Yellow
        else if (color == 4) startCorner = new Point(0, 19); // Green

        //실제 첫수에 시작점이 포함됬는지 판단하는 변수
        boolean startCornerMatch = false;

        for (Point p : pieceCoords) {
            int boardX = x + p.x;
            int boardY = y + p.y;

            // 블록이 보드의 범위 밖이라면
            if (boardX < 0 || boardX >= 20 || boardY < 0 || boardY >= 20) return false;
            // 이미 놓여진 위치에 놓으려고 한다면
            if (board[boardY][boardX] != 0) return false;
            // 현재 블록이 첫수라면
            if (isFirstMove) {
                // 블록의 한 좌표라도 시작점과 닿아있다면
                if (startCorner != null && boardX == startCorner.x && boardY == startCorner.y) {
                    startCornerMatch = true;
                }
            } else {
                // 좌 우
                int[] dx = {0, 0, 1, -1};
                // 상 하
                int[] dy = {1, -1, 0, 0};
                for (int i = 0; i < 4; i++) {
                    int checkX = boardX + dx[i];
                    int checkY = boardY + dy[i];
                    //보드 칸 안에서만
                    if (checkX >= 0 && checkX < 20 && checkY >= 0 && checkY < 20) {
                        //상하좌우로 같은 색상이 있으면 불가능 블로커스 게임 규칙
                        if (board[checkY][checkX] == color) return false;
                    }
                }

                // 대각선 상대 좌표
                int[] ddx = {1, 1, -1, -1};
                int[] ddy = {1, -1, 1, -1};
                for (int i = 0; i < 4; i++) {
                    int checkX = boardX + ddx[i];
                    int checkY = boardY + ddy[i];
                    //보드 칸 안에서만
                    if (checkX >= 0 && checkX < 20 && checkY >= 0 && checkY < 20) {
                        //대각선으로 같은 색상이 있다면
                        if (board[checkY][checkX] == color) cornerTouch = true;
                    }
                }
            }
        }
        //첫 수인데 시작점을 포함하지 않는다면 false, 첫 수가 아닌데 대각선 터치가 없다면 false
        return isFirstMove ? startCornerMatch : cornerTouch;
    }

    // 볻에 블럭놓기 로직
    private void placePieceOnBoard(BlokusPiece piece, int x, int y) {
        List<Point> pieceCoords = piece.getPoints();
        int color = piece.getColor();
        //블록 내부 좌표에 한하여
        for (Point p : pieceCoords) {
            int boardX = x + p.x;
            int boardY = y + p.y;
            //보드의 내부에서만
            if (boardX >= 0 && boardX < 20 && boardY >= 0 && boardY < 20) {
                board[boardY][boardX] = color;
            }
        }
    }

    // 턴 넘기기 로직
    private void advanceTurn() {
        // 이전 전의 타이머가 아직 남아있다면
        if (currentTimerTask != null) {
            // 취소
            currentTimerTask.cancel();
        }


        int timedOutCount = 0;
        do {
            // 턴 인덱스를 돌면서 현재 색상 찾기
            currentPlayerTurnIndex = (currentPlayerTurnIndex + 1) % 4;
            currentTurnColor = currentPlayerTurnIndex + 1;
            timedOutCount++;
            // 4명 다 타임 아웃이 됬다면 게임이 끝났다고 간주
            if (timedOutCount > 4) {
                handleGameOver(false);
                return;
            }
            // 해당 색이 타임아웃이 아니면
        } while (isTimedOut.get(currentTurnColor));

        //이번 턴 색에 10초 보너스 할당
        int newTime = remainingTime.get(currentTurnColor) + TIME_BONUS_SECONDS;
        remainingTime.put(currentTurnColor, newTime);

        //초 단위 타이머 시작
        startTurnTimer();

        // 현재 어떤 색상의 턴인지 안내
        String colorName = getColorName(currentTurnColor);
        String playerName = getPlayerNameByColor(currentTurnColor);
        broadcastMessage("SYSTEM_MSG" + ":" + playerName + " (" + colorName + ") 턴입니다.");

        broadcastGameState();
        broadcastTimeUpdate();
    }

    //
    private void startTurnTimer() {
        // 타이머의 작업을 익명 내부 클래스로 정의
        currentTimerTask = new TimerTask() {
            @Override
            public void run() {
                //락을 걸어 하나의 스레드만 점유할 수 있도록 함
                synchronized (GameRoom.this) {
                    //게임이 시작을 안했다면
                    if (!gameStarted) {
                        this.cancel();
                        return;
                    }
                    //현재 색상의 남은 시간을 가져와 1초 깍고 저장
                    int time = remainingTime.get(currentTurnColor) - 1;
                    remainingTime.put(currentTurnColor, time);

                    //시간이 0이 됬다면 타임아웃 변수에 저장 및 취소
                    if (time <= 0) {
                        isTimedOut.put(currentTurnColor, true);
                        String colorName = getColorName(currentTurnColor);
                        broadcastMessage("SYSTEM_MSG" + ":" + colorName + " 색상 시간 초과! 턴이 강제로 넘어갑니다.");
                        broadcastTimeUpdate();
                        handlePassTurn(null);
                        this.cancel();
                    //시간이 남았다면 업데이트
                    } else {
                        broadcastTimeUpdate();
                    }
                }
            }
        };
        gameTimer.scheduleAtFixedRate(currentTimerTask, 1000, 1000);
    }

    // 색상 별 남은 시간 출력 로직
    private void broadcastTimeUpdate() {
        String timeData = String.format("%s:%d,%d,%d,%d",
                "TIME_UPDATE",
                remainingTime.get(1),
                remainingTime.get(2),
                remainingTime.get(3),
                remainingTime.get(4)
        );
        broadcastMessage(timeData);
    }

    // 게임 종료 판단 로직
    private boolean checkGameOver() {
        int activePlayers = 0;
        for (int i = 1; i <= 4; i++) {
            // 만약 타임아웃이 아니라면
            if (!isTimedOut.get(i)) activePlayers++;
        }

        // 모든 플레이어가 타임아웃이라면
        if (activePlayers == 0) return true;
        // 남은색들이 모두 패스를 했다면
        if (passCount >= activePlayers) return true;

        return false;
    }

    // 게임 졸료 로직
    private void handleGameOver(boolean forced) {
        //만약 게임 아직 시작을 안했다면
        if (!gameStarted) return;
        gameStarted = false;

        // 만약 타이머가 아직 돌아간다면
        if (currentTimerTask != null) currentTimerTask.cancel();
        if (gameTimer != null) gameTimer.cancel();

        String resultMessage;

        //만약 인원종료로 인한 강제종료라면
        if (forced) {
            resultMessage = "WINNER:" + players.get(0).getUsername() + " (기권승)";
        } else {

            Map<ClientHandler, Integer> scores = new HashMap<>();

            // 플레이들에 한해
            for (ClientHandler player : playerHands.keySet()) {
                int score = 0;
                List<BlokusPiece> hand = playerHands.get(player);
                //만약 모든 조각을 다 썼다면 보너스
                if (hand.isEmpty()) {
                    score = -15;
                } else {
                    for (BlokusPiece piece : hand) {
                        //타임 아웃이라면 타임 아웃 되지 않은 색에 대해 합산
                        if (!isTimedOut.get(piece.getColor())) {
                            score += piece.getSize();
                        }
                    }
                }


                int[] colors = playerColors.get(player);
                for (int c : colors) {
                    if (isTimedOut.get(c)) {
                        // 타임아웃 패널티 자리
                    }
                }

                scores.put(player, score);
            }

            //2인일 시
            if (playerCountOnStart == 2) {
                ClientHandler p1 = players.get(0);
                ClientHandler p2 = players.get(1);

                int scoreP1 = scores.get(p1);
                int scoreP2 = scores.get(p2);

                if (scoreP1 < scoreP2)
                    resultMessage = "WINNER:" + p1.getUsername() + " (점수: " + scoreP1 + " vs " + scoreP2 + ")";
                else if (scoreP2 < scoreP1)
                    resultMessage = "WINNER:" + p2.getUsername() + " (점수: " + scoreP2 + " vs " + scoreP1 + ")";
                else
                    resultMessage = "DRAW (점수: " + scoreP1 + ")";

            } else { // 4인용
                // 플레이어와 점수 쌍 저장 변수
                List<Map.Entry<ClientHandler, Integer>> sortedPlayers =
                        new ArrayList<>(scores.entrySet());

                // Comparator.comparingInt(Map.Entry::getValue = 점수를 받아와 오름차순으로 정렬
                Collections.sort(sortedPlayers, Comparator.comparingInt(Map.Entry::getValue));

                // ex) 1등: A (-10점) | 2등: B (3점) | 3등: C (8점) |
                StringBuilder rankStr = new StringBuilder();
                for (int i = 0; i < sortedPlayers.size(); i++) {
                    ClientHandler p = sortedPlayers.get(i).getKey();
                    int s = sortedPlayers.get(i).getValue();
                    rankStr.append((i + 1)).append("등: ")
                            .append(p.getUsername()).append(" (").append(s).append("점) | ");
                }
                resultMessage = rankStr.toString();
            }
        }

        broadcastMessage("GAME_OVER" + ":" + resultMessage);
        //서버에서 방 지우기
        server.removeRoom(this.roomId);
    }

    // 브로드 캐스팅 로직
    public void broadcastMessage(String message) {
        //동시 접근 방지를 위해 락 사용
        synchronized (players) {
            for (ClientHandler client : players) {
                client.sendMessage(message);
            }
        }
    }

    //
    private void broadcastRoomUpdate() {
        StringBuilder roomUpdateStr = new StringBuilder("ROOM_UPDATE");
        //플레이어가 1명이라도 ㅣㅇㅆ을시
        if (players.size() > 0) roomUpdateStr.append(":");

        synchronized (players) {
            for (ClientHandler p : players) {
                String role = p.equals(host) ? "host" : "guest";
                roomUpdateStr.append("[")
                        .append(p.getUsername()).append(",")
                        .append(role).append("];");
            }
        }

        //마지막 부분 파싱
        if (roomUpdateStr.length() > 0 && roomUpdateStr.charAt(roomUpdateStr.length() - 1) == ';') {
            roomUpdateStr.deleteCharAt(roomUpdateStr.length() - 1);
        }
        broadcastMessage(roomUpdateStr.toString());
    }

    // 색상값 이름 반환 로직
    private String getPlayerNameByColor(int color) {
        if (playerCountOnStart == 4) {
            return players.get(color - 1).getUsername();
        } else { // 1v1
            return players.get((color - 1) % 2).getUsername();
        }
    }

    // 색상 이름 반환 로직
    private String getColorName(int color) {
        if (color == 1) return "Red";
        if (color == 2) return "Blue";
        if (color == 3) return "Yellow";
        if (color == 4) return "Green";
        return "Unknown";
    }

    // 플레이어들에게 현재 턴과 보드판 상황 전달 로직
    private void broadcastGameState() {
        StringBuilder boardData = new StringBuilder();
        for (int r = 0; r < 20; r++) {
            for (int c = 0; c < 20; c++) {
                boardData.append(board[r][c]).append(",");
            }
        }
        boardData.deleteCharAt(boardData.length() - 1);

        String currentPlayerName = getPlayerNameByColor(currentTurnColor);
        String colorName = getColorName(currentTurnColor);

        currentPlayerName += " (" + colorName + ")";

        broadcastMessage("GAME_STATE" + ":" + boardData.toString() + ":" + currentPlayerName + ":" + currentTurnColor);
    }

    //플레이어에게 해당하는 블록 목록 전달 로직
    private void sendHandUpdate(ClientHandler player) {
        List<BlokusPiece> hand = playerHands.get(player);
        if (hand == null) return;

        StringBuilder handData = new StringBuilder("HAND_UPDATE");
        if (hand.size() > 0) handData.append(":");

        for (BlokusPiece piece : hand) {
            handData.append(piece.getId()).append("/").append(piece.getColor()).append(",");
        }
        if (handData.length() > 0 && handData.charAt(handData.length() - 1) == ',') {
            handData.deleteCharAt(handData.length() - 1);
        }
        player.sendMessage(handData.toString());
    }

    //플레이어들의 색상 및 블록 목록 초기화 함수
    private void initializePlayerHandsAndColors() {

        // 블록 목록 / 색상 / 첫 턴 반정 초기화
        playerHands.clear();
        playerColors.clear();
        isFirstMoveForColor.clear();

        for (int i = 1; i <= 4; i++) isFirstMoveForColor.put(i, true);

        // 2인용일 시
        if (playerCountOnStart == 2) {
            ClientHandler p1 = players.get(0);
            ClientHandler p2 = players.get(1);

            playerColors.put(p1, new int[]{1, 3});
            playerColors.put(p2, new int[]{2, 4});

            List<BlokusPiece> handP1 = new ArrayList<>();
            List<BlokusPiece> handP2 = new ArrayList<>();

            for (String id : BlokusPiece.ALL_PIECE_IDS) {
                handP1.add(new BlokusPiece(id, 1)); // Red
                handP1.add(new BlokusPiece(id, 3)); // Yellow
                handP2.add(new BlokusPiece(id, 2)); // Blue
                handP2.add(new BlokusPiece(id, 4)); // Green
            }
            playerHands.put(p1, handP1);
            playerHands.put(p2, handP2);

        } else { // 4인용
            for (int i = 0; i < players.size(); i++) {
                ClientHandler player = players.get(i);
                int color = i + 1;

                playerColors.put(player, new int[]{color});
                List<BlokusPiece> hand = new ArrayList<>();
                for (String id : BlokusPiece.ALL_PIECE_IDS) {
                    hand.add(new BlokusPiece(id, color));
                }
                playerHands.put(player, hand);
            }
        }
    }

    //기타 게터 및 bool 메소드
    public int getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public int getPlayerCount() { return players.size(); }
    public boolean isGameStarted() { return gameStarted; }
    public List<ClientHandler> getPlayers() { return players; }
}
