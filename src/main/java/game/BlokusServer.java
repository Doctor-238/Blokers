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
        for (String lobbyUsername : lobbyClients.keySet()) {
            if (lobbyUsername.equalsIgnoreCase(username)) {
                return true;
            }
        }
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

    public GameRoom createRoom(String roomName, ClientHandler host, GameRoom.GameMode gameMode) {
        int roomId = roomIdCounter.incrementAndGet();
        GameRoom newRoom = new GameRoom(roomId, roomName, host, this, gameMode);
        gameRooms.put(roomId, newRoom);

        removeClientFromLobby(host);
        newRoom.addPlayer(host);

        System.out.println(gameMode.name() + " 방 생성됨: " + roomName + " (ID: " + roomId + ") by " + host.getUsername());
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
                roomListStr.append(String.format("[%d,%s,%d/4,%s];",
                        room.getRoomId(), room.getRoomName(), room.getPlayerCount(), room.getGameMode().name()));
            }
        }
        if (hasData) {
            roomListStr.deleteCharAt(roomListStr.length() - 1);
        }
        client.sendMessage(roomListStr.toString());
    }

    public synchronized void sendWhisper(ClientHandler from, String targetUsername, String message) {
        ClientHandler target = null;

        for (ClientHandler client : lobbyClients.values()) {
            if (client.getUsername().equalsIgnoreCase(targetUsername)) {
                target = client;
                break;
            }
        }

        if (target == null) {
            for (GameRoom room : gameRooms.values()) {
                List<ClientHandler> roomPlayers = room.getPlayers();
                synchronized (roomPlayers) {
                    for (ClientHandler client : roomPlayers) {
                        if (client.getUsername().equalsIgnoreCase(targetUsername)) {
                            target = client;
                            break;
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

    public GameRoom getRoom(int roomId) {
        return gameRooms.get(roomId);
    }

    public void onClientDisconnect(ClientHandler client) {
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