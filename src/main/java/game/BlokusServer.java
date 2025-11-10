package game;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BlokusServer {
    private static final int PORT = 12345;

    private ConcurrentHashMap<Integer, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ClientHandler> lobbyClients = new ConcurrentHashMap<>();
    private AtomicInteger roomIdCounter = new AtomicInteger(0);

    private ConcurrentHashMap<String, Double> playerScores = new ConcurrentHashMap<>();
    private static final String SCORES_FILE = "blokus_scores.properties";

    public static void main(String[] args) {
        new BlokusServer().startServer();
    }

    public void startServer() {
        loadScores();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("블로커스 서버 시작. 포트: " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("새 클라이언트 접속: " + clientSocket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clientHandler.start();
            }
        } catch (IOException e) {
            System.err.println("서버 소켓 오류: " + e.getMessage());
        }
    }

    private void loadScores() {
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(SCORES_FILE)) {
            props.load(input);
            for (String username : props.stringPropertyNames()) {
                playerScores.put(username, Double.parseDouble(props.getProperty(username)));
            }
            System.out.println("스코어 로드 완료: " + SCORES_FILE);
        } catch (FileNotFoundException e) {
            System.out.println("스코어 파일 없음. 새로 생성합니다.");
        } catch (IOException | NumberFormatException e) {
            System.err.println("스코어 로드 중 오류 발생: " + e.getMessage());
        }
    }

    private synchronized void saveScores() {
        Properties props = new Properties();
        for (Map.Entry<String, Double> entry : playerScores.entrySet()) {
            props.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        try (OutputStream output = new FileOutputStream(SCORES_FILE)) {
            props.store(output, "Blokus Player Scores");
            System.out.println("스코어 저장 완료: " + SCORES_FILE);
        } catch (IOException e) {
            System.err.println("스코어 저장 중 오류 발생: " + e.getMessage());
        }
    }

    public synchronized void recordGameResult(Map<String, Double> scoreChanges) {
        for (Map.Entry<String, Double> entry : scoreChanges.entrySet()) {
            String username = entry.getKey();
            double change = entry.getValue();
            double currentScore = playerScores.getOrDefault(username, 0.0);
            playerScores.put(username, currentScore + change);
        }
        saveScores();
    }

    public void sendLeaderboard(ClientHandler client) {
        if (playerScores.isEmpty()) {
            client.sendMessage(Protocol.S2C_LEADERBOARD_DATA);
            return;
        }
        List<Map.Entry<String, Double>> sortedScores = new ArrayList<>(playerScores.entrySet());
        sortedScores.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        StringBuilder leaderboardData = new StringBuilder(Protocol.S2C_LEADERBOARD_DATA + ":");
        for (Map.Entry<String, Double> entry : sortedScores) {
            leaderboardData.append(entry.getKey()).append("/").append(entry.getValue()).append(";");
        }
        leaderboardData.deleteCharAt(leaderboardData.length() - 1);
        client.sendMessage(leaderboardData.toString());
    }


    public synchronized boolean isUsernameTakenAnywhere(String username) {
        // (4번) 수정: 대소문자를 구분하지 않고 로비 유저 검색
        for (String lobbyUsername : lobbyClients.keySet()) {
            if (lobbyUsername.equalsIgnoreCase(username)) {
                return true;
            }
        }
        // (4번) 방 검색은 이미 equalsIgnoreCase를 사용 중
        for (GameRoom room : gameRooms.values()) {
            if (room.isPlayerInRoom(username)) return true;
        }
        return false;
    }

    public synchronized boolean isRoomNameTaken(String roomName) {
        for (GameRoom room : gameRooms.values()) {
            if (room.getRoomName().equalsIgnoreCase(roomName)) {
                return true;
            }
        }
        return false;
    }

    public void addClientToLobby(ClientHandler client) {
        lobbyClients.put(client.getUsername(), client);
        sendLeaderboard(client);
    }

    public void removeClientFromLobby(ClientHandler client) {
        if (client.getUsername() != null) {
            lobbyClients.remove(client.getUsername());
        }
    }

    public GameRoom createRoom(String roomName, ClientHandler host) {
        int roomId = roomIdCounter.incrementAndGet();
        GameRoom newRoom = new GameRoom(roomId, roomName, host, this);
        gameRooms.put(roomId, newRoom);

        removeClientFromLobby(host);
        newRoom.addPlayer(host);

        System.out.println("방 생성됨: " + roomName + " (ID: " + roomId + ") by " + host.getUsername());
        return newRoom;
    }

    public GameRoom joinRoom(int roomId, ClientHandler player) {
        GameRoom room = gameRooms.get(roomId);
        if (room != null && !room.isGameStarted() && room.getPlayerCount() < 4) {
            removeClientFromLobby(player);
            room.addPlayer(player);
            System.out.println(player.getUsername() + "가 방 " + roomId + "에 참여.");
            return room;
        }
        return null;
    }

    public void leaveRoom(GameRoom room, ClientHandler player) {
        boolean remove = room.removePlayer(player);

        if (remove) {
            gameRooms.remove(room.getRoomId());
            System.out.println("방 " + room.getRoomId() + " 제거됨 (0명).");
        } else {
            System.out.println(player.getUsername() + "가 방 " + room.getRoomId() + "에서 나감.");
        }

        addClientToLobby(player);
    }

    public void removeRoom(int roomId) {
        GameRoom room = gameRooms.remove(roomId);
        if (room != null) {
            System.out.println("게임 종료. 방 " + roomId + " 제거됨.");
            for (ClientHandler player : room.getPlayers()) {
                player.setCurrentRoom(null);
                addClientToLobby(player);
            }
        }
    }

    public void sendRoomList(ClientHandler client) {
        StringBuilder roomListStr = new StringBuilder(Protocol.S2C_ROOM_LIST);
        boolean hasData = false;
        for (GameRoom room : gameRooms.values()) {
            if (!room.isGameStarted()) {
                if (!hasData) {
                    roomListStr.append(":");
                    hasData = true;
                }
                roomListStr.append(String.format("[%d,%s,%d/4];",
                        room.getRoomId(), room.getRoomName(), room.getPlayerCount()));
            }
        }
        if (hasData) {
            roomListStr.deleteCharAt(roomListStr.length() - 1);
        }
        client.sendMessage(roomListStr.toString());
    }

    public GameRoom getRoom(int roomId) {
        return gameRooms.get(roomId);
    }

    public void onClientDisconnect(ClientHandler client) {
        if (client.getCurrentRoom() != null) {
            GameRoom room = client.getCurrentRoom();
            // (3번) 수정: handleDisconnectOrResign을 먼저 호출하여 턴을 넘기거나 isTimedOut 처리
            room.handleDisconnectOrResign(client, "disconnect");
            // (3번) leaveRoom은 player를 list에서 제거하고 lobby로 보냄
            leaveRoom(room, client);
        }
        // (4번) 로비/방 여부와 관계없이 무조건 removeClientFromLobby 호출
        removeClientFromLobby(client);
        System.out.println(client.getUsername() + " 접속 종료.");
    }
}