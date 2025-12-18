package game;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public class BlokusServer extends JFrame {
    private static final int PORT = 12345;

    private JTextArea logArea;

    private final Vector<GameRoom> gameRooms = new Vector<>();
    private final Vector<ClientHandler> lobbyClients = new Vector<>();
    private int roomIdCounter = 0;

    // 외부참조 Properties
    private final Properties scoreProps = new Properties();
    private static final String SCORES_FILE = "blokus_scores.properties";

    // 변경사항 로그 필터링 기능 추가
    private CardLayout cardLayout;
    private JPanel mainContainer;
    private JPanel topBar;
    private JButton btnTargetFilter;
    private JButton btnLogFilter;
    private JButton btnClearLog;
    private JButton btnBack;

    private JPanel targetFilterPanel;
    private JPanel logFilterPanel;
    private JPanel userCheckBoxContainer;

    // hiddenProtocols에는 이제 int code를 String으로 변환한 값(예: "32")이 저장됩니다.
    private final Set<String> hiddenTargets = ConcurrentHashMap.newKeySet();
    private final Set<String> hiddenProtocols = ConcurrentHashMap.newKeySet();
    private final Map<String, JCheckBox> userCheckBoxMap = new ConcurrentHashMap<>();

    public BlokusServer() {
        super("Blokus Server Log");
        initGUI();
    }

    private void initGUI() {
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnTargetFilter = new JButton("타겟 필터링 설정");
        btnLogFilter = new JButton("로그 필터링 설정");
        btnClearLog = new JButton("로그 지우기");
        btnBack = new JButton("뒤로가기");
        btnBack.setVisible(false);

        topBar.add(btnTargetFilter);
        topBar.add(btnLogFilter);
        topBar.add(btnClearLog);
        topBar.add(btnBack);
        add(topBar, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scrollPane = new JScrollPane(logArea);

        createTargetFilterView();
        createLogFilterView();

        mainContainer.add(scrollPane, "LOGS");
        mainContainer.add(new JScrollPane(targetFilterPanel), "TARGETS");

        JScrollPane logFilterScroll = new JScrollPane(logFilterPanel);
        logFilterScroll.getVerticalScrollBar().setUnitIncrement(16);
        mainContainer.add(logFilterScroll, "PROTOCOLS");

        add(mainContainer, BorderLayout.CENTER);

        btnTargetFilter.addActionListener(e -> showScreen("TARGETS"));
        btnLogFilter.addActionListener(e -> showScreen("PROTOCOLS"));
        btnClearLog.addActionListener(e -> logArea.setText(""));
        btnBack.addActionListener(e -> showScreen("LOGS"));

        redirectSystemStreams();

        setVisible(true);
    }

    private void showScreen(String cardName) {
        cardLayout.show(mainContainer, cardName);
        if ("LOGS".equals(cardName)) {
            btnTargetFilter.setVisible(true);
            btnLogFilter.setVisible(true);
            btnClearLog.setVisible(true);
            btnBack.setVisible(false);
        } else {
            btnTargetFilter.setVisible(false);
            btnLogFilter.setVisible(false);
            btnClearLog.setVisible(false);
            btnBack.setVisible(true);
        }
    }

    private void createTargetFilterView() {
        targetFilterPanel = new JPanel(new BorderLayout());
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.add(new JLabel("<html><h2>타겟 필터링 설정</h2><p>체크 해제시 해당 대상의 로그가 보이지 않습니다.</p></html>"));
        targetFilterPanel.add(header, BorderLayout.NORTH);

        userCheckBoxContainer = new JPanel();
        userCheckBoxContainer.setLayout(new BoxLayout(userCheckBoxContainer, BoxLayout.Y_AXIS));

        JCheckBox serverCheck = new JCheckBox("Server / System", true);
        serverCheck.addActionListener(e -> {
            if (serverCheck.isSelected()) hiddenTargets.remove("SERVER");
            else hiddenTargets.add("SERVER");
        });
        userCheckBoxContainer.add(serverCheck);
        userCheckBoxContainer.add(new JSeparator());

        targetFilterPanel.add(userCheckBoxContainer, BorderLayout.CENTER);
    }

    // [Refactoring] int형 Protocol 상수를 읽도록 수정
    private void createLogFilterView() {
        logFilterPanel = new JPanel(new BorderLayout());
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.add(new JLabel("<html><h2>로그 필터링 설정</h2><p>체크 해제시 해당 프로토콜 로그가 보이지 않습니다.</p></html>"));
        logFilterPanel.add(header, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        Map<String, List<Field>> categoryMap = new LinkedHashMap<>();
        categoryMap.put("로그인 및 로비 (Login & Lobby)", new ArrayList<>());
        categoryMap.put("방 관리 (Room Management)", new ArrayList<>());
        categoryMap.put("게임 진행 (Game Play)", new ArrayList<>());
        categoryMap.put("게임 액션 (In-Game Action)", new ArrayList<>());
        categoryMap.put("채팅 및 시스템 (Chat & System)", new ArrayList<>());
        categoryMap.put("피어리스 모드 (Peerless Mode)", new ArrayList<>());
        categoryMap.put("기타 (Others)", new ArrayList<>());

        Field[] fields = Protocol.class.getDeclaredFields();
        for (Field field : fields) {
            // int형 상수인지 확인
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) && field.getType() == int.class) {
                String name = field.getName();

                if (name.contains("LOGIN") || name.contains("LEADERBOARD") || name.contains("ROOM_LIST")) {
                    categoryMap.get("로그인 및 로비 (Login & Lobby)").add(field);
                } else if (name.contains("CREATE_ROOM") || name.contains("JOIN") || name.contains("LEAVE") || name.contains("KICK") || name.contains("ROOM_UPDATE")) {
                    categoryMap.get("방 관리 (Room Management)").add(field);
                } else if (name.contains("PEERLESS")) {
                    categoryMap.get("피어리스 모드 (Peerless Mode)").add(field);
                } else if (name.contains("CHAT") || name.contains("WHISPER") || name.contains("SYSTEM")) {
                    categoryMap.get("채팅 및 시스템 (Chat & System)").add(field);
                } else if (name.contains("PLACE") || name.contains("VALID") || name.contains("PASS")) {
                    categoryMap.get("게임 액션 (In-Game Action)").add(field);
                } else if (name.contains("GAME") || name.contains("HAND") || name.contains("TIME") || name.contains("RESIGN")) {
                    categoryMap.get("게임 진행 (Game Play)").add(field);
                } else {
                    categoryMap.get("기타 (Others)").add(field);
                }
            }
        }

        for (Map.Entry<String, List<Field>> entry : categoryMap.entrySet()) {
            String categoryName = entry.getKey();
            List<Field> fieldList = entry.getValue();
            if (fieldList.isEmpty()) continue;

            JPanel groupPanel = new JPanel(new BorderLayout());
            groupPanel.setBorder(BorderFactory.createTitledBorder(categoryName));

            JPanel splitPanel = new JPanel(new GridLayout(1, 2, 10, 0));

            JPanel leftPanel = new JPanel();
            leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

            JPanel rightPanel = new JPanel();
            rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));

            for (Field field : fieldList) {
                try {
                    // int 값 읽기
                    int protocolCode = field.getInt(null);
                    String codeStr = String.valueOf(protocolCode); // 필터링용 문자열 키

                    JCheckBox chk = new JCheckBox(field.getName(), true);
                    chk.setToolTipText("Code: " + protocolCode + " (0x" + Integer.toHexString(protocolCode).toUpperCase() + ")");

                    chk.addActionListener(e -> {
                        if (chk.isSelected()) hiddenProtocols.remove(codeStr);
                        else hiddenProtocols.add(codeStr);
                    });

                    if (field.getName().startsWith("C2S")) {
                        leftPanel.add(chk);
                    } else {
                        rightPanel.add(chk);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            JPanel leftWrapper = new JPanel(new BorderLayout());
            leftWrapper.add(leftPanel, BorderLayout.NORTH);

            JPanel rightWrapper = new JPanel(new BorderLayout());
            rightWrapper.add(rightPanel, BorderLayout.NORTH);

            splitPanel.add(leftWrapper);
            splitPanel.add(rightWrapper);

            groupPanel.add(splitPanel, BorderLayout.CENTER);

            contentPanel.add(groupPanel);
            contentPanel.add(Box.createVerticalStrut(10));
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(contentPanel, BorderLayout.NORTH);
        logFilterPanel.add(wrapper, BorderLayout.CENTER);
    }

    private void updateUserCheckboxes() {
        SwingUtilities.invokeLater(() -> {
            int componentCount = userCheckBoxContainer.getComponentCount();
            for (int i = componentCount - 1; i >= 2; i--) {
                userCheckBoxContainer.remove(i);
            }
            userCheckBoxMap.clear();

            Set<String> activeUsers = new HashSet<>();
            for (ClientHandler c : lobbyClients) {
                if (c.getUsername() != null) activeUsers.add(c.getUsername());
            }
            for (GameRoom room : gameRooms) {
                for (ClientHandler c : room.getPlayers()) {
                    if (c.getUsername() != null) activeUsers.add(c.getUsername());
                }
            }

            for (String user : activeUsers) {
                boolean isVisible = !hiddenTargets.contains(user);
                JCheckBox chk = new JCheckBox(user, isVisible);
                chk.addActionListener(e -> {
                    if (chk.isSelected()) hiddenTargets.remove(user);
                    else hiddenTargets.add(user);
                });
                userCheckBoxContainer.add(chk);
                userCheckBoxMap.put(user, chk);
            }
            userCheckBoxContainer.revalidate();
            userCheckBoxContainer.repaint();
        });
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
                if (shouldFilter(text)) {
                    return;
                }
                logArea.append(text);
            }
        });
    }

    // [Refactoring] BlokusMsg.toString() 형식에 맞춰 필터 로직 수정
    private boolean shouldFilter(String text) {
        if (text == null || text.trim().isEmpty()) return false;

        if (hiddenTargets.contains("SERVER")) {
            if (text.startsWith("블로커스 서버") || text.startsWith("새 클라이언트") || text.startsWith("서버 소켓") || text.contains("Server (S2C")
                    || text.startsWith("CLASSIC") || text.startsWith("PEERLESS") || text.startsWith("방") || text.startsWith("게임 종료")
                    || text.startsWith("스코어") || text.startsWith("???") || text.startsWith("Socket")) {
                return true;
            }
        }

        for (String hiddenUser : hiddenTargets) {
            // BlokusMsg.toString()에는 user=xxx 가 포함될 수 있음
            if (text.startsWith(hiddenUser + " ") || text.contains("user=" + hiddenUser) || text.contains("from " + hiddenUser)) {
                return true;
            }
        }

        // BlokusMsg.toString()은 "Msg{code=123, ...}" 형태임
        for (String hiddenProtoCode : hiddenProtocols) {
            // "code=123," 형태로 포함되어 있는지 확인
            if (text.contains("code=" + hiddenProtoCode + ",") || text.contains("code=" + hiddenProtoCode + " ")) {
                return true;
            }
        }

        return false;
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

    // [Refactoring] BlokusMsg 전송으로 변경
    public void sendLeaderboard(ClientHandler client) {
        synchronized (this) {
            if (scoreProps.isEmpty()) {
                // 데이터가 없을 때는 data 필드를 null로 보냄
                client.sendMessage(new BlokusMsg(Protocol.S2C_LEADERBOARD_DATA));
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

        StringBuilder leaderboardData = new StringBuilder();
        for (Map.Entry<String, Double> e : sorted) {
            leaderboardData.append(e.getKey()).append("/").append(e.getValue()).append(";");
        }
        if (leaderboardData.length() > 0) {
            leaderboardData.deleteCharAt(leaderboardData.length() - 1);
        }

        // Protocol + Data 객체 전송
        client.sendMessage(new BlokusMsg(Protocol.S2C_LEADERBOARD_DATA, leaderboardData.toString()));
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
        updateUserCheckboxes();
    }

    public synchronized void removeClientFromLobby(ClientHandler client) {
        lobbyClients.remove(client);
        updateUserCheckboxes();
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

    // [Refactoring] BlokusMsg 전송으로 변경
    public synchronized void sendRoomList(ClientHandler client) {
        StringBuilder roomListStr = new StringBuilder();
        boolean hasData = false;

        for (GameRoom room : gameRooms) {
            if (!room.isGameStarted()) {
                if (hasData) {
                    roomListStr.append(";");
                }
                roomListStr.append(String.format("[%d,%s,%d/4,%s]",
                        room.getRoomId(), room.getRoomName(), room.getPlayerCount(), room.getGameMode().name()));
                hasData = true;
            }
        }
        // Protocol은 헤더가 아닌 생성자에 포함
        client.sendMessage(new BlokusMsg(Protocol.S2C_ROOM_LIST, roomListStr.toString()));
    }

    // [Refactoring] BlokusMsg 전송으로 변경 (username 필드 활용)
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
            // 받는 사람에게: Code=WHISPER, User=보낸사람, Data=메시지
            target.sendMessage(new BlokusMsg(Protocol.S2C_WHISPER, from.getUsername(), message));

            // 보낸 사람에게(확인용): Code=WHISPER, User=받는사람(또는 나), Data=메시지 (Client에서 처리하기 나름)
            // 여기서는 'to User'라는 걸 표시하기 위해 Data에 포맷팅해서 보내거나,
            // 클라이언트 처리에 맞춰서 User 필드에 targetUsername을 넣어서 보내면 됨.
            from.sendMessage(new BlokusMsg(Protocol.S2C_WHISPER, targetUsername, "To: " + message));
        } else {
            from.sendMessage(new BlokusMsg(Protocol.S2C_SYSTEM_MSG, "[" + targetUsername + "] 님을 찾을 수 없습니다."));
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
        updateUserCheckboxes();
    }
}