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
    // 유저와 연결된 TCP 소켓
    private Socket socket;
    // 서버 객체
    private BlokusServer server;
    // 클라이언트와 문자열 송수신 하는 스트림
    private PrintWriter out;
    private BufferedReader in;
    // 로그인한 이름
    private String username;
    // 현재 클라이언트가 존재하고 있는 방 없으면 NULL
    private GameRoom currentRoom;

    public ClientHandler(Socket socket, BlokusServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            //연결된 소켓 객체로부터 인풋스트림 설정
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            //연결된 소켓 객체로부터 아웃풋스트림 설정 autoFlush로 flush까지 자동 설정
            out = new PrintWriter(socket.getOutputStream(), true);
            //인풋 스트림으로부터 받아올 메세지
            String message;

            while ((message = in.readLine()) != null) {
                System.out.println((username != null ? username : "???") + " (C2S): " + message);
                //한 줄 읽을때마다 handleMessage 수행
                handleMessage(message);
            }

        } catch (IOException e) {
            System.out.println((username != null ? username : "Socket") + " 연결 종료.");
        } finally {
            // 연결 끊길 시에 cleanup() 호출로 정리 및 접속 종료 안내
            cleanup();
        }
    }

    private void handleMessage(String message) {
        // 메세지를 둘로 나눔
        String[] parts = message.split(":", 2);
        // 앞부분은 명령 ex) COMMAND, LOGIN, CREATE_ROOM, PLACE
        String command = parts[0];
        // data에는 명령으로 처리할 데이터
        String data = (parts.length > 1) ? parts[1] : "";

        try {
            //로그인 안한 상태에서 로그인말고 다른 명령하지 못하도록 제한
            if (username == null && !command.equals("LOGIN")) {
                sendMessage("LOGIN_FAIL:로그인이 필요합니다.");
                return;
            }

            // 해당 분기에서 호출 로직이 결정됨
            switch (command) {
                case "LOGIN":
                    handleLogin(data);
                    break;
                case "GET_ROOM_LIST":
                    server.broadcastRoomListToLobby();
                    break;
                case "CREATE_ROOM":
                    handleCreateRoom(data);
                    break;
                case "JOIN_ROOM":
                    handleJoinRoom(data);
                    break;
                case "LEAVE_ROOM":
                    handleLeaveRoom();
                    break;
                case "START_GAME":
                    handleStartGame();
                    break;
                case "KICK":
                    handleKickPlayer(data);
                    break;
                case "PLACE":
                    handlePlaceBlock(data);
                    break;
                case "PASS_TURN":
                    handlePassTurn();
                    break;
                case "CHAT":
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

    // 로그인 로직
    private void handleLogin(String username) {
        //유저 이름 비었을 시
        if (username == null || username.trim().isEmpty()) {
            sendMessage("LOGIN_FAIL:유효하지 않은 이름입니다.");
            return;
        }
        //서버 상에 중복되는 이름이 있을 시
        if (server.isUsernameTaken(username)) {
            sendMessage("LOGIN_FAIL:이미 사용중인 이름입니다.");
            return;
        }

        this.username = username;
        sendMessage("LOGIN_SUCCESS");
        //서버에 위임
        server.addClientToLobby(this);
    }

    // 방 생성 로직
    private void handleCreateRoom(String roomName) {
        // 현재 방에 입장해 있다면
        if (currentRoom != null) {
            sendMessage("SYSTEM_MSG:이미 방에 입장해 있습니다.");
            return;
        }
        // 서버에 중복되는 방 이름이 존재한다면
        if (server.isRoomNameTaken(roomName)) {
            sendMessage("SYSTEM_MSG:이미 존재하는 방 이름입니다.");
            return;
        }
        // 방 생성은 server로 위임
        GameRoom newRoom = server.createRoom(roomName, this);
        this.currentRoom = newRoom;
        sendMessage("JOIN_SUCCESS:" + newRoom.getRoomId() + ":" + newRoom.getRoomName());
    }

    //방 입장 로직
    private void handleJoinRoom(String roomIdStr) {
        // 이미 방에 입장해 있을 시
        if (currentRoom != null) {
            sendMessage("SYSTEM_MSG:이미 방에 입장해 있습니다.");
            return;
        }
        try {
            int roomId = Integer.parseInt(roomIdStr);
            GameRoom room = server.joinRoom(roomId, this);
            if (room != null) {
                // 현재 방 변수 할당
                this.currentRoom = room;
                sendMessage("JOIN_SUCCESS:" + room.getRoomId() + ":" + room.getRoomName());
            } else {
                sendMessage("JOIN_FAIL:방이 꽉 찼거나 게임 중입니다.");
            }
        } catch (NumberFormatException e) {
            sendMessage("JOIN_FAIL:잘못된 방 ID입니다.");
        }
    }

    // 방 떠나기 로직
    private void handleLeaveRoom() {
        // 현재 들어가 있는 방이 없을 시
        if (currentRoom == null) {
            sendMessage("SYSTEM_MSG:입장한 방이 없습니다.");
            return;
        }
        // 현재 방이 게임 시작 전일 시
        if (!currentRoom.isGameStarted()) {
            server.leaveRoom(currentRoom, this);
            this.currentRoom = null;
            sendMessage("SYSTEM_MSG:방에서 나왔습니다. 로비로 이동합니다.");
        } else {
            sendMessage("SYSTEM_MSG:게임이 시작된 후에는 나갈 수 없습니다.");
        }
    }

    // 게임 시작 로직
    private void handleStartGame() {
        if (currentRoom == null) return;
        // 게임 시작은 GameRoom으로 위임
        currentRoom.startGame(this);
    }

    // 플레이어 강퇴 로직
    private void handleKickPlayer(String targetUsername) {
        // 현재 들어가 있는 방이 없을 시
        if (currentRoom == null) return;
        // 플레이어 강퇴는 GameRoom으로 위임
        currentRoom.kickPlayer(this, targetUsername);
    }

    // 블록 놓기 로직
    private void handlePlaceBlock(String data) {
        // 현재 들어가있는 방이 없거나 또는 시작을 안 했을 시
        if (currentRoom == null || !currentRoom.isGameStarted()) {
            sendMessage("INVALID_MOVE:게임 중이 아닙니다.");
            return;
        }
        // GameRoom에 위임
        currentRoom.handlePlaceBlock(this, data);
    }

    // 턴 넘기기 로직
    private void handlePassTurn() {
        // 현재 들어가있는 방이 없거나 또는 시작을 안 했을 시
        if (currentRoom == null || !currentRoom.isGameStarted()) {
            sendMessage("INVALID_MOVE:게임 중이 아닙니다.");
            return;
        }
        // GameRoom에 위임
        currentRoom.handlePassTurn(this);
    }

    // 채팅 로직
    private void handleChat(String message) {
        // 현재 들어가 있는 방이 없을 시
        if (currentRoom != null) {
            //GameRoom에 위임
            currentRoom.broadcastMessage("CHAT:" + this.username + ":" + message);
        } else {
            sendMessage("SYSTEM_MSG:방에 입장해야 채팅할 수 있습니다.");
        }
    }

    //메세지 전송 로직
    // out 스트림을 통해 클라이언트에게 메세지 전송
    public void sendMessage(String message) {
        out.println(message);
    }

    // 소켓 및 스트림 정리 로직
    private void cleanup() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
        } finally {
            // server에게 위임
            server.onClientDisconnect(this);
        }
    }

    public String getUsername() { return username; }
    public GameRoom getCurrentRoom() { return currentRoom; }
    public void setCurrentRoom(GameRoom room) { this.currentRoom = room; }
}
