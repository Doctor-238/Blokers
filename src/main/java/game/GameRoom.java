package game;

import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GameRoom implements Serializable {
    public enum GameMode { CLASSIC, PEERLESS }
    private enum PeerlessPhase { NONE, PREP, COUNTDOWN, MAIN }

    private int roomId;
    private String roomName;
    private ClientHandler host;
    private BlokusServer server;
    private GameMode gameMode;

    private List<ClientHandler> players = Collections.synchronizedList(new ArrayList<>());

    private boolean gameStarted = false;
    private int[][] board = new int[20][20];

    private Map<ClientHandler, List<BlokusPiece>> playerHands = Collections.synchronizedMap(new HashMap<>());
    private Map<ClientHandler, int[]> playerColors = Collections.synchronizedMap(new HashMap<>());
    private Map<Integer, Boolean> isFirstMoveForColor = Collections.synchronizedMap(new HashMap<>());

    private int playerCountOnStart = 0;

    private int currentPlayerTurnIndex = 0;
    private int currentTurnColor;

    // passCount 제거됨

    private static final int CLASSIC_INITIAL_TIME_SECONDS = 300;
    private static final int CLASSIC_TIME_BONUS_SECONDS = 20;
    private static final int PEERLESS_PREP_TIME_SECONDS = 20;
    private static final int PEERLESS_COUNTDOWN_SECONDS = 3;
    private static final int PEERLESS_MAIN_TIME_SECONDS = 300;

    private transient Timer gameTimer;
    private transient TimerTask currentTimerTask;
    private Map<Integer, Integer> remainingTime = Collections.synchronizedMap(new HashMap<>());
    private Map<Integer, Boolean> isTimedOut = Collections.synchronizedMap(new HashMap<>());

    private transient Timer peerlessTimer;
    private transient AtomicInteger peerlessSecondsRemaining = new AtomicInteger(0);
    private transient PeerlessPhase peerlessGamePhase = PeerlessPhase.NONE;


    public GameRoom(int roomId, String roomName, ClientHandler host, BlokusServer server, GameMode gameMode) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.host = host;
        this.server = server;
        this.gameMode = gameMode;
    }

    public boolean isPlayerInRoom(String username) {
        synchronized (players) {
            for (ClientHandler player : players) {
                if (player.getUsername().equalsIgnoreCase(username)) {
                    return true;
                }
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

        if (players.isEmpty()) {
            if (peerlessTimer != null) peerlessTimer.cancel();
            if (gameTimer != null) gameTimer.cancel();
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
        // passCount 초기화 제거

        for (int i = 1; i <= 4; i++) {
            remainingTime.put(i, CLASSIC_INITIAL_TIME_SECONDS);
            isTimedOut.put(i, false);
        }

        initializePlayerHandsAndColors();

        // 플레이어 이름 목록 생성 (순서대로)
        StringBuilder allPlayerNames = new StringBuilder();
        for (ClientHandler p : players) {
            allPlayerNames.append(p.getUsername()).append(",");
        }
        if (allPlayerNames.length() > 0) {
            allPlayerNames.deleteCharAt(allPlayerNames.length() - 1);
        }

        for (ClientHandler p : players) {
            StringBuilder myColorsStr = new StringBuilder();
            int[] colors = playerColors.get(p);
            for (int c : colors) myColorsStr.append(c).append(",");
            if (myColorsStr.length() > 0) myColorsStr.deleteCharAt(myColorsStr.length() - 1);

            String msgBase = (gameMode == GameMode.CLASSIC) ? Protocol.S2C_GAME_START : Protocol.S2C_GAME_START_PEERLESS;
            p.sendMessage(msgBase + ":" + playerCountOnStart + ":" + myColorsStr + ":" + allPlayerNames.toString());

            sendHandUpdate(p);
        }

        if (gameMode == GameMode.CLASSIC) {
            gameTimer = new Timer();
            currentPlayerTurnIndex = -1;
            advanceTurn();
        } else {
            startPeerlessPrepTimer();
        }
    }

    private void startPeerlessPrepTimer() {
        peerlessGamePhase = PeerlessPhase.PREP;
        peerlessSecondsRemaining.set(PEERLESS_PREP_TIME_SECONDS);
        broadcastMessage(Protocol.S2C_PEERLESS_PREP_START);

        if (peerlessTimer != null) peerlessTimer.cancel();
        peerlessTimer = new Timer();
        peerlessTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameRoom.this) {
                    if (!gameStarted) {
                        this.cancel();
                        return;
                    }

                    int time = peerlessSecondsRemaining.decrementAndGet();
                    broadcastMessage(Protocol.S2C_PEERLESS_PREP_TIMER_UPDATE + ":" + time + ":PREP");

                    if (time <= 0) {
                        this.cancel();
                        peerlessGamePhase = PeerlessPhase.COUNTDOWN;
                        startPeerlessMainCountdown(PEERLESS_COUNTDOWN_SECONDS);
                    }
                }
            }
        }, 1000, 1000);
    }

    private boolean allFirstBlocksPlaced() {
        for (int i = 1; i <= 4; i++) {
            if (isFirstMoveForColor.get(i)) {

                boolean colorInPlay = false;
                for (int[] colors : playerColors.values()) {
                    for (int c : colors) {
                        if (c == i) {
                            colorInPlay = true;
                            break;
                        }
                    }
                }
                if (colorInPlay) return false;
            }
        }
        return true;
    }

    private void startPeerlessMainCountdown(int seconds) {
        peerlessGamePhase = PeerlessPhase.COUNTDOWN;
        peerlessSecondsRemaining.set(seconds);

        if (peerlessTimer != null) peerlessTimer.cancel();
        peerlessTimer = new Timer();
        peerlessTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameRoom.this) {
                    if (!gameStarted) {
                        this.cancel();
                        return;
                    }

                    int time = peerlessSecondsRemaining.get();
                    broadcastMessage(Protocol.S2C_PEERLESS_PREP_TIMER_UPDATE + ":" + time + ":COUNTDOWN");
                    peerlessSecondsRemaining.decrementAndGet();

                    if (time <= 0) {
                        this.cancel();
                        broadcastMessage(Protocol.S2C_PEERLESS_MAIN_START);
                        peerlessGamePhase = PeerlessPhase.MAIN;
                        startPeerlessMainGameTimer();
                    }
                }
            }
        }, 0, 1000);
    }

    private void startPeerlessMainGameTimer() {
        peerlessGamePhase = PeerlessPhase.MAIN;
        peerlessSecondsRemaining.set(PEERLESS_MAIN_TIME_SECONDS);

        if (peerlessTimer != null) peerlessTimer.cancel();
        peerlessTimer = new Timer();
        peerlessTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameRoom.this) {
                    if (!gameStarted) {
                        this.cancel();
                        return;
                    }
                    int time = peerlessSecondsRemaining.decrementAndGet();
                    broadcastMessage(Protocol.S2C_PEERLESS_TIMER_UPDATE + ":" + time);

                    if (time <= 0) {
                        this.cancel();
                        handleGameOver(false);
                    }
                }
            }
        }, 1000, 1000);
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
        if (gameMode == GameMode.CLASSIC) {
            handleClassicPlaceBlock(player, data);
        } else {
            handlePeerlessPlaceBlock(player, data);
        }
    }

    private synchronized void handleClassicPlaceBlock(ClientHandler player, String data) {
        ClientHandler turnPlayer = getPlayerByColor(currentTurnColor);

        if (turnPlayer == null || !turnPlayer.equals(player)) {
            player.sendMessage(Protocol.S2C_INVALID_MOVE + ":당신의 턴이 아닙니다.");
            return;
        }

        String[] parts = data.split(":");
        // Expected format: ID:x:y:rotation:flipped
        if (parts.length < 5) {
            player.sendMessage(Protocol.S2C_INVALID_MOVE + ":잘못된 요청입니다.");
            return;
        }

        String pieceId = parts[0];
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        int rotation = Integer.parseInt(parts[3]);
        int flipped = Integer.parseInt(parts[4]);

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

        if (flipped == 1) pieceToPlace.flip();
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

        // passCount = 0; // Removed
        advanceTurn();
    }

    private synchronized void handlePeerlessPlaceBlock(ClientHandler player, String data) {
        String[] parts = data.split(":");
        // Expected format: ID:x:y:rotation:flipped:color
        if (parts.length < 6) {
            player.sendMessage(Protocol.S2C_PEERLESS_PLACE_FAIL + ":잘못된 요청입니다.");
            return;
        }

        String pieceId = parts[0];
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        int rotation = Integer.parseInt(parts[3]);
        int flipped = Integer.parseInt(parts[4]);
        int colorToPlace = Integer.parseInt(parts[5]);

        if (peerlessGamePhase == PeerlessPhase.PREP) {
            if (!isFirstMoveForColor.get(colorToPlace)) {
                player.sendMessage(Protocol.S2C_PEERLESS_PLACE_FAIL + ":준비 시간에는 색상별로 첫 블록 하나만 놓을 수 있습니다.");
                return;
            }
        }

        if (peerlessGamePhase == PeerlessPhase.COUNTDOWN) {
            player.sendMessage(Protocol.S2C_PEERLESS_PLACE_FAIL + ":게임 시작 카운트다운 중입니다.");
            return;
        }

        int[] myColors = playerColors.get(player);
        boolean ownsColor = false;
        if (myColors != null) {
            for (int c : myColors) {
                if (c == colorToPlace) {
                    ownsColor = true;
                    break;
                }
            }
        }
        if (!ownsColor) {
            player.sendMessage(Protocol.S2C_PEERLESS_PLACE_FAIL + ":권한이 없는 색상입니다.");
            return;
        }

        if (isTimedOut.get(colorToPlace)) {
            player.sendMessage(Protocol.S2C_PEERLESS_PLACE_FAIL + ":이미 점수가 확정된 색상입니다.");
            return;
        }

        BlokusPiece pieceToPlace = null;
        List<BlokusPiece> hand = playerHands.get(player);
        for (BlokusPiece piece : hand) {
            if (piece.getId().equals(pieceId) && piece.getColor() == colorToPlace) {
                pieceToPlace = new BlokusPiece(piece);
                break;
            }
        }
        if (pieceToPlace == null) {
            player.sendMessage(Protocol.S2C_PEERLESS_PLACE_FAIL + ":해당 조각이(ID:" + pieceId + ", Color:" + colorToPlace + ") 없거나 이미 사용했습니다.");
            return;
        }

        if (flipped == 1) pieceToPlace.flip();
        for (int i = 0; i < rotation; i++) pieceToPlace.rotate();

        if (!isValidMove(pieceToPlace, x, y, colorToPlace)) {
            player.sendMessage(Protocol.S2C_PEERLESS_PLACE_FAIL + ":놓을 수 없는 위치입니다. (규칙 위반)");
            return;
        }

        placePieceOnBoard(pieceToPlace, x, y);
        boolean wasFirstMove = isFirstMoveForColor.get(colorToPlace);
        isFirstMoveForColor.put(colorToPlace, false);

        BlokusPiece originalPiece = null;
        for (BlokusPiece piece : hand) {
            if (piece.getId().equals(pieceId) && piece.getColor() == colorToPlace) {
                originalPiece = piece;
                break;
            }
        }
        hand.remove(originalPiece);

        player.sendMessage(Protocol.S2C_PEERLESS_PLACE_SUCCESS + ":" + pieceId + ":" + colorToPlace);
        broadcastPeerlessBoardState();

        if (wasFirstMove && peerlessGamePhase == PeerlessPhase.PREP && allFirstBlocksPlaced()) {
            if (peerlessTimer != null) peerlessTimer.cancel();
            peerlessGamePhase = PeerlessPhase.COUNTDOWN;
            startPeerlessMainCountdown(PEERLESS_COUNTDOWN_SECONDS);
        }
    }


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

    // handlePassTurn 삭제됨

    public synchronized void handleResignColor(ClientHandler player, String data) {
        if (gameMode == GameMode.PEERLESS) return;
        if (!gameStarted) return;

        try {
            int colorToResign = Integer.parseInt(data);

            if (colorToResign != currentTurnColor) {
                player.sendMessage(Protocol.S2C_INVALID_MOVE + ":현재 턴의 색상만 점수를 확정할 수 있습니다.");
                return;
            }

            ClientHandler turnPlayer = getPlayerByColor(currentTurnColor);
            if (turnPlayer == null || !turnPlayer.equals(player)) {
                player.sendMessage(Protocol.S2C_INVALID_MOVE + ":당신의 턴이 아닙니다.");
                return;
            }

            if (!isTimedOut.get(colorToResign)) {
                isTimedOut.put(colorToResign, true);
                broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + getColorName(colorToResign) + " 색의 점수가 확정되었습니다.");
            }

            // handlePassTurn(null) 대신 advanceTurn 호출
            if (checkGameOver()) {
                handleGameOver(false);
            } else {
                advanceTurn();
            }

        } catch (NumberFormatException e) {
            System.err.println("Invalid C2S_RESIGN_COLOR data: " + data);
        }
    }

    public synchronized void handlePeerlessResign(ClientHandler player) {
        if (gameMode == GameMode.CLASSIC) return;
        if (!gameStarted) return;

        int[] colors = playerColors.get(player);
        if (colors != null) {
            for (int c : colors) {
                if (!isTimedOut.get(c)) {
                    isTimedOut.put(c, true);
                    broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + getColorName(c) + " (" + player.getUsername() + ") 님의 점수가 확정되었습니다.");
                }
            }
        }

        if (checkGameOver()) {
            handleGameOver(false);
        }
    }

    public synchronized void handleDisconnectOrResign(ClientHandler player, String reason) {
        if (!gameStarted) return;

        int[] colors = playerColors.get(player);
        if (colors != null) {
            for (int c : colors) {
                if (!isTimedOut.get(c)) {
                    isTimedOut.put(c, true);
                    broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + getColorName(c) + " 색이 연결 종료되어 점수가 확정되었습니다.");
                }
            }
        }

        if (gameMode == GameMode.CLASSIC) {
            ClientHandler currentTurnPlayer = getPlayerByColor(currentTurnColor);
            if (player.equals(currentTurnPlayer)) {
                broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + player.getUsername() + "님이 턴을 포기했습니다. 턴이 넘어갑니다.");
                // handlePassTurn(null) 대신 advanceTurn 또는 GameOver 체크
                if (checkGameOver()) {
                    handleGameOver(false);
                } else {
                    advanceTurn();
                }
            }
        } else {
            if (checkGameOver()) {
                handleGameOver(false);
            }
        }
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

    private synchronized void advanceTurn() {
        if (gameMode == GameMode.PEERLESS) return;

        if (currentTimerTask != null) {
            currentTimerTask.cancel();
        }

        int attempts = 0;
        do {
            currentPlayerTurnIndex = (currentPlayerTurnIndex + 1) % 4;
            currentTurnColor = currentPlayerTurnIndex + 1;
            attempts++;
            if (attempts > 4) {
                if (!checkGameOver()) {
                    handleGameOver(false);
                }
                return;
            }
        } while (isTimedOut.get(currentTurnColor));

        if (!hasPiecesRemaining(currentTurnColor)) {
            // broadcastMessage 제거: 블록 소진 시 시스템 메시지 안 보냄 (UI상 X 표시 방지)
            isTimedOut.put(currentTurnColor, true); // Mark as done
            if (checkGameOver()) {
                handleGameOver(false);
            } else {
                advanceTurn();
            }
            return;
        }

        int newTime = remainingTime.get(currentTurnColor) + CLASSIC_TIME_BONUS_SECONDS;
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
                    if (!gameStarted || gameMode == GameMode.PEERLESS) {
                        this.cancel();
                        return;
                    }

                    int time = remainingTime.get(currentTurnColor) - 1;
                    remainingTime.put(currentTurnColor, time);

                    if (time <= 0) {
                        // 시간 초과 시 탈락 처리(isTimedOut=true) 제거
                        // 단순히 턴만 넘어감
                        broadcastMessage(Protocol.S2C_SYSTEM_MSG + ":" + getColorName(currentTurnColor) + " 님의 시간이 초과되어 턴이 넘어갑니다.");
                        broadcastTimeUpdate();

                        // 시간 초과는 게임 종료 사유가 아니므로 checkGameOver() 호출 없이 바로 advanceTurn()
                        advanceTurn();
                        this.cancel();
                    } else {
                        broadcastTimeUpdate();
                    }
                }
            }
        };
        if (gameTimer == null) gameTimer = new Timer();
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
            boolean colorInPlay = false;
            for (int[] colors : playerColors.values()) {
                for (int c : colors) {
                    if (c == i) {
                        colorInPlay = true;
                        break;
                    }
                }
            }
            if (colorInPlay && !isTimedOut.get(i)) {
                activeColors++;
            }
        }

        if (activeColors == 0) return true;

        // passCount 관련 로직 제거됨: 게임 종료는 모든 플레이어가 Resign/Timeout일 때만 발생

        return false;
    }

    private boolean isPlayerTimedOut(ClientHandler player) {
        int[] colors = playerColors.get(player);
        if (colors == null) return true;

        int timedOutCount = 0;
        for (int c : colors) {
            if (isTimedOut.getOrDefault(c, false)) {
                timedOutCount++;
            }
        }
        return timedOutCount == colors.length;
    }

    private void handleGameOver(boolean forced) {
        if (!gameStarted) return;
        gameStarted = false;

        if (currentTimerTask != null) currentTimerTask.cancel();
        if (gameTimer != null) gameTimer.cancel();
        if (peerlessTimer != null) peerlessTimer.cancel();

        String resultMessage;
        Map<String, Double> scoreChanges = new HashMap<>();

        if (forced) {
            resultMessage = "WINNER:" + (players.isEmpty() ? "NONE" : players.get(0).getUsername()) + " (강제승)";
        } else {
            Map<ClientHandler, Integer> scores = new HashMap<>();

            for (ClientHandler player : playerColors.keySet()) {

                int score = 0;
                List<BlokusPiece> hand = playerHands.get(player);

                if (hand == null) {
                    score = 999;
                } else if (hand.isEmpty()) {
                    score = 0;
                } else {
                    for (BlokusPiece piece : hand) {
                        score += piece.getSize();
                    }
                }

                scores.put(player, score);
            }

            if (playerCountOnStart == 2) {
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
                List<Map.Entry<ClientHandler, Integer>> sorted = new ArrayList<>(scores.entrySet());
                sorted.sort(Comparator.comparingInt(Map.Entry::getValue));

                double[] points = {2.0, 1.0, 0.0, -1.0};
                StringBuilder rankStr = new StringBuilder();

                for (int i = 0; i < sorted.size(); i++) {
                    ClientHandler p = sorted.get(i).getKey();
                    int s = sorted.get(i).getValue();
                    double pointChange = (i < points.length) ? points[i] : -1.0;

                    if (s == 999) pointChange = -1.0;

                    scoreChanges.put(p.getUsername(), pointChange);

                    String scoreDisplay;
                    if (s == 999) {
                        scoreDisplay = "오류";
                    } else {
                        scoreDisplay = s + "점";
                    }

                    rankStr.append((i + 1)).append("등: ").append(p.getUsername()).append(" (")
                            .append(scoreDisplay).append(" / ").append(pointChange > 0 ? "+" : "")
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
        ClientHandler player = getPlayerByColor(color);
        if (player != null) {
            return player.getUsername();
        }
        return "X";
    }

    private String getColorName(int color) {
        if (color == 1) return "Red";
        if (color == 2) return "Blue";
        if (color == 3) return "Yellow";
        if (color == 4) return "Green";
        return "Unknown";
    }

    private void broadcastGameState() {
        if (gameMode == GameMode.PEERLESS) return;

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

    private void broadcastPeerlessBoardState() {
        if (gameMode == GameMode.CLASSIC) return;

        StringBuilder boardData = new StringBuilder();
        for (int r = 0; r < 20; r++) {
            for (int c = 0; c < 20; c++) {
                boardData.append(board[r][c]).append(",");
            }
        }
        boardData.deleteCharAt(boardData.length() - 1);
        broadcastMessage(Protocol.S2C_PEERLESS_BOARD_UPDATE + ":" + boardData);
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

    private boolean hasPiecesRemaining(int color) {
        ClientHandler player = getPlayerByColor(color);
        if (player == null) return false;
        List<BlokusPiece> hand = playerHands.get(player);
        if (hand == null) return false;

        for (BlokusPiece piece : hand) {
            if (piece.getColor() == color) {
                return true;
            }
        }
        return false;
    }

    public int getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public int getPlayerCount() { return players.size(); }
    public boolean isGameStarted() { return gameStarted; }
    public List<ClientHandler> getPlayers() { return players; }
    public GameMode getGameMode() { return gameMode; }
}