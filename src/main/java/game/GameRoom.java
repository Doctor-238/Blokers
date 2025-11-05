package game;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 개별 게임방을 관리하는 클래스.
 */
public class GameRoom {
    private int roomId;
    private String roomName;
    private ClientHandler host;
    private BlokusServer server;

    private List<ClientHandler> players = Collections.synchronizedList(new ArrayList<>());

    private boolean gameStarted = false;
    private int[][] board = new int[20][20];

    private Map<ClientHandler, List<BlokusPiece>> playerHands = Collections.synchronizedMap(new HashMap<>());
    private Map<ClientHandler, int[]> playerColors = Collections.synchronizedMap(new HashMap<>());
    private Map<Integer, Boolean> isFirstMoveForColor = Collections.synchronizedMap(new HashMap<>());

    private int playerCountOnStart = 0;
    private int currentPlayerTurnIndex = 0;
    private int currentTurnColor;
    private int passCount = 0;

    // 타이머 관련 변수
    private static final int INITIAL_TIME_SECONDS = 300; // 5분
    private static final int TIME_BONUS_SECONDS = 10; // 턴 시작 시 10초 추가
    private Timer gameTimer;
    private TimerTask currentTimerTask;
    private Map<Integer, Integer> remainingTime = Collections.synchronizedMap(new HashMap<>());
    private Map<Integer, Boolean> isTimedOut = Collections.synchronizedMap(new HashMap<>());

