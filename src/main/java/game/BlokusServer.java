package game;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

public class BlokusServer extends JFrame {
    private static final int PORT = 12345;

    private JTextArea logArea;

    private final Vector<GameRoom> gameRooms = new Vector<>();
    private final Vector<ClientHandler> lobbyClients = new Vector<>();
    private int roomIdCounter = 0;

    private final Properties scoreProps = new Properties();
    private static final String SCORES_FILE = "blokus_scores.properties";

    public BlokusServer() {
        super("Blokus Server Log");
        initGUI();
    }

    private void initGUI() {
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        redirectSystemStreams();

        setVisible(true);
    }

    private void redirectSystemStreams() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                updateTextArea(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                updateTextArea(new String(b, off, len));
            }

            @Override
            public void write(byte[] b) throws IOException {
                updateTextArea(new String(b));
            }
        };

        PrintStream printStream = new PrintStream(out, true);
        System.setOut(printStream);
        System.setErr(printStream);
    }

    private void updateTextArea(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                logArea.append(text);
            }
        });
    }

    public static void main(String[] args) {
        final BlokusServer serverWindow = new BlokusServer();

        new Thread(new Runnable() {
            @Override
            public void run() {
                serverWindow.startServer();
            }
        }).start();
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

    public synchronized boolean isUsernameTakenAnywhere(String username) {
        for (ClientHandler c : lobbyClients) {
            if (c.getUsername() != null && c.getUsername().equalsIgnoreCase(username)) return true;
        }
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
        if (!lobbyClients.contains(client)) lobbyClients.add(client);
        sendLeaderboard(client);
    }

    public synchronized void removeClientFromLobby(ClientHandler client) {
        lobbyClients.remove(client);
    }

    public synchronized GameRoom createRoom(String roomName, ClientHandler host, GameRoom.GameMode gameMode) {
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

        for (ClientHandler c : lobbyClients) {
            if (c.getUsername() != null && c.getUsername().equalsIgnoreCase(targetUsername)) {
                target = c; break;
            }
        }

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
        for (GameRoom room : gameRooms) {
            if (room.getRoomId() == roomId) return room;
        }
        return null;
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