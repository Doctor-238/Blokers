package game;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class BlokusServer {
    // 서버 포트 번호
    private static final int PORT = 12345;

    //Map + HashMap
    private final Map<Integer, GameRoom> gameRooms = new HashMap<>();
    private final Map<String, ClientHandler> lobbyClients = new HashMap<>();

    //방 갯수 관리 변수
    private int roomIdCounter = 0;

    public static void main(String[] args) {
        new BlokusServer().startServer();
    }

    //서버 시작 메소드
    public void startServer() {

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("블로커스 서버 시작. 포트: " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("새 클라이언트 접속: " + clientSocket.getInetAddress());

                // Thread 상속 ClientHandler 클래스 클라이언트와 통신 담당
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clientHandler.start();
            }
        } catch (IOException e) {
            System.err.println("서버 소켓 오류: " + e.getMessage());
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("서버 소켓 종료 중 오류: " + e.getMessage());
                }
            }
        }
    }

    // 이름 중복 체크
    public synchronized boolean isUsernameTaken(String username) {
        // 로비에서 해당 이름이 존재하는지 체크
        if (lobbyClients.containsKey(username)) {
            return true;
        }
        // 게임방 내에서 해당 이름이 존재하는지 체크
        for (GameRoom room : gameRooms.values()) {
            if (room.isPlayerInRoom(username)) {
                return true;
            }
        }
        return false;
    }

    // 방 이름 중복 체크
    public synchronized boolean isRoomNameTaken(String roomName) {
        for (GameRoom room : gameRooms.values()) {
            if (room.getRoomName().equalsIgnoreCase(roomName)) {
                return true;
            }
        }
        return false;
    }

    // 맵 접근 메서드에 synchronized 추가
    public synchronized void addClientToLobby(ClientHandler client) {
        lobbyClients.put(client.getUsername(), client);
        broadcastRoomListToLobby();
    }

    // 로비에서 클라이언트 제외
    public synchronized void removeClientFromLobby(ClientHandler client) {
        if (client.getUsername() != null) {
            lobbyClients.remove(client.getUsername());
        }
    }

    public synchronized GameRoom createRoom(String roomName, ClientHandler host) {

        // 방 1개 생성시 카운터 1 증가
        roomIdCounter++;
        int roomId = roomIdCounter;

        // 게임 방 객체 생성
        GameRoom newRoom = new GameRoom(roomId, roomName, host, this);
        gameRooms.put(roomId, newRoom);

        //방을 만든 유저 로비에서 제외한 후 다시 생성한 방으로 추가
        removeClientFromLobby(host);
        newRoom.addPlayer(host);

        System.out.println("방 생성됨: " + roomName + " (ID: " + roomId + ") by " + host.getUsername());

        //클라이언트 전체 갱신
        broadcastRoomListToLobby();
        return newRoom;
    }

    public synchronized GameRoom joinRoom(int roomId, ClientHandler player) {

        // 로비에서 플레이어 제외 후 방으로 추가
        GameRoom room = gameRooms.get(roomId);
        if (room != null && !room.isGameStarted() && room.getPlayerCount() < 4) {
            removeClientFromLobby(player);
            room.addPlayer(player);
            System.out.println(player.getUsername() + "가 방 " + roomId + "에 참여.");

            // 클라이언트 전체 갱신
            broadcastRoomListToLobby();
            return room;
        }
        return null; // 참여 실패
    }

    public synchronized void leaveRoom(GameRoom room, ClientHandler player) {

        // 0명일 때 방 제거 로직 GameRoom.removePlayer boolean 반환
        boolean roomShouldBeRemoved = room.removePlayer(player);

        // 유저가 나갔을 때 방이 0명일 시
        if (roomShouldBeRemoved) {
            gameRooms.remove(room.getRoomId());
            System.out.println("방 " + room.getRoomId() + " 제거됨 (0명).");
        } else {
            System.out.println(player.getUsername() + "가 방 " + room.getRoomId() + "에서 나감.");
        }

        // 로비로 유저 추가
        addClientToLobby(player);
        // 클라이언트 전체 갱신
        broadcastRoomListToLobby();
    }

    public synchronized void removeRoom(int roomId) {
        GameRoom room = gameRooms.remove(roomId);
        if (room != null) {
            System.out.println("게임 종료. 방 " + roomId + " 제거됨.");
            // 게임이 끝난 플레이어들을 로비로 이동
            for (ClientHandler player : room.getPlayers()) {
                player.setCurrentRoom(null);
                addClientToLobby(player);
            }
        }
        broadcastRoomListToLobby();
    }

    public synchronized void broadcastRoomListToLobby() {
        StringBuilder roomListStr = new StringBuilder("ROOM_LIST");

        // 방의 정보를 확인하는 플래그 변수
        boolean hasData = false;

        // 전체 방에 한해서
        for (GameRoom room : gameRooms.values()) {
            // 게임이 시작하지 않았고
            if (!room.isGameStarted()) {
                // 방의 정보가 하나도 붙지 않았다면
                if (!hasData) {
                    roomListStr.append(":"); // 첫 데이터 추가 직전에만 콜론 추가
                    hasData = true;
                }
                // 방 정보 추가
                roomListStr.append(String.format("[%d,%s,%d/4];",
                        room.getRoomId(), room.getRoomName(), room.getPlayerCount()));
            }
        }

        if (hasData) {
            roomListStr.deleteCharAt(roomListStr.length() - 1); // 마지막 ';' 제거
        }

        // 로비에 있는 모든 클라이언트에게 전송
        for (ClientHandler client : lobbyClients.values()) {
            client.sendMessage(roomListStr.toString());
        }
    }

    public synchronized GameRoom getRoom(int roomId) {
        return gameRooms.get(roomId);
    }

    //
    public synchronized void onClientDisconnect(ClientHandler client) {
        if (client.getCurrentRoom() != null) {
            // 0명 방 자동 제거 로직 호출
            leaveRoom(client.getCurrentRoom(), client);
        }
        removeClientFromLobby(client);
        System.out.println(client.getUsername() + " 접속 종료.");
    }
}
