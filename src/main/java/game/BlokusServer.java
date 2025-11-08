package game;

import game.auth.AuthManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 블로커스 게임 서버 메인 클래스.
 * (회원가입/로그인2/밴/유저목록 기능 추가)
 */
public class BlokusServer {
    private static final int PORT = 12345;

    private ConcurrentHashMap<Integer, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ClientHandler> lobbyClients = new ConcurrentHashMap<>();
    private AtomicInteger roomIdCounter = new AtomicInteger(0);

    // 인증/밴 관리자
    private final AuthManager authManager = new AuthManager();

    // 간단 관리자 개념: 첫 번째 가입자(또는 첫 로그인 사용자)
    private volatile String adminUsername = null;

    public static void main(String[] args) {
        new BlokusServer().startServer();
    }

    public void startServer() {
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

    // Username 중복 (로비 + 게임방)
    public synchronized boolean isUsernameTakenAnywhere(String username) {
        if (lobbyClients.containsKey(username)) return true;
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
        broadcastRoomListToLobby();
        // 관리자 알림
        if (adminUsername != null && adminUsername.equals(client.getUsername())) {
            client.sendMessage(ProtocolExt.S2C_YOU_ARE_ADMIN);
        }
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
        broadcastRoomListToLobby();
        return newRoom;
    }

    public GameRoom joinRoom(int roomId, ClientHandler player) {
        GameRoom room = gameRooms.get(roomId);
        if (room != null && !room.isGameStarted() && room.getPlayerCount() < 4) {
            removeClientFromLobby(player);
            room.addPlayer(player);
            System.out.println(player.getUsername() + "가 방 " + roomId + "에 참여.");
            broadcastRoomListToLobby();
            return room;
        }
        return null; // 실패
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
        broadcastRoomListToLobby();
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
        broadcastRoomListToLobby();
    }

    public void broadcastRoomListToLobby() {
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
        for (ClientHandler client : lobbyClients.values()) {
            client.sendMessage(roomListStr.toString());
        }
    }

    public GameRoom getRoom(int roomId) {
        return gameRooms.get(roomId);
    }

    public void onClientDisconnect(ClientHandler client) {
        if (client.getCurrentRoom() != null) {
            // 게임 중이면 기권 처리 로직
            GameRoom room = client.getCurrentRoom();
            room.handleDisconnectOrResign(client, "disconnect");
            leaveRoom(room, client);
        }
        removeClientFromLobby(client);
        System.out.println(client.getUsername() + " 접속 종료.");
    }

    // --- Auth 관련 접근 ---

    public AuthManager getAuthManager() {
        return authManager;
    }

    public synchronized void ensureAdminAssigned(String username) {
        if (adminUsername == null) {
            adminUsername = username;
            System.out.println("관리자 지정: " + adminUsername);
        }
    }

    public boolean isAdmin(String username) {
        return adminUsername != null && adminUsername.equals(username);
    }

    public void broadcastUserListTo(ClientHandler requester) {
        // USER_LIST:[name,status];...
        StringBuilder sb = new StringBuilder(ProtocolExt.S2C_USER_LIST).append(":");
        // 로비 유저
        for (String name : lobbyClients.keySet()) {
            sb.append("[").append(name).append(",online];");
        }
        // 방/게임 유저
        for (GameRoom room : gameRooms.values()) {
            for (ClientHandler p : room.getPlayers()) {
                String status = room.isGameStarted() ? "in_game" : "in_room";
                sb.append("[").append(p.getUsername()).append(",").append(status).append("];");
            }
        }
        // 밴 처리된(접속해 있지 않은) 계정도 표시
        // (접속중인 경우 위에 포함)
        // authManager는 전체 계정 목록을 안 들고 있으니 bannedUsers만 노출 (추가 확장 시 전체 user 목록 별도로 유지)
        // 여기서는 bannedUsers 따로 알 수 없으므로 밴된 유저 온라인 중이면 상태는 그대로.
        // 간단히: 요청자가 관리자라면 밴 상태 유저를 추가 나열하는 구조 필요 -> AuthManager에 전체 사용자 목록이 없으므로 생략.
        if (sb.charAt(sb.length() - 1) == ';') sb.deleteCharAt(sb.length() - 1);
        requester.sendMessage(sb.toString());
    }
}