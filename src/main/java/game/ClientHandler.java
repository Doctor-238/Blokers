package game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * 각 클라이언트의 연결을 처리하는 스레드.
 */
public class ClientHandler extends Thread {
    private Socket socket;
    private BlokusServer server;
    private PrintWriter out;
    private BufferedReader in;

    private String username;
    private GameRoom currentRoom;

    public ClientHandler(Socket socket, BlokusServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                System.out.println((username != null ? username : "???") + " (C2S): " + message);
                handleMessage(message);
            }

        } catch (IOException e) {
            System.out.println((username != null ? username : "Socket") + " 연결 종료.");
        } finally {
            cleanup();
        }
    }

    private void handleMessage(String message) {
        String[] parts = message.split(":", 2);
        String command = parts[0];
        String data = (parts.length > 1) ? parts[1] : "";

        try {
            if (username == null && !command.equals(Protocol.C2S_LOGIN)) {
                sendMessage(Protocol.S2C_LOGIN_FAIL + ":로그인이 필요합니다.");
                return;
            }

            switch (command) {
                case Protocol.C2S_LOGIN:
                    handleLogin(data);
                    break;
                case Protocol.C2S_GET_ROOM_LIST:
                    server.broadcastRoomListToLobby();
                    break;
                case Protocol.C2S_CREATE_ROOM:
                    handleCreateRoom(data);
                    break;
                case Protocol.C2S_JOIN_ROOM:
                    handleJoinRoom(data);
                    break;
                case Protocol.C2S_LEAVE_ROOM:
                    handleLeaveRoom();
                    break;
                case Protocol.C2S_START_GAME:
                    handleStartGame();
                    break;
                case Protocol.C2S_KICK_PLAYER:
                    handleKickPlayer(data);
                    break;
                case Protocol.C2S_PLACE_BLOCK:
                    handlePlaceBlock(data);
                    break;
                case Protocol.C2S_PASS_TURN:
                    handlePassTurn();
                    break;
                case Protocol.C2S_CHAT:
                    handleChat(data);
                    break;
                default:
                    System.err.println("알 수 없는 명령어: " + message);
            }
        } catch (Exception e) {
            System.err.println("메시지 처리 중 예외 발생 (" + message + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    // C2S_LOGIN: <username>
    private void handleLogin(String username) {
        if (username == null || username.trim().isEmpty()) {
            sendMessage(Protocol.S2C_LOGIN_FAIL + ":유효하지 않은 이름입니다.");
            return;
        }
        if (server.isUsernameTaken(username)) {
            sendMessage(Protocol.S2C_LOGIN_FAIL + ":이미 사용중인 이름입니다.");
            return;
        }

        this.username = username;
        sendMessage(Protocol.S2C_LOGIN_SUCCESS);
        server.addClientToLobby(this);
    }

    // C2S_CREATE_ROOM: <roomName>
    private void handleCreateRoom(String roomName) {
        if (currentRoom != null) {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":이미 방에 입장해 있습니다.");
            return;
        }
        if (server.isRoomNameTaken(roomName)) {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":이미 존재하는 방 이름입니다.");
            return;
        }

        GameRoom newRoom = server.createRoom(roomName, this);
        this.currentRoom = newRoom;
        sendMessage(Protocol.S2C_JOIN_SUCCESS + ":" + newRoom.getRoomId() + ":" + newRoom.getRoomName());
    }

    // C2S_JOIN_ROOM: <roomId>
    private void handleJoinRoom(String roomIdStr) {
        if (currentRoom != null) {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":이미 방에 입장해 있습니다.");
            return;
        }
        try {
            int roomId = Integer.parseInt(roomIdStr);
            GameRoom room = server.joinRoom(roomId, this);
            if (room != null) {
                this.currentRoom = room;
                sendMessage(Protocol.S2C_JOIN_SUCCESS + ":" + room.getRoomId() + ":" + room.getRoomName());
            } else {
                sendMessage(Protocol.S2C_JOIN_FAIL + ":방이 꽉 찼거나 게임 중입니다.");
            }
        } catch (NumberFormatException e) {
            sendMessage(Protocol.S2C_JOIN_FAIL + ":잘못된 방 ID입니다.");
        }
    }

    // C2S_LEAVE_ROOM
    private void handleLeaveRoom() {
        if (currentRoom == null) {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":입장한 방이 없습니다.");
            return;
        }
        if (!currentRoom.isGameStarted()) {
            server.leaveRoom(currentRoom, this);
            this.currentRoom = null;
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":방에서 나왔습니다. 로비로 이동합니다.");
        } else {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":게임이 시작된 후에는 나갈 수 없습니다.");
        }
    }

    private void handleStartGame() {
        if (currentRoom == null) return;
        currentRoom.startGame(this);
    }

    private void handleKickPlayer(String targetUsername) {
        if (currentRoom == null) return;
        currentRoom.kickPlayer(this, targetUsername);
    }

    // C2S_PLACE_BLOCK: <pieceId>:<x>:<y>:<rotation>
    private void handlePlaceBlock(String data) {
        if (currentRoom == null || !currentRoom.isGameStarted()) {
            sendMessage(Protocol.S2C_INVALID_MOVE + ":게임 중이 아닙니다.");
            return;
        }
        currentRoom.handlePlaceBlock(this, data);
    }

    private void handlePassTurn() {
        if (currentRoom == null || !currentRoom.isGameStarted()) {
            sendMessage(Protocol.S2C_INVALID_MOVE + ":게임 중이 아닙니다.");
            return;
        }
        currentRoom.handlePassTurn(this);
    }

    private void handleChat(String message) {
        if (currentRoom != null) {
            currentRoom.broadcastMessage(Protocol.S2C_CHAT + ":" + this.username + ":" + message);
        } else {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":방에 입장해야 채팅할 수 있습니다.");
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    private void cleanup() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
        } finally {
            server.onClientDisconnect(this);
        }
    }

    public String getUsername() { return username; }
    public GameRoom getCurrentRoom() { return currentRoom; }
    public void setCurrentRoom(GameRoom room) { this.currentRoom = room; }
}