    public GameRoom(int roomId, String roomName, ClientHandler host, BlokusServer server) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.host = host;
        this.server = server;
    }

    public synchronized boolean isPlayerInRoom(String username) {
        for (ClientHandler player : players) {
            if (player.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void addPlayer(ClientHandler player) {
        if (!players.contains(player) && players.size() < 4 && !gameStarted) {
            players.add(player);
            player.setCurrentRoom(this);
            broadcastRoomUpdate();
        }
    }

    public synchronized boolean removePlayer(ClientHandler player) {
        boolean wasHost = player.equals(host);
        players.remove(player);
        player.setCurrentRoom(null);

        if (gameStarted) {
            broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + player.getUsername() + "님이 게임 중 나갔습니다. (패배 처리)");
            if (players.size() < 2) {
                handleGameOver(true); // 강제 종료
            }
        }

        if (players.isEmpty()) {
            return true; // 방 제거 신호
        }

        if (wasHost && !players.isEmpty()) {
            host = players.get(0);
            broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + host.getUsername() + "님이 새 방장이 되었습니다.");
        }

        if (!gameStarted) {
            broadcastRoomUpdate();
        }
        return false;
    }

    public synchronized void startGame(ClientHandler starter) {
        if (!starter.equals(host)) {
            starter.sendMessage(Protocol.S2C_SYSTEM_MSG + ":방장만 게임을 시작할 수 있습니다.");
            return;
        }
        if (gameStarted) {
            starter.sendMessage(Protocol.S2C_SYSTEM_MSG + ":이미 게임이 시작되었습니다.");
            return;
        }
        if (players.size() != 2 && players.size() != 4) {
            starter.sendMessage(Protocol.S2C_SYSTEM_MSG + ":2명 또는 4명일 때만 시작할 수 있습니다.");
            return;
        }

        gameStarted = true;
        playerCountOnStart = players.size();
        this.board = new int[20][20];
        this.passCount = 0;

        for (int i=1; i<=4; i++) {
            remainingTime.put(i, INITIAL_TIME_SECONDS);
            isTimedOut.put(i, false);
        }
        this.gameTimer = new Timer();

        initializePlayerHandsAndColors();

        for (int i = 0; i < players.size(); i++) {
            ClientHandler player = players.get(i);

            String myColorsStr = "";
            int[] colors = playerColors.get(player);
            for(int c : colors) { myColorsStr += c + ","; }
            myColorsStr = myColorsStr.substring(0, myColorsStr.length() - 1);

            player.sendMessage(Protocol.S2C_GAME_START + ":" + playerCountOnStart + ":" + myColorsStr);
            sendHandUpdate(player);
        }

        currentPlayerTurnIndex = -1;
        advanceTurn();
    }

    public synchronized void kickPlayer(ClientHandler kicker, String targetUsername) {
        if (!kicker.equals(host)) {
            kicker.sendMessage(Protocol.S2C_SYSTEM_MSG + ":방장만 강퇴할 수 있습니다.");
            return;
        }
        if (kicker.getUsername().equals(targetUsername)) {
            kicker.sendMessage(Protocol.S2C_SYSTEM_MSG + ":자기 자신을 강퇴할 수 없습니다.");
            return;
        }
        if (gameStarted) {
            kicker.sendMessage(Protocol.S2C_SYSTEM_MSG + ":게임 시작 후에는 강퇴할 수 없습니다.");
            return;
        }

        ClientHandler target = null;
        for (ClientHandler p : players) {
            if (p.getUsername().equals(targetUsername)) {
                target = p;
                break;
            }
        }

        if (target != null) {
            target.sendMessage(Protocol.S2C_KICKED);
            removePlayer(target);
            server.addClientToLobby(target);
            broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + targetUsername + "님이 강퇴당했습니다.");
        } else {
            kicker.sendMessage(Protocol.S2C_SYSTEM_MSG + ":해당 유저를 찾을 수 없습니다.");
        }
    }

    public synchronized void handlePlaceBlock(ClientHandler player, String data) {
        if (isTimedOut.get(currentTurnColor)) {
            player.sendMessage(Protocol.S2C_INVALID_MOVE + ":시간이 초과되어 이 색상으로는 놓을 수 없습니다.");
            return;
        }

        if (playerCountOnStart == 4) {
            if (!players.get(currentPlayerTurnIndex).equals(player)) {
                player.sendMessage(Protocol.S2C_INVALID_MOVE + ":당신의 턴이 아닙니다.");
                return;
            }
        } else {
            if (!players.get(currentPlayerTurnIndex % 2).equals(player)) {
                player.sendMessage(Protocol.S2C_INVALID_MOVE + ":당신의 턴이 아닙니다.");
                return;
            }
        }

        String[] parts = data.split(":");
        if (parts.length < 4) {
            player.sendMessage(Protocol.S2C_INVALID_MOVE + ":잘못된 요청입니다.");
            return;
        }

        String pieceId = parts[0];
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        int rotation = Integer.parseInt(parts[3]);

        BlokusPiece pieceToPlace = null;
        List<BlokusPiece> hand = playerHands.get(player);
        for (BlokusPiece piece : hand) {
            if (piece.getId().equals(pieceId) && piece.getColor() == this.currentTurnColor) {
                pieceToPlace = new BlokusPiece(piece);
                break;
            }
        }
        if (pieceToPlace == null) {
            player.sendMessage(Protocol.S2C_INVALID_MOVE + ":해당 조각을(ID:" + pieceId + ", Color:" + currentTurnColor + ") 가지고 있지 않거나 이미 사용했습니다.");
            return;
        }

        for (int i = 0; i < rotation; i++) pieceToPlace.rotate();

        if (!isValidMove(pieceToPlace, x, y, this.currentTurnColor)) {
            player.sendMessage(Protocol.S2C_INVALID_MOVE + ":놓을 수 없는 위치입니다. (규칙 위반)");
            return;
        }

        placePieceOnBoard(pieceToPlace, x, y);
        isFirstMoveForColor.put(this.currentTurnColor, false);

        BlokusPiece originalPiece = null;
        for (BlokusPiece piece : hand) {
            if (piece.getId().equals(pieceId) && piece.getColor() == this.currentTurnColor) {
                originalPiece = piece;
                break;
            }
        }
        hand.remove(originalPiece);
        sendHandUpdate(player);

        this.passCount = 0;
        advanceTurn();
    }

    public synchronized void handlePassTurn(ClientHandler player) {
        if (player != null) {
            if (playerCountOnStart == 4) {
                if (!players.get(currentPlayerTurnIndex).equals(player)) {
                    player.sendMessage(Protocol.S2C_INVALID_MOVE + ":당신의 턴이 아닙니다.");
                    return;
                }
            } else {
                if (!players.get(currentPlayerTurnIndex % 2).equals(player)) {
                    player.sendMessage(Protocol.S2C_INVALID_MOVE + ":당신의 턴이 아닙니다.");
                    return;
                }
            }
            // [수정됨] 시스템 메시지는 advanceTurn에서 턴 알림으로 대체
            // broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + player.getUsername() + "님이 턴을 넘겼습니다. (연속 " + passCount + " 패스)");
        }

        this.passCount++;

        if (checkGameOver()) {
            handleGameOver(false);
        } else {
            advanceTurn();
        }
    }

    // --- 비공개 헬퍼 메소드 ---

    private boolean isValidMove(BlokusPiece piece, int x, int y, int color) {
        if (isTimedOut.get(color)) {
            return false;
        }

        List<Point> pieceCoords = piece.getPoints();
        boolean isFirstMove = isFirstMoveForColor.get(color);
        boolean cornerTouch = false;

        Point startCorner = null;
        if (color == 1) startCorner = new Point(0, 0);       // Red
        else if (color == 2) startCorner = new Point(19, 0); // Blue
        else if (color == 3) startCorner = new Point(19, 19); // Yellow
        else if (color == 4) startCorner = new Point(0, 19); // Green

        boolean startCornerMatch = false;

        for (Point p : pieceCoords) {
            int boardX = x + p.x;
            int boardY = y + p.y;

            if (boardX < 0 || boardX >= 20 || boardY < 0 || boardY >= 20) return false;
            if (board[boardY][boardX] != 0) return false;

            if (isFirstMove) {
                if (startCorner != null && boardX == startCorner.x && boardY == startCorner.y) {
                    startCornerMatch = true;
                }
            } else {
                int[] dx = {0, 0, 1, -1};
                int[] dy = {1, -1, 0, 0};
                for (int i = 0; i < 4; i++) {
                    int checkX = boardX + dx[i];
                    int checkY = boardY + dy[i];
                    if (checkX >= 0 && checkX < 20 && checkY >= 0 && checkY < 20) {
                        if (board[checkY][checkX] == color) return false;
                    }
                }

                int[] ddx = {1, 1, -1, -1};
                int[] ddy = {1, -1, 1, -1};
                for (int i = 0; i < 4; i++) {
                    int checkX = boardX + ddx[i];
                    int checkY = boardY + ddy[i];
                    if (checkX >= 0 && checkX < 20 && checkY >= 0 && checkY < 20) {
                        if (board[checkY][checkX] == color) cornerTouch = true;
                    }
                }
            }
        }
        return isFirstMove ? startCornerMatch : cornerTouch;
    }

    private void placePieceOnBoard(BlokusPiece piece, int x, int y) {
        List<Point> pieceCoords = piece.getPoints();
        int color = piece.getColor();
        for (Point p : pieceCoords) {
            int boardX = x + p.x;
            int boardY = y + p.y;
            if (boardX >= 0 && boardX < 20 && boardY >= 0 && boardY < 20) {
                board[boardY][boardX] = color;
            }
        }
    }

    private void advanceTurn() {
        if (currentTimerTask != null) {
            currentTimerTask.cancel();
        }

        int maxTurns = 4;
        int timedOutCount = 0;
        do {
            currentPlayerTurnIndex = (currentPlayerTurnIndex + 1) % 4;
            currentTurnColor = currentPlayerTurnIndex + 1;
            timedOutCount++;
            if (timedOutCount > 4) {
                handleGameOver(false);
                return;
            }
        } while (isTimedOut.get(currentTurnColor));


        int newTime = remainingTime.get(currentTurnColor) + TIME_BONUS_SECONDS;
        remainingTime.put(currentTurnColor, newTime);

        startTurnTimer();

        // [수정됨] 턴 변경 시스템 메시지 전송
        String colorName = getColorName(currentTurnColor);
        String playerName = getPlayerNameByColor(currentTurnColor);
        broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + playerName + " (" + colorName + ") 턴입니다.");

        broadcastGameState();
        broadcastTimeUpdate();
    }

    private void startTurnTimer() {
        currentTimerTask = new TimerTask() {
            @Override
            public void run() {
                synchronized(GameRoom.this) {
                    if (!gameStarted) {
                        this.cancel();
                        return;
                    }

                    int time = remainingTime.get(currentTurnColor) - 1;
                    remainingTime.put(currentTurnColor, time);

                    if (time <= 0) {
                        isTimedOut.put(currentTurnColor, true);
                        // [수정됨] 시간 초과 시스템 메시지
                        String colorName = getColorName(currentTurnColor);
                        broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + colorName + " 색상 시간 초과! 턴이 강제로 넘어갑니다.");
                        broadcastTimeUpdate();
                        handlePassTurn(null); // 강제 턴 넘김
                        this.cancel();
                    } else {
                        broadcastTimeUpdate();
                    }
                }
            }
        };
        gameTimer.scheduleAtFixedRate(currentTimerTask, 1000, 1000);
    }

    private void broadcastTimeUpdate() {
        String timeData = String.format("%s:%d,%d,%d,%d",
                Protocol.S2C_TIME_UPDATE,
                remainingTime.get(1),
                remainingTime.get(2),
                remainingTime.get(3),
                remainingTime.get(4)
        );
        broadcastMessage(timeData);
    }

    private boolean checkGameOver() {
        int activePlayers = 0;
        for(int i=1; i<=4; i++) {
            if (!isTimedOut.get(i)) activePlayers++;
        }

        if (activePlayers == 0) return true;
        if (passCount >= activePlayers) return true;

        return false;
    }

    private void handleGameOver(boolean forced) {
        if (!gameStarted) return;
        gameStarted = false;

        if (currentTimerTask != null) currentTimerTask.cancel();
        if (gameTimer != null) gameTimer.cancel();

        String resultMessage;

        if (forced) {
            resultMessage = "WINNER:" + players.get(0).getUsername() + " (기권승)";
        } else {
            Map<ClientHandler, Integer> scores = new HashMap<>();

            for (ClientHandler player : playerHands.keySet()) {
                int score = 0;
                List<BlokusPiece> hand = playerHands.get(player);
                if (hand.isEmpty()) {
                    score = -15;
                } else {
                    for (BlokusPiece piece : hand) {
                        if (!isTimedOut.get(piece.getColor())) {
                            score += piece.getSize();
                        }
                    }
                }

                int[] colors = playerColors.get(player);
                for (int c : colors) {
                    if (isTimedOut.get(c)) {
                        // 타임아웃 패널티
                    }
                }

                scores.put(player, score);
            }

            if (playerCountOnStart == 2) {
                ClientHandler p1 = players.get(0);
                ClientHandler p2 = players.get(1);

                int scoreP1 = scores.get(p1);
                int scoreP2 = scores.get(p2);

                if (scoreP1 < scoreP2) resultMessage = "WINNER:" + p1.getUsername() + " (점수: " + scoreP1 + " vs " + scoreP2 + ")";
                else if (scoreP2 < scoreP1) resultMessage = "WINNER:" + p2.getUsername() + " (점수: " + scoreP2 + " vs " + scoreP1 + ")";
                else resultMessage = "DRAW (점수: " + scoreP1 + ")";

            } else { // 4인용
                List<Map.Entry<ClientHandler, Integer>> sortedPlayers =
                        new ArrayList<>(scores.entrySet());

                Collections.sort(sortedPlayers, Comparator.comparingInt(Map.Entry::getValue));

                StringBuilder rankStr = new StringBuilder();
                for (int i = 0; i < sortedPlayers.size(); i++) {
                    ClientHandler p = sortedPlayers.get(i).getKey();
                    int s = sortedPlayers.get(i).getValue();
                    rankStr.append((i+1) + "등: " + p.getUsername() + " (" + s + "점) | ");
                }
                resultMessage = rankStr.toString();
            }
        }

        broadcastMessage(Protocol.S2C_GAME_OVER + ":" + resultMessage);
        server.removeRoom(this.roomId);
    }

    public void broadcastMessage(String message) {
        synchronized (players) {
            for (ClientHandler client : players) {
                client.sendMessage(message);
            }
        }
    }

    private void broadcastRoomUpdate() {
        StringBuilder roomUpdateStr = new StringBuilder(Protocol.S2C_ROOM_UPDATE);
        if (players.size() > 0) roomUpdateStr.append(":");

        synchronized (players) {
            for (ClientHandler p : players) {
                String role = p.equals(host) ? "host" : "guest";
                roomUpdateStr.append("[").append(p.getUsername()).append(",").append(role).append("];");
            }
        }
        if (roomUpdateStr.length() > 0 && roomUpdateStr.charAt(roomUpdateStr.length() - 1) == ';') {
            roomUpdateStr.deleteCharAt(roomUpdateStr.length() - 1);
        }
        broadcastMessage(roomUpdateStr.toString());
    }

    private String getPlayerNameByColor(int color) {
        if (playerCountOnStart == 4) {
            return players.get(color - 1).getUsername();
        } else { // 1v1
            return players.get((color - 1) % 2).getUsername();
        }
    }

    private String getColorName(int color) {
        if (color == 1) return "Red";
        if (color == 2) return "Blue";
        if (color == 3) return "Yellow";
        if (color == 4) return "Green";
        return "Unknown";
    }

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

        broadcastMessage(Protocol.S2C_GAME_STATE + ":" + boardData.toString() + ":" + currentPlayerName + ":" + currentTurnColor);
    }

    private void sendHandUpdate(ClientHandler player) {
        List<BlokusPiece> hand = playerHands.get(player);
        if (hand == null) return;

        StringBuilder handData = new StringBuilder(Protocol.S2C_HAND_UPDATE);
        if (hand.size() > 0) handData.append(":");

        for (BlokusPiece piece : hand) {
            handData.append(piece.getId()).append("/").append(piece.getColor()).append(",");
        }
        if (handData.length() > 0 && handData.charAt(handData.length() - 1) == ',') {
            handData.deleteCharAt(handData.length() - 1);
        }
        player.sendMessage(handData.toString());
    }

    private void initializePlayerHandsAndColors() {
        playerHands.clear();
        playerColors.clear();
        isFirstMoveForColor.clear();

        for (int i=1; i<=4; i++) isFirstMoveForColor.put(i, true);

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

    public int getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public int getPlayerCount() { return players.size(); }
    public boolean isGameStarted() { return gameStarted; }
    public List<ClientHandler> getPlayers() { return players; }
}