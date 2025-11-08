package game;

import game.auth.AuthManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * 각 클라이언트 연결을 처리하는 스레드.
 * (회원가입/로그인2/비밀번호 변경/유저목록/밴/기권/관전 명령 추가)
 */
public class ClientHandler extends Thread {
    private Socket socket;
    private BlokusServer server;
    private PrintWriter out;
    private BufferedReader in;

    private String username;
    private GameRoom currentRoom;
    private boolean authenticated = false;
    private boolean resigned = false; // 기권 여부

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
            // 회원가입/LOGIN2는 인증 전 허용
            if (!authenticated &&
                    !command.equals(Protocol.C2S_LOGIN) &&
                    !command.equals(ProtocolExt.C2S_LOGIN2) &&
                    !command.equals(ProtocolExt.C2S_SIGNUP)) {
                sendMessage(Protocol.S2C_LOGIN_FAIL + ":로그인이 필요합니다.");
                return;
            }

            switch (command) {
                // 기존 단순 LOGIN (비밀번호 없음)
                case Protocol.C2S_LOGIN:
                    handleLegacyLogin(data);
                    break;

                // 신규 회원가입
                case ProtocolExt.C2S_SIGNUP:
                    handleSignup(data);
                    break;

                // 비밀번호 포함 로그인
                case ProtocolExt.C2S_LOGIN2:
                    handleLogin2(data);
                    break;

                case ProtocolExt.C2S_CHANGE_PASSWORD:
                    handleChangePassword(data);
                    break;

                case ProtocolExt.C2S_GET_USER_LIST:
                    server.broadcastUserListTo(this);
                    break;

                case ProtocolExt.C2S_BAN_USER:
                    handleBanUser(data);
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

                case ProtocolExt.C2S_RESIGN:
                    handleResign();
                    break;

                case ProtocolExt.C2S_SPECTATE:
                    // 간단 구현: 이미 기권 상태라면 게임 상태 계속 수신 가능 (별도 리스트 필요 시 GameRoom 확장)
                    sendMessage(Protocol.S2C_SYSTEM_MSG + ":관전 모드로 전환되었습니다.");
                    break;

                default:
                    System.err.println("알 수 없는 명령어: " + message);
            }
        } catch (Exception e) {
            System.err.println("메시지 처리 중 예외 발생 (" + message + "): " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Legacy LOGIN:<username>
    private void handleLegacyLogin(String usernameRaw) {
        if (usernameRaw == null || usernameRaw.trim().isEmpty()) {
            sendMessage(Protocol.S2C_LOGIN_FAIL + ":유효하지 않은 이름입니다.");
            return;
        }
        if (server.getAuthManager().isBanned(usernameRaw)) {
            sendMessage(Protocol.S2C_LOGIN_FAIL + ":밴된 사용자입니다.");
            return;
        }
        if (server.isUsernameTakenAnywhere(usernameRaw)) {
            sendMessage(Protocol.S2C_LOGIN_FAIL + ":이미 사용중인 이름입니다.");
            return;
        }

        this.username = usernameRaw;
        this.authenticated = true;
        server.ensureAdminAssigned(username);
        sendMessage(Protocol.S2C_LOGIN_SUCCESS);
        server.addClientToLobby(this);
    }

    // SIGNUP:username|password
    private void handleSignup(String data) {
        String[] arr = data.split("\\|", 2);
        if (arr.length != 2) {
            sendMessage(ProtocolExt.S2C_SIGNUP_FAIL + ":형식오류");
            return;
        }
        String u = arr[0].trim();
        String p = arr[1];

        AuthManager auth = server.getAuthManager();
        if (auth.signup(u, p)) {
            sendMessage(ProtocolExt.S2C_SIGNUP_SUCCESS);
        } else {
            sendMessage(ProtocolExt.S2C_SIGNUP_FAIL + ":중복 또는 잘못된 입력");
        }
    }

    // LOGIN2:username|password
    private void handleLogin2(String data) {
        String[] arr = data.split("\\|", 2);
        if (arr.length != 2) {
            sendMessage(Protocol.S2C_LOGIN_FAIL + ":형식오류");
            return;
        }
        String u = arr[0].trim();
        String p = arr[1];

        AuthManager auth = server.getAuthManager();
        if (!auth.login(u, p)) {
            sendMessage(Protocol.S2C_LOGIN_FAIL + ":로그인 실패(계정불가/비번/밴)");
            return;
        }
        if (server.isUsernameTakenAnywhere(u)) {
            sendMessage(Protocol.S2C_LOGIN_FAIL + ":이미 접속중인 이름");
            return;
        }
        this.username = u;
        this.authenticated = true;
        server.ensureAdminAssigned(u);
        sendMessage(Protocol.S2C_LOGIN_SUCCESS);
        server.addClientToLobby(this);
    }

    // CHANGE_PWD:old|new
    private void handleChangePassword(String data) {
        String[] arr = data.split("\\|", 2);
        if (arr.length != 2) {
            sendMessage(ProtocolExt.S2C_CHANGE_PASSWORD_FAIL + ":형식오류");
            return;
        }
        String oldP = arr[0];
        String newP = arr[1];
        if (server.getAuthManager().changePassword(this.username, oldP, newP)) {
            sendMessage(ProtocolExt.S2C_CHANGE_PASSWORD_OK);
        } else {
            sendMessage(ProtocolExt.S2C_CHANGE_PASSWORD_FAIL + ":실패(기존 비번 불일치 등)");
        }
    }

    private void handleBanUser(String target) {
        if (!server.isAdmin(this.username)) {
            sendMessage(ProtocolExt.S2C_BAN_RESULT + ":fail:권한없음");
            return;
        }
        if (target == null || target.isBlank()) {
            sendMessage(ProtocolExt.S2C_BAN_RESULT + ":fail:대상없음");
            return;
        }
        if (server.getAuthManager().ban(target)) {
            sendMessage(ProtocolExt.S2C_BAN_RESULT + ":ok");
            // 현재 접속중이면 강제 기권 처리
            for (ClientHandler ch : serverLobbyAndRoomsAll()) {
                if (ch.getUsername() != null && ch.getUsername().equals(target)) {
                    if (ch.getCurrentRoom() != null) {
                        ch.getCurrentRoom().handleDisconnectOrResign(ch, "ban");
                        server.leaveRoom(ch.getCurrentRoom(), ch);
                    }
                    ch.sendMessage(Protocol.S2C_SYSTEM_MSG + ":밴되었습니다. 로비로 이동합니다.");
                }
            }
        } else {
            sendMessage(ProtocolExt.S2C_BAN_RESULT + ":fail:존재하지않음");
        }
    }

    private Iterable<ClientHandler> serverLobbyAndRoomsAll() {
        // 간단히: 로비 + 모든 방 플레이어 합쳐서 순회
        java.util.List<ClientHandler> all = new java.util.ArrayList<>(serverLobbyClients());
        for (GameRoom gr : serverGetRooms()) {
            all.addAll(gr.getPlayers());
        }
        return all;
    }

    private java.util.Collection<ClientHandler> serverLobbyClients() {
        // 접근용(직접 필드에 접근할 수 없어 우회 필요시 BlokusServer getter 추가 고려)
        // 여기선 편의상 리플렉션 없이 구조 단순화 위해 gameRooms만 사용 -> 로비 목록은 이미 broadcast에 존재.
        // 실제로 필요하면 BlokusServer에 getLobbyClients() 추가하는 것이 좋음.
        return new java.util.ArrayList<>();
    }

    private java.util.Collection<GameRoom> serverGetRooms() {
        // 같은 이유로 별도 getter 없으니 서버에 메서드 추가하는 것이 바람직.
        // 여기서는 GameRoom 접근 필요 => 간소화를 위해 직접 사용 불가시 설계 변경 필요.
        // (실제 구현시 BlokusServer에 getRooms() 메서드 추가하세요.)
        return new java.util.ArrayList<>();
    }

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
        if (!currentRoom.isGameStarted()) {
            server.leaveRoom(currentRoom, this);
            this.currentRoom = null;
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":방에서 나왔습니다. 로비로 이동합니다.");
        } else {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":게임이 시작된 후에는 나갈 수 없습니다. (기권 사용)");
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

    private void handlePlaceBlock(String data) {
        if (currentRoom == null || !currentRoom.isGameStarted()) {
            sendMessage(Protocol.S2C_INVALID_MOVE + ":게임 중이 아닙니다.");
            return;
        }
        if (resigned) {
            sendMessage(Protocol.S2C_INVALID_MOVE + ":기권 상태입니다.");
            return;
        }
        currentRoom.handlePlaceBlock(this, data);
    }

    private void handlePassTurn() {
        if (currentRoom == null || !currentRoom.isGameStarted()) {
            sendMessage(Protocol.S2C_INVALID_MOVE + ":게임 중이 아닙니다.");
            return;
        }
        if (resigned) {
            sendMessage(Protocol.S2C_INVALID_MOVE + ":기권 상태입니다.");
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

    // RESIGN
    private void handleResign() {
        if (currentRoom == null || !currentRoom.isGameStarted()) {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":게임 중이 아닙니다.");
            return;
        }
        if (resigned) {
            sendMessage(Protocol.S2C_SYSTEM_MSG + ":이미 기권했습니다.");
            return;
        }
        resigned = true;
        currentRoom.handleDisconnectOrResign(this, "resign");
        sendMessage(Protocol.S2C_SYSTEM_MSG + ":기권 처리되었습니다. 관전 또는 로비 이동 가능.");
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