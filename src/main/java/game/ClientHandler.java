package game;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandler extends Thread {
    private final Socket socket;
    private final BlokusServer server;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private String username;
    private GameRoom currentRoom;
    private boolean authenticated = false;

    private boolean closed = false;

    public ClientHandler(Socket socket, BlokusServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // ObjectStream 생성 순서 중요 (Output 먼저)
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Object obj = in.readObject();
                if (obj == null) break;

                if (obj instanceof BlokusMsg) {
                    BlokusMsg msg = (BlokusMsg) obj;

                    // 로그 출력 (필터링은 서버 GUI에서 처리)
                    System.out.println("Client (" + (username != null ? username : "???") + "): " + msg);

                    handleMessage(msg);
                } else {
                    System.err.println("올바르지 않은 객체 수신: " + obj.getClass().getName());
                }
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

    private void handleMessage(BlokusMsg msg) {
        int code = msg.getCode();
        String data = msg.getData(); // 이제 명령어 부분이 빠진 순수 데이터만 들어옴

        try {
            // 로그인 전에는 로그인 패킷만 허용
            if (!authenticated && code != Protocol.C2S_LOGIN) {
                sendMessage(new BlokusMsg(Protocol.S2C_LOGIN_FAIL, "로그인이 필요합니다."));
                return;
            }

            switch (code) {
                case Protocol.C2S_LOGIN:
                    handleLogin(data);
                    break;

                case Protocol.C2S_GET_LEADERBOARD:
                    server.sendLeaderboard(this);
                    break;

                case Protocol.C2S_GET_ROOM_LIST:
                    server.sendRoomList(this);
                    break;

                case Protocol.C2S_CREATE_ROOM:
                    // Data format: "RoomName" or "RoomName:Mode"
                    if (data != null && data.contains(":")) {
                        String[] parts = data.split(":", 2);
                        handleCreateRoom(parts[0], parts[1]);
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
                    handlePlaceBlock(data);
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
                    // Data format: "TargetUser:Message"
                    if (data != null && data.contains(":")) {
                        String[] parts = data.split(":", 2);
                        server.sendWhisper(this, parts[0], parts[1]);
                    } else {
                        sendMessage(new BlokusMsg(Protocol.S2C_SYSTEM_MSG, "귓속말 형식이 올바르지 않습니다."));
                    }
                    break;

                default:
                    System.err.println("처리되지 않은 프로토콜 코드: " + code);
            }
        } catch (Exception e) {
            System.err.println("메시지 처리 중 예외 발생 (" + msg + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleLogin(String usernameRaw) {
        if (usernameRaw == null || usernameRaw.trim().isEmpty()) {
            sendMessage(new BlokusMsg(Protocol.S2C_LOGIN_FAIL, "유효하지 않은 이름입니다."));
            cleanup();
            return;
        }
        if (server.isUsernameTakenAnywhere(usernameRaw)) {
            sendMessage(new BlokusMsg(Protocol.S2C_LOGIN_FAIL, "이미 사용중인 이름입니다."));
            cleanup();
            return;
        }

        this.username = usernameRaw;
        this.authenticated = true;
        sendMessage(new BlokusMsg(Protocol.S2C_LOGIN_SUCCESS));
        server.addClientToLobby(this);
    }

    private void handleCreateRoom(String roomName, String modeStr) {
        if (currentRoom != null) {
            sendMessage(new BlokusMsg(Protocol.S2C_SYSTEM_MSG, "이미 방에 입장해 있습니다."));
            return;
        }
        if (server.isRoomNameTaken(roomName)) {
            sendMessage(new BlokusMsg(Protocol.S2C_SYSTEM_MSG, "이미 존재하는 방 이름입니다."));
            return;
        }

        GameRoom.GameMode gameMode;
        try {
            gameMode = GameRoom.GameMode.valueOf(modeStr.toUpperCase());
        } catch (Exception e) {
            gameMode = GameRoom.GameMode.CLASSIC;
        }

        GameRoom newRoom = server.createRoom(roomName, this, gameMode);
        this.currentRoom = newRoom;

        // 데이터 포맷: "RoomID:RoomName"
        String joinData = newRoom.getRoomId() + ":" + newRoom.getRoomName();
        sendMessage(new BlokusMsg(Protocol.S2C_JOIN_SUCCESS, joinData));
    }

    private void handleJoinRoom(String roomIdStr) {
        if (currentRoom != null) {
            sendMessage(new BlokusMsg(Protocol.S2C_SYSTEM_MSG, "이미 방에 입장해 있습니다."));
            return;
        }
        try {
            int roomId = Integer.parseInt(roomIdStr);
            GameRoom room = server.joinRoom(roomId, this);
            if (room != null) {
                this.currentRoom = room;
                String joinData = room.getRoomId() + ":" + room.getRoomName();
                sendMessage(new BlokusMsg(Protocol.S2C_JOIN_SUCCESS, joinData));
            } else {
                sendMessage(new BlokusMsg(Protocol.S2C_JOIN_FAIL, "방이 꽉 찼거나 게임 중입니다."));
            }
        } catch (NumberFormatException e) {
            sendMessage(new BlokusMsg(Protocol.S2C_JOIN_FAIL, "잘못된 방 ID입니다."));
        }
    }

    private void handleLeaveRoom() {
        if (currentRoom == null) {
            sendMessage(new BlokusMsg(Protocol.S2C_SYSTEM_MSG, "입장한 방이 없습니다."));
            return;
        }

        server.leaveRoom(currentRoom, this);
        this.currentRoom = null;
        sendMessage(new BlokusMsg(Protocol.S2C_SYSTEM_MSG, "방에서 나왔습니다. 로비로 이동합니다."));
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
            sendMessage(new BlokusMsg(Protocol.S2C_INVALID_MOVE, "게임 중이 아닙니다."));
            return;
        }
        currentRoom.handlePlaceBlock(this, data);
    }

    private void handleChat(String message) {
        if (currentRoom != null) {
            // 채팅 보낼 때: 코드, 보낸사람(username), 내용(data)
            // 서버가 broadcast 할 때는 BlokusMsg 생성자에 username을 명시해서 보냄
            currentRoom.broadcastMessage(new BlokusMsg(Protocol.S2C_CHAT, this.username, message));
        } else {
            sendMessage(new BlokusMsg(Protocol.S2C_SYSTEM_MSG, "방에 입장해야 채팅할 수 있습니다."));
        }
    }

    /**
     * 클라이언트에게 메시지 전송 (BlokusMsg 객체)
     */
    public void sendMessage(BlokusMsg msg) {
        if (out == null) return;

        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // 동일 객체 재전송 시 참조 문제 방지

            // 로그 출력
            System.out.println("Server (to " + (username != null ? username : "???") + "): " + msg);
        } catch (IOException e) {
            System.err.println("S2C Send Error to " + username + ": " + e.getMessage());
            cleanup();
        }
    }

    private synchronized void cleanup() {
        if (closed) return;
        closed = true;

        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}

        server.onClientDisconnect(this);
    }

    public String getUsername() { return username; }
    public GameRoom getCurrentRoom() { return currentRoom; }
    public void setCurrentRoom(GameRoom room) { this.currentRoom = room; }
}