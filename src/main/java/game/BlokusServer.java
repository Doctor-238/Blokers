package game;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import java.net.ServerSocket;
import java.net.Socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

/**
 * Blokus 서버
 * - PPT 구조 준수:
 *   1) ServerSocket.accept()는 무한루프에서 블로킹 (다중 접속 서버) :contentReference[oaicite:3]{index=3}
 *   2) accept()로 얻은 Socket은 "클라이언트 핸들러 스레드"로 처리 :contentReference[oaicite:4]{index=4}
 *   3) 다중 사용자 관리는 Vector 방식(다자간 채팅 서버 방식) :contentReference[oaicite:5]{index=5}
 */
public class BlokusServer {
    private static final int PORT = 12345;

    // ===== [CHANGED] java.util.concurrent 제거 → Vector + synchronized 로 대체 =====
    // 기존: ConcurrentHashMap<Integer, GameRoom> gameRooms
    private final Vector<GameRoom> gameRooms = new Vector<>();

    // 기존: ConcurrentHashMap<String, ClientHandler> lobbyClients
    // PPT 다자간 예제처럼 users(접속자 목록)를 Vector로 관리하는 방식에 맞춤 :contentReference[oaicite:6]{index=6}
    private final Vector<ClientHandler> lobbyClients = new Vector<>();

    // 기존: AtomicInteger roomIdCounter
    private int roomIdCounter = 0;

    // 기존: ConcurrentHashMap<String, Double> playerScores
    // 점수는 파일(Properties)로 저장/로드하므로, 서버 메소드 자체를 synchronized로 보호
    private final Properties scoreProps = new Properties();
    private static final String SCORES_FILE = "blokus_scores.properties";

    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, "UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        new BlokusServer().startServer();
    }

    public void startServer() {
        loadScores();

        // PPT: ServerSocket 생성 후 accept() 무한 루프에서 클라이언트 접속 대기 :contentReference[oaicite:7]{index=7}
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("블로커스 서버 시작. 포트: " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept(); // blocking :contentReference[oaicite:8]{index=8}
                System.out.println("새 클라이언트 접속: " + clientSocket.getInetAddress());

                // PPT: 다중 접속 서버는 accept 후 새 소켓을 스레드로 처리 :contentReference[oaicite:9]{index=9}
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clientHandler.start(); // 클라이언트별 수신 스레드 시작
            }
        } catch (IOException e) {
            System.err.println("서버 소켓 오류: " + e.getMessage());
        }
    }

    // ===== 점수 로딩/저장 (기존 로직 유지, 내부는 synchronized로 보호) =====

    private synchronized void loadScores() {
        try (InputStream input = new FileInputStream(SCORES_FILE)) {
            scoreProps.load(input);
            System.out.println("스코어 로드 완료: " + SCORES_FILE);
        } catch (FileNotFoundException e) {
            System.out.println("스코어 파일 없음. 새로 생성합니다.");
        } catch (IOException e) {
            System.err.println("스코어 로드 중 오류 발생: " + e.getMessage());
        }
    }

    private synchronized void saveScores() {
        try (OutputStream output = new FileOutputStream(SCORES_FILE)) {
            scoreProps.store(output, "Blokus Player Scores");
            System.out.println("스코어 저장 완료: " + SCORES_FILE);
        } catch (IOException e) {
            System.err.println("스코어 저장 중 오류 발생: " + e.getMessage());
        }
    }

    public synchronized void recordGameResult(Map<String, Double> scoreChanges) {
        for (Map.Entry<String, Double> entry : scoreChanges.entrySet()) {
            String username = entry.getKey();
            double change = entry.getValue();

            double current = 0.0;
            String raw = scoreProps.getProperty(username);
            if (raw != null) {
                try { current = Double.parseDouble(raw); }
                catch (NumberFormatException ignored) { current = 0.0; }
            }
            scoreProps.setProperty(username, String.valueOf(current + change));
        }
        saveScores();
    }

    public void sendLeaderboard(ClientHandler client) {
        // [CHANGED] scoreProps 기반으로 동작하도록 수정(기존 ConcurrentHashMap 제거에 따른 변경)
        synchronized (this) {
            if (scoreProps.isEmpty()) {
                client.sendMessage(Protocol.S2C_LEADERBOARD_DATA);
                return;
            }
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>();
        synchronized (this) {
            for (String username : scoreProps.stringPropertyNames()) {
                String v = scoreProps.getProperty(username);
                double d = 0.0;
                try { d = Double.parseDouble(v); } catch (Exception ignored) {}
                sorted.add(Map.entry(username, d));
            }
        }
        sorted.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        StringBuilder leaderboardData = new StringBuilder(Protocol.S2C_LEADERBOARD_DATA + ":");
        for (Map.Entry<String, Double> e : sorted) {
            leaderboardData.append(e.getKey()).append("/").append(e.getValue()).append(";");
        }
        leaderboardData.deleteCharAt(leaderboardData.length() - 1);
        client.sendMessage(leaderboardData.toString());
    }

    // ===== 로비/방 관리 (컬렉션만 교체, 기능 로직 유지) =====

    public synchronized boolean isUsernameTakenAnywhere(String username) {
        // [CHANGED] lobbyClients: Vector<ClientHandler> 순회로 변경
        for (ClientHandler c : lobbyClients) {
            if (c.getUsername() != null && c.getUsername().equalsIgnoreCase(username)) return true;
        }
        // [CHANGED] gameRooms: Vector<GameRoom> 순회로 변경
        for (GameRoom room : gameRooms) {
            if (room.isPlayerInRoom(username)) return true;
        }
        return false;
    }

    public synchronized boolean isRoomNameTaken(String roomName) {
        for (GameRoom room : gameRooms) {
            if (room.getRoomName().equalsIgnoreCase(roomName)) return true;
        }
        return false;
    }

    public synchronized void addClientToLobby(ClientHandler client) {
        // [CHANGED] Vector 기반 관리
        if (!lobbyClients.contains(client)) lobbyClients.add(client);
        sendLeaderboard(client);
    }

    public synchronized void removeClientFromLobby(ClientHandler client) {
        lobbyClients.remove(client);
    }

    public synchronized GameRoom createRoom(String roomName, ClientHandler host, GameRoom.GameMode gameMode) {
        // [CHANGED] AtomicInteger → synchronized 증가
        int roomId = ++roomIdCounter;

        GameRoom newRoom = new GameRoom(roomId, roomName, host, this, gameMode);
        gameRooms.add(newRoom);

        removeClientFromLobby(host);
        newRoom.addPlayer(host);

        System.out.println(gameMode.name() + " 방 생성됨: " + roomName + " (ID: " + roomId + ") by " + host.getUsername());
        return newRoom;
    }

    public synchronized GameRoom joinRoom(int roomId, ClientHandler player) {
        GameRoom room = getRoom(roomId);
        if (room != null && !room.isGameStarted() && room.getPlayerCount() < 4) {
            removeClientFromLobby(player);
            room.addPlayer(player);
            System.out.println(player.getUsername() + "가 방 " + roomId + "에 참여.");
            return room;
        }
        return null;
    }

    public synchronized void leaveRoom(GameRoom room, ClientHandler player) {
        boolean remove = room.removePlayer(player);

        if (remove) {
            gameRooms.remove(room);
            System.out.println("방 " + room.getRoomId() + " 제거됨 (0명).");
        } else {
            System.out.println(player.getUsername() + "가 방 " + room.getRoomId() + "에서 나감.");
        }

        addClientToLobby(player);
    }

    public synchronized void removeRoom(int roomId) {
        GameRoom room = getRoom(roomId);
        if (room != null) {
            gameRooms.remove(room);
            System.out.println("게임 종료. 방 " + roomId + " 제거됨.");
            for (ClientHandler player : room.getPlayers()) {
                player.setCurrentRoom(null);
                addClientToLobby(player);
            }
        }
    }

    public synchronized void sendRoomList(ClientHandler client) {
        StringBuilder roomListStr = new StringBuilder(Protocol.S2C_ROOM_LIST);
        boolean hasData = false;

        for (GameRoom room : gameRooms) {
            if (!room.isGameStarted()) {
                if (!hasData) {
                    roomListStr.append(":");
                    hasData = true;
                }
                roomListStr.append(String.format("[%d,%s,%d/4,%s];",
                        room.getRoomId(), room.getRoomName(), room.getPlayerCount(), room.getGameMode().name()));
            }
        }
        if (hasData) roomListStr.deleteCharAt(roomListStr.length() - 1);
        client.sendMessage(roomListStr.toString());
    }

    public synchronized void sendWhisper(ClientHandler from, String targetUsername, String message) {
        ClientHandler target = null;

        // [CHANGED] 로비는 Vector 순회
        for (ClientHandler c : lobbyClients) {
            if (c.getUsername() != null && c.getUsername().equalsIgnoreCase(targetUsername)) {
                target = c; break;
            }
        }

        // [CHANGED] 방 목록도 Vector 순회
        if (target == null) {
            for (GameRoom room : gameRooms) {
                List<ClientHandler> roomPlayers = room.getPlayers();
                synchronized (roomPlayers) {
                    for (ClientHandler c : roomPlayers) {
                        if (c.getUsername() != null && c.getUsername().equalsIgnoreCase(targetUsername)) {
                            target = c; break;
                        }
                    }
                }
                if (target != null) break;
            }
        }

        if (target != null) {
            String whisperMsg = String.format("[귓속말 from %s]:%s", from.getUsername(), message);
            target.sendMessage(Protocol.S2C_WHISPER + ":" + whisperMsg);

            String echoMsg = String.format("[귓속말 to %s]:%s", target.getUsername(), message);
            from.sendMessage(Protocol.S2C_WHISPER + ":" + echoMsg);
        } else {
            from.sendMessage(Protocol.S2C_SYSTEM_MSG + ":[" + targetUsername + "] 님을 찾을 수 없습니다.");
        }
    }

    public synchronized GameRoom getRoom(int roomId) {
        // [CHANGED] Map 조회 → Vector 순회로 조회
        for (GameRoom room : gameRooms) {
            if (room.getRoomId() == roomId) return room;
        }
        return null;
    }

    public void onClientDisconnect(ClientHandler client) {
        // 기존 로직 유지 (단, leave/remove에서 내부적으로 synchronized 처리)
        if (client.getCurrentRoom() != null) {
            GameRoom room = client.getCurrentRoom();
            room.handleDisconnectOrResign(client, "disconnect");

            if (room.getGameMode() != GameRoom.GameMode.PEERLESS) {
                leaveRoom(room, client);
            }
        }
        removeClientFromLobby(client);
        System.out.println(client.getUsername() + " 접속 종료.");
    }
}
