package game;

import java.awt.*;
import java.util.*;
import java.util.List;

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

    private static final int INITIAL_TIME_SECONDS = 300;
    private static final int TIME_BONUS_SECONDS = 20;
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

        // --- (3번) 수정 ---
        // 기권/접속종료 시 게임을 강제로 끝내지 않습니다.
        // if (gameStarted) {
        //    if (players.size() < 2) {
        //        handleGameOver(true);
        //    }
        // }
        // ------------------

        if (players.isEmpty()) {
            return true;
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
        board = new int[20][20];
        passCount = 0;

        for (int i = 1; i <= 4; i++) {
            remainingTime.put(i, INITIAL_TIME_SECONDS);
            isTimedOut.put(i, false);
        }
        gameTimer = new Timer();

        initializePlayerHandsAndColors();

        for (ClientHandler p : players) {
            StringBuilder myColorsStr = new StringBuilder();
            int[] colors = playerColors.get(p);
            for (int c : colors) myColorsStr.append(c).append(",");
            if (myColorsStr.length() > 0) myColorsStr.deleteCharAt(myColorsStr.length() - 1);
            p.sendMessage(Protocol.S2C_GAME_START + ":" + playerCountOnStart + ":" + myColorsStr);
            sendHandUpdate(p);
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
        ClientHandler turnPlayer;
        if (playerCountOnStart == 4) {
            // (3번) 기권한 플레이어는 players 리스트에서 제거되므로, 이 로직은 턴 계산에 실패할 수 있음.
            // (3번) 하지만 어차피 isTimedOut 플래그로 턴이 넘어가므로,
            // (3번) turnPlayer가 실제 player와 일치하는지만 확인하면 됨.
            // (3번) -> 이 로직을 쓰지 말고, 색상으로 플레이어를 찾아야 함.

            // (3번) 수정: players 리스트 대신 playerHands 맵에서 현재 턴 색상의 소유자를 찾음
            turnPlayer = getPlayerByColor(currentTurnColor);

        } else { // 1v1
            turnPlayer = getPlayerByColor(currentTurnColor);
        }

        if (turnPlayer == null || !turnPlayer.equals(player)) {
            player.sendMessage(Protocol.S2C_INVALID_MOVE + ":당신의 턴이 아닙니다.");
            return;
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
            if (piece.getId().equals(pieceId) && piece.getColor() == currentTurnColor) {
                pieceToPlace = new BlokusPiece(piece);
                break;
            }
        }
        if (pieceToPlace == null) {
            player.sendMessage(Protocol.S2C_INVALID_MOVE + ":해당 조각이(ID:" + pieceId + ", Color:" + currentTurnColor + ") 없거나 이미 사용했습니다.");
            return;
        }

        for (int i = 0; i < rotation; i++) pieceToPlace.rotate();

        if (!isValidMove(pieceToPlace, x, y, currentTurnColor)) {
            player.sendMessage(Protocol.S2C_INVALID_MOVE + ":놓을 수 없는 위치입니다. (규칙 위반)");
            return;
        }

        placePieceOnBoard(pieceToPlace, x, y);
        isFirstMoveForColor.put(currentTurnColor, false);

        BlokusPiece originalPiece = null;
        for (BlokusPiece piece : hand) {
            if (piece.getId().equals(pieceId) && piece.getColor() == currentTurnColor) {
                originalPiece = piece;
                break;
            }
        }
        hand.remove(originalPiece);
        sendHandUpdate(player);

        passCount = 0;
        advanceTurn();
    }

    // (3번) handlePlaceBlock을 위한 헬퍼 메서드
    private ClientHandler getPlayerByColor(int color) {
        for (ClientHandler player : playerColors.keySet()) {
            for (int c : playerColors.get(player)) {
                if (c == color) {
                    return player;
                }
            }
        }
        return null;
    }


    public synchronized void handlePassTurn(ClientHandler player) {
        if (player != null) {
            // (3번) 수정: 턴 플레이어 확인 로직 변경
            ClientHandler turnPlayer = getPlayerByColor(currentTurnColor);

            if (turnPlayer == null || !turnPlayer.equals(player)) {
                player.sendMessage(Protocol.S2C_INVALID_MOVE + ":당신의 턴이 아닙니다.");
                return;
            }
        }

        passCount++;
        if (checkGameOver()) {
            handleGameOver(false);
        } else {
            advanceTurn();
        }
    }

    // (기권) C2S_RESIGN_COLOR (버튼 기권) 처리
    public synchronized void handleResignColor(ClientHandler player, String data) {
        if (!gameStarted) return;

        try {
            int colorToResign = Integer.parseInt(data);

            // 본인 턴의 색상만 기권 가능
            if (colorToResign != currentTurnColor) {
                player.sendMessage(Protocol.S2C_INVALID_MOVE + ":현재 턴의 색상만 기권할 수 있습니다.");
                return;
            }

            ClientHandler turnPlayer = getPlayerByColor(currentTurnColor);
            if (turnPlayer == null || !turnPlayer.equals(player)) {
                player.sendMessage(Protocol.S2C_INVALID_MOVE + ":당신의 턴이 아닙니다.");
                return;
            }

            if (!isTimedOut.get(colorToResign)) {
                isTimedOut.put(colorToResign, true);
                broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + getColorName(colorToResign) + " 색이 기권했습니다.");
            }

            // 기권도 턴을 넘김 (passCount는 증가시키지 않음)
            handlePassTurn(null);

        } catch (NumberFormatException e) {
            System.err.println("Invalid C2S_RESIGN_COLOR data: " + data);
        }
    }

    // (기권) 이 메서드는 이제 *연결 끊김* (disconnect) 시에만 호출됨
    public synchronized void handleDisconnectOrResign(ClientHandler player, String reason) {
        if (!gameStarted) return;
        int[] colors = playerColors.get(player);
        if (colors != null) {
            for (int c : colors) {
                if (!isTimedOut.get(c)) {
                    isTimedOut.put(c, true);
                    broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + getColorName(c) + " 색이 탈락했습니다 (" + reason + ")");
                }
            }
        }

        // (3번) 수정: 플레이어가 나갔을 때, 턴이 그 플레이어에게 멈추는 것을 방지
        ClientHandler currentTurnPlayer = getPlayerByColor(currentTurnColor);
        if (player.equals(currentTurnPlayer)) {
            broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + player.getUsername() + "님이 턴을 포기했습니다. 턴이 넘어갑니다.");
            handlePassTurn(null); // (3번) 강제로 턴을 넘김
        }

        // (3번) removePlayer(player)는 BlokusServer.onClientDisconnect에서 호출됨
        // (3번) 여기서는 호출하지 않음 (이 메서드는 BlokusServer에서 호출되기 때문)
        // (3번) players.size() < 2 체크는 removePlayer에서 이미 제거됨.
    }

    private boolean isValidMove(BlokusPiece piece, int x, int y, int color) {
        List<Point> pieceCoords = piece.getPoints();
        boolean isFirstMove = isFirstMoveForColor.get(color);
        boolean cornerTouch = false;

        Point startCorner = null;
        if (color == 1) startCorner = new Point(0, 0);
        else if (color == 2) startCorner = new Point(19, 0);
        else if (color == 3) startCorner = new Point(19, 19);
        else if (color == 4) startCorner = new Point(0, 19);

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

        int attempts = 0;
        do {
            currentPlayerTurnIndex = (currentPlayerTurnIndex + 1) % 4;
            currentTurnColor = currentPlayerTurnIndex + 1;
            attempts++;
            if (attempts > 4) { // (3번) 모든 색이 isTimedOut이면 무한 루프 방지
                if (!checkGameOver()) { // (3번) 추가: 혹시 모르니 게임오버 체크
                    handleGameOver(false);
                }
                return;
            }
        } while (isTimedOut.get(currentTurnColor));

        int newTime = remainingTime.get(currentTurnColor) + TIME_BONUS_SECONDS;
        remainingTime.put(currentTurnColor, newTime);

        startTurnTimer();

        String colorName = getColorName(currentTurnColor);
        String playerName = getPlayerNameByColor(currentTurnColor);
        broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":턴 변경 → " + colorName + " (" + playerName + ")");
        broadcastGameState();
        broadcastTimeUpdate();
    }

    private void startTurnTimer() {
        currentTimerTask = new TimerTask() {
            @Override
            public void run() {
                synchronized (GameRoom.this) {
                    if (!gameStarted) {
                        this.cancel();
                        return;
                    }

                    int time = remainingTime.get(currentTurnColor) - 1;
                    remainingTime.put(currentTurnColor, time);

                    if (time <= 0) {
                        // (기권) 타임아웃 시 isTimedOut 설정 및 메시지 브로드캐스트
                        isTimedOut.put(currentTurnColor, true);
                        broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + getColorName(currentTurnColor) + " 님의 시간이 초과되어 턴이 강제로 넘어갑니다.");
                        broadcastTimeUpdate();
                        handlePassTurn(null);
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
        String legacy = String.format("%s:%d,%d,%d,%d",
                Protocol.S2C_TIME_UPDATE,
                remainingTime.get(1),
                remainingTime.get(2),
                remainingTime.get(3),
                remainingTime.get(4));

        broadcastMessage(legacy);
    }

    private boolean checkGameOver() {
        int activeColors = 0;
        for (int i = 1; i <= 4; i++) {
            if (!isTimedOut.get(i)) activeColors++;
        }
        if (activeColors == 0) return true;
        if (passCount >= activeColors) return true;
        return false;
    }

    private boolean isPlayerTimedOut(ClientHandler player) {
        int[] colors = playerColors.get(player);
        if (colors == null) return true;
        // (3번) 수정: isTimedOut.getOrDefault 사용
        // (기권) 1v1 시, 한 색만 타임아웃되어도 타임아웃으로 간주
        for (int c : colors) {
            if (isTimedOut.getOrDefault(c, false)) return true;
        }
        return false;
    }

    private void handleGameOver(boolean forced) {
        if (!gameStarted) return;
        gameStarted = false;

        if (currentTimerTask != null) currentTimerTask.cancel();
        if (gameTimer != null) gameTimer.cancel();

        String resultMessage;
        Map<String, Double> scoreChanges = new HashMap<>();

        if (forced) {
            // (3번) 이 로직은 이제 removePlayer에서 호출되지 않음.
            resultMessage = "WINNER:" + (players.isEmpty() ? "NONE" : players.get(0).getUsername()) + " (강제승)";
        } else {
            Map<ClientHandler, Integer> scores = new HashMap<>();

            // (3번) playerHands 맵에는 기권한 유저가 남아있을 수 있음.
            // (3번) playerColors 맵을 기준으로 점수 계산
            for (ClientHandler player : playerColors.keySet()) {

                // (3번) 이미 나간 유저(isTimedOut)라도 점수 계산은 해야 함
                // (3V) 단, isPlayerTimedOut은 점수 계산 시점에 나갔는지(true) 아닌지(false)만 알려줌
                // (3번) -> isTimedOut(c)를 직접 확인해야 함

                int score = 0;
                List<BlokusPiece> hand = playerHands.get(player);

                if (hand == null) { // (3번) Disconnect 등으로 hand가 없는 경우
                    score = 999; // (3번) 기권자는 최고 벌점
                } else if (hand.isEmpty()) {
                    score = -15; // 보너스
                } else {
                    for (BlokusPiece piece : hand) {
                        // (3번) 기권/타임아웃된 색상의 블록은 점수 계산에 포함
                        // (3번) (기권 시점의 점수가 확정되어야 하므로)
                        // (3번) -> 이 로직은 유지
                        // (기권) 1v1 (2색) 점수 계산 수정
                        if (playerCountOnStart == 2) {
                            // 1v1에서는 isTimedOut된 색상의 조각도 점수에 포함
                            score += piece.getSize();
                        } else {
                            // 4인전에서는 isTimedOut되지 않은 색상만 계산 (기존 로직)
                            if (!isTimedOut.getOrDefault(piece.getColor(), false)) {
                                score += piece.getSize();
                            }
                        }
                    }
                }

                // (3번) 기권/타임아웃으로 isTimedOut 플래그가 하나라도 켜진 유저는
                // (3번) 남은 블록 수와 관계없이 최고 벌점을 부여 (단, 1v1 제외)
                if (playerCountOnStart == 4 && isPlayerTimedOut(player)) {
                    score = 999;
                }

                scores.put(player, score);
            }

            if (playerCountOnStart == 2) {
                // (3번) 1v1은 players 리스트가 아닌, playerColors 맵을 기준으로
                List<ClientHandler> pList = new ArrayList<>(playerColors.keySet());
                ClientHandler p1 = pList.get(0);
                ClientHandler p2 = pList.get(1);

                int scoreP1 = scores.getOrDefault(p1, 999);
                int scoreP2 = scores.getOrDefault(p2, 999);

                ClientHandler winner, loser;
                if (scoreP1 < scoreP2) { winner = p1; loser = p2; }
                else if (scoreP2 < scoreP1) { winner = p2; loser = p1; }
                else { winner = null; loser = null; }

                if (winner != null) {
                    scoreChanges.put(winner.getUsername(), 1.5);
                    scoreChanges.put(loser.getUsername(), -0.5);
                    resultMessage = "WINNER:" + winner.getUsername() + " (점수: " + scoreP1 + " vs " + scoreP2 + ")";
                } else {
                    scoreChanges.put(p1.getUsername(), 0.0);
                    scoreChanges.put(p2.getUsername(), 0.0);
                    resultMessage = "DRAW (점수: " + scoreP1 + ")";
                }

            } else {
                // (3번) 4인 랭킹 점수
                List<Map.Entry<ClientHandler, Integer>> sorted = new ArrayList<>(scores.entrySet());
                sorted.sort(Comparator.comparingInt(Map.Entry::getValue));

                double[] points = {2.0, 1.0, 0.0, -1.0};
                StringBuilder rankStr = new StringBuilder();

                for (int i = 0; i < sorted.size(); i++) {
                    ClientHandler p = sorted.get(i).getKey();
                    int s = sorted.get(i).getValue();
                    double pointChange = (i < points.length) ? points[i] : -1.0;

                    // (3번) 기권자(999점)는 무조건 -1점
                    if (s == 999) pointChange = -1.0;

                    scoreChanges.put(p.getUsername(), pointChange);

                    rankStr.append((i + 1)).append("등: ").append(p.getUsername()).append(" (")
                            .append(s == 999 ? "기권" : s).append("점 / ").append(pointChange > 0 ? "+" : "")
                            .append(pointChange).append("점) | ");
                }
                resultMessage = rankStr.toString();
            }

            if (!scoreChanges.isEmpty()) {
                server.recordGameResult(scoreChanges);
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
        // (3번) 수정: 기권한 유저도 이름을 표시해야 하므로, playerColors 맵에서 찾음
        ClientHandler player = getPlayerByColor(color);
        if (player != null) {
            return player.getUsername();
        }

        // (3번) 1v1 로직 (getPlayerByColor로 대체 가능해 보임)
        // if (playerCountOnStart == 4) {
        //    return players.size() >= color ? players.get(color - 1).getUsername() : "X";
        // } else {
        //    return players.get((color - 1) % 2).getUsername();
        // }

        return "X"; // (3번) 해당 색상의 플레이어를 못 찾음
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

        broadcastMessage(Protocol.S2C_GAME_STATE + ":" + boardData + ":" + currentPlayerName + ":" + currentTurnColor);
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

        for (int i = 1; i <= 4; i++) isFirstMoveForColor.put(i, true);

        if (playerCountOnStart == 2) {
            ClientHandler p1 = players.get(0);
            ClientHandler p2 = players.get(1);

            playerColors.put(p1, new int[]{1, 3});
            playerColors.put(p2, new int[]{2, 4});

            List<BlokusPiece> handP1 = new ArrayList<>();
            List<BlokusPiece> handP2 = new ArrayList<>();

            for (String id : BlokusPiece.ALL_PIECE_IDS) {
                handP1.add(new BlokusPiece(id, 1));
                handP1.add(new BlokusPiece(id, 3));
                handP2.add(new BlokusPiece(id, 2));
                handP2.add(new BlokusPiece(id, 4));
            }
            playerHands.put(p1, handP1);
            playerHands.put(p2, handP2);

        } else {
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