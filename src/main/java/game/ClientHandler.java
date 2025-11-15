package game;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class ClientHandler extends Thread {
    private Socket socket;
    private BlokusServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private String username;
    private GameRoom currentRoom;
    private boolean authenticated = false;

    public ClientHandler(Socket socket, BlokusServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            String message;
            while ((message = (String) in.readObject()) != null) {
                System.out.println((username != null ? username : "???") + " (C2S): " + message);
                handleMessage(message);
            }

        } catch (SocketException | EOFException e) {
            System.out.println((username != null ? username : "Socket") + " 연결 종료.");
        } catch (IOException e) {
            System.out.println((username != null ? username : "Socket") + " IO 오류: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("알 수 없는 객체 수신: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleMessage(String message) {
        String[] parts = message.split(":", 3);
        String command = parts[0];
        String data = (parts.length > 1) ? parts[1] : "";

        try {
            if (!authenticated && !command.equals(Protocol.C2S_LOGIN)) {
                sendMessage(Protocol.S2C_LOGIN_FAIL + ":로그인이 필요합니다.");
                return;
            }

            switch (command) {
                case Protocol.C2S_LOGIN:
                    handleLegacyLogin(data);
                    break;

                case Protocol.C2S_GET_LEADERBOARD:
                    server.sendLeaderboard(this);
                    break;

                case Protocol.C2S_GET_ROOM_LIST:
                    server.sendRoomList(this);
                    break;
                case Protocol.C2S_CREATE_ROOM:
                    if (parts.length == 3) {
                        handleCreateRoom(parts[1], parts[2]);
                    } else {
                        handleCreateRoom(data, "CLASSIC");
                    }
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
                    handlePlaceBlock(message.substring(Protocol.C2S_PLACE_BLOCK.length() + 1));
                    break;
                case Protocol.C2S_PASS_TURN:
                    handlePassTurn();
                    break;

                case Protocol.C2S_RESIGN_COLOR:
                    if (currentRoom != null) {
                        currentRoom.handleResignColor(this, data);
                    }
                    break;

                case Protocol.C2S_RESIGN_PEERLESS:
                    if (currentRoom != null) {
                        currentRoom.handlePeerlessResign(this);
                    }
                    break;

                case Protocol.C2S_CHAT:
                    handleChat(data);
                    break;

                case Protocol.C2S_WHISPER:
                    String[] whisperParts = message.substring(Protocol.C2S_WHISPER.length() + 1).split(":", 2);
                    if (whisperParts.length == 2) {
                        server.sendWhisper(this, whisperParts[0], whisperParts[1]);
                    }
                    break;

                default:
                    System.err.println("알 수 없는 명령어: " + message);
            }
        } catch (Exception e) {
            System.err.println("메시지 처리 중 예외 발생 (" + message + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleLegacyLogin(String usernameRaw) {
        if (usernameRaw == null || usernameRaw.trim().isEmpty()) {
            sendMessage(Protocol.S2C_LOGIN_FAIL + ":유효하지 않은 이름입니다.");
            cleanup();
            return;
        }
        if (server.isUsernameTakenAnywhere(usernameRaw)) {
            sendMessage(Protocol.S2C_LOGIN_FAIL + ":이미 사용중인 이름입니다.");
            cleanup();
            return;
        }

        this.username = usernameRaw;
        this.authenticated = true;
        sendMessage(Protocol.S2C_LOGIN_SUCCESS);
        server.addClientToLobby(this);
    }

    private void handleCreateRoom(String roomName, String modeStr) {
        if (currentRoom != null) {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":이미 방에 입장해 있습니다.");
            return;
        }
        if (server.isRoomNameTaken(roomName)) {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":이미 존재하는 방 이름입니다.");
            return;
        }

        GameRoom.GameMode gameMode;
        try {
            gameMode = GameRoom.GameMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            gameMode = GameRoom.GameMode.CLASSIC;
        }

        GameRoom newRoom = server.createRoom(roomName, this, gameMode);
        this.currentRoom = newRoom;
        sendMessage(Protocol.S2C_JOIN_SUCCESS + ":" + newRoom.getRoomId() + ":" + newRoom.getRoomName());
    }

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

    private void handleLeaveRoom() {
        if (currentRoom == null) {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":입장한 방이 없습니다.");
            return;
        }

        server.leaveRoom(currentRoom, this);
        this.currentRoom = null;
        sendMessage(Protocol.S2C_SYSTEM_MSG + ":방에서 나왔습니다. 로비로 이동합니다.");
    }

    private void handleStartGame() {
        if (currentRoom == null) return;
        currentRoom.startGame(this);
    }

    private void handleKickPlayer(String targetUsername) {
        if (currentRoom == null) return;
        currentRoom.kickPlayer(this, targetUsername);
    }

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
        if (out != null) {
            try {
                out.writeObject(message);
                out.flush();
                System.out.println("Server (S2C to " + (username != null ? username : "???") + "): " + message);
            } catch (IOException e) {
                System.err.println("S2C Send Error to " + username + ": " + e.getMessage());
                cleanup();
            }
        }
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