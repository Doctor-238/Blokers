package game;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class BlokusClient extends JFrame {

    private String serverHost = "localhost";
    private int serverPort = 12345;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ClientReceiver receiver;

    private String username;

    private CardLayout cardLayout;
    private JPanel mainPanel;
    private LoginScreen loginScreen;
    private LobbyScreen lobbyScreen;
    private RoomScreen roomScreen;
    private GameScreen gameScreen;

    private boolean handlingLoginFail = false;

    public BlokusClient() {
        setTitle("블로커스 (Blokus)");

        Dimension newSize = new Dimension(920, 800);
        setSize(newSize);
        setMinimumSize(newSize);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                cleanupConnection();
                System.exit(0);
            }
        });

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        loginScreen = new LoginScreen(this);
        mainPanel.add(loginScreen, "LOGIN");

        lobbyScreen = new LobbyScreen(this);
        mainPanel.add(lobbyScreen, "LOBBY");

        roomScreen = new RoomScreen(this);
        mainPanel.add(roomScreen, "ROOM");

        gameScreen = new GameScreen(this);
        mainPanel.add(gameScreen, "GAME");

        add(mainPanel);

        cardLayout.show(mainPanel, "LOGIN");
    }

    private void cleanupConnection() {
        if (out != null) out.close();
        if (in != null) try { in.close(); } catch (IOException e) {}
        if (socket != null) try { socket.close(); } catch (IOException e) {}
    }

    public void attemptLogin(String username) {
        if (username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "이름을 입력하세요.", "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            connect();
            sendMessage(Protocol.C2S_LOGIN + ":" + username);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage(), "연결 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void connect() throws IOException {
        cleanupConnection();
        socket = new Socket(serverHost, serverPort);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        receiver = new ClientReceiver(in, this);
        receiver.start();
    }

    public void handleConnectionLost() {
        if (handlingLoginFail) {
            handlingLoginFail = false;
        } else {
            JOptionPane.showMessageDialog(this, "서버와의 연결이 끊겼습니다. 로그인 화면으로 돌아갑니다.", "연결 오류", JOptionPane.ERROR_MESSAGE);
        }

        cleanupConnection();
        this.username = null;
        cardLayout.show(mainPanel, "LOGIN");
    }

    public void handleServerMessage(String message) {
        System.out.println("서버 (S2C): " + message);
        String[] parts = message.split(":", 2);
        String command = parts[0];
        String data = (parts.length > 1) ? parts[1] : null;

        SwingUtilities.invokeLater(() -> {
            switch (command) {
                case Protocol.S2C_LOGIN_SUCCESS:
                    this.username = loginScreen.getUsername();
                    cardLayout.show(mainPanel, "LOBBY");
                    break;
                case Protocol.S2C_LOGIN_FAIL:
                    handlingLoginFail = true;
                    JOptionPane.showMessageDialog(this, "로그인 실패: " + data, "오류", JOptionPane.ERROR_MESSAGE);
                    break;

                case Protocol.S2C_LEADERBOARD_DATA:
                    lobbyScreen.updateLeaderboard(data);
                    break;

                case Protocol.S2C_ROOM_LIST:
                    lobbyScreen.updateRoomList(data);
                    break;
                case Protocol.S2C_JOIN_SUCCESS:
                    roomScreen.setRoomName(data.split(":")[1]);
                    roomScreen.clearChat();
                    cardLayout.show(mainPanel, "ROOM");
                    break;
                case Protocol.S2C_JOIN_FAIL:
                    JOptionPane.showMessageDialog(this, "방 참여 실패: " + data, "오류", JOptionPane.ERROR_MESSAGE);
                    break;
                case Protocol.S2C_ROOM_UPDATE:
                    roomScreen.updatePlayerList(data, username);
                    break;
                case Protocol.S2C_KICKED:
                    JOptionPane.showMessageDialog(this, "방에서 강퇴당했습니다.", "알림", JOptionPane.INFORMATION_MESSAGE);
                    cardLayout.show(mainPanel, "LOBBY");
                    sendMessage(Protocol.C2S_GET_LEADERBOARD);
                    break;
                case Protocol.S2C_GAME_START:
                    gameScreen.initializeGame(data);
                    gameScreen.clearChat();
                    cardLayout.show(mainPanel, "GAME");
                    break;
                case Protocol.S2C_GAME_STATE:
                    gameScreen.updateGameState(data);
                    break;
                case Protocol.S2C_HAND_UPDATE:
                    gameScreen.updatePlayerHand(data);
                    gameScreen.deselectPiece();
                    break;
                case Protocol.S2C_TIME_UPDATE:
                    gameScreen.updateTimer(data);
                    break;
                case Protocol.S2C_INVALID_MOVE:
                    JOptionPane.showMessageDialog(this, "잘못된 이동: " + data, "알림", JOptionPane.WARNING_MESSAGE);
                    break;
                case Protocol.S2C_GAME_OVER:
                    JOptionPane.showMessageDialog(this, "게임 종료!\n" + data, "게임 종료", JOptionPane.INFORMATION_MESSAGE);
                    cardLayout.show(mainPanel, "LOBBY");
                    sendMessage(Protocol.C2S_GET_LEADERBOARD);
                    break;
                case Protocol.S2C_CHAT:
                    roomScreen.appendChatMessage(data);
                    gameScreen.appendChatMessage(data);
                    break;
                case Protocol.S2C_SYSTEM_MSG:
                    if (data != null && data.contains("로비로")) {
                        cardLayout.show(mainPanel, "LOBBY");
                        sendMessage(Protocol.C2S_GET_LEADERBOARD);
                    }
                    String sysMsg = "[시스템]:" + data;
                    roomScreen.appendChatMessage(sysMsg);
                    gameScreen.appendChatMessage(sysMsg);
                    break;
            }
        });
    }

    public void sendMessage(String msg) {
        if (out != null) out.println(msg);
    }

    public String getUsername() { return username; }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BlokusClient().setVisible(true));
    }
}

class ClientReceiver extends Thread {
    private final BufferedReader in;
    private final BlokusClient client;

    public ClientReceiver(BufferedReader in, BlokusClient client) {
        this.in = in;
        this.client = client;
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                client.handleServerMessage(message);
            }
        } catch (IOException e) {
        } finally {
            client.handleConnectionLost();
        }
    }
}

class LoginScreen extends JPanel {
    private final BlokusClient client;
    private final JTextField usernameField;

    public LoginScreen(BlokusClient client) {
        this.client = client;
        setLayout(new FlowLayout());
        add(new JLabel("이름:"));
        usernameField = new JTextField(12);
        add(usernameField);

        JButton loginButton = new JButton("접속");
        loginButton.addActionListener(e -> client.attemptLogin(usernameField.getText()));
        add(loginButton);
    }

    public String getUsername() {
        return usernameField.getText();
    }
}

class LobbyScreen extends JPanel {
    private final BlokusClient client;
    private final CardLayout cardLayout;

    // 점수판 패널
    private final JTable leaderboardTable;
    private final DefaultTableModel leaderboardModel;

    // 방 목록 패널
    private final JList<String> roomList;
    private final DefaultListModel<String> roomListModel;

    private static final String LEADERBOARD_PANEL = "LEADERBOARD";
    private static final String ROOM_LIST_PANEL = "ROOM_LIST";

    public LobbyScreen(BlokusClient client) {
        this.client = client;
        cardLayout = new CardLayout();
        setLayout(cardLayout);

        // --- 1. 점수판 패널 (기본) ---
        JPanel leaderboardPanel = new JPanel(new BorderLayout());

        String[] columnNames = {"순위", "이름", "점수"};
        leaderboardModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        leaderboardTable = new JTable(leaderboardModel);
        leaderboardTable.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        leaderboardTable.setRowHeight(25);
        leaderboardTable.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 14));
        leaderboardTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        leaderboardTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        leaderboardTable.getColumnModel().getColumn(2).setPreferredWidth(100);

        leaderboardPanel.add(new JScrollPane(leaderboardTable), BorderLayout.CENTER);

        // --- (1번) 점수판 하단 버튼 수정 ---
        JPanel leaderboardBottomPanel = new JPanel();

        // (1번) '방 만들기' 버튼 제거
        // JButton createButton = new JButton("방 만들기");
        // createButton.addActionListener(e -> createRoom());

        // (1번) '방 접속하기' -> '방 목록 / 생성'으로 이름 변경
        JButton showRoomsButton = new JButton("방 목록 / 생성");
        showRoomsButton.addActionListener(e -> showRoomList());

        JButton refreshLeaderboardButton = new JButton("점수 갱신");
        refreshLeaderboardButton.addActionListener(e -> client.sendMessage(Protocol.C2S_GET_LEADERBOARD));

        // leaderboardBottomPanel.add(createButton); // (1번) 제거
        leaderboardBottomPanel.add(showRoomsButton);
        leaderboardBottomPanel.add(refreshLeaderboardButton);
        leaderboardPanel.add(leaderboardBottomPanel, BorderLayout.SOUTH);
        // ------------------------------


        // --- 2. 방 목록 패널 ---
        JPanel roomListPanel = new JPanel(new BorderLayout());

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setFont(new Font("맑은 고딕", Font.PLAIN, 14));

        roomList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    joinSelectedRoom();
                }
            }
        });

        roomListPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);

        // --- (1번) 방 목록 하단 버튼 수정 ---
        JPanel roomListBottomPanel = new JPanel();

        // (1번) '방 만들기' 버튼 *추가*
        JButton createButtonInRoomList = new JButton("방 만들기");
        createButtonInRoomList.addActionListener(e -> createRoom());

        JButton joinButton = new JButton("선택한 방 접속");
        joinButton.addActionListener(e -> joinSelectedRoom());

        JButton backButton = new JButton("로비로 돌아가기");
        backButton.addActionListener(e -> cardLayout.show(this, LEADERBOARD_PANEL));

        JButton refreshRoomsButton = new JButton("방 새로고침");
        refreshRoomsButton.addActionListener(e -> client.sendMessage(Protocol.C2S_GET_ROOM_LIST));

        roomListBottomPanel.add(createButtonInRoomList); // (1번) 추가
        roomListBottomPanel.add(joinButton);
        roomListBottomPanel.add(refreshRoomsButton);
        roomListBottomPanel.add(backButton);
        roomListPanel.add(roomListBottomPanel, BorderLayout.SOUTH);
        // ------------------------------


        // --- 패널 추가 ---
        this.add(leaderboardPanel, LEADERBOARD_PANEL);
        this.add(roomListPanel, ROOM_LIST_PANEL);

        cardLayout.show(this, LEADERBOARD_PANEL);
    }

    private void createRoom() {
        String roomName = JOptionPane.showInputDialog(this, "생성할 방의 이름을 입력하세요:", "방 만들기", JOptionPane.PLAIN_MESSAGE);
        if (roomName != null && !roomName.trim().isEmpty()) {
            client.sendMessage(Protocol.C2S_CREATE_ROOM + ":" + roomName);
        }
    }

    private void showRoomList() {
        client.sendMessage(Protocol.C2S_GET_ROOM_LIST);
        cardLayout.show(this, ROOM_LIST_PANEL);
    }

    private void joinSelectedRoom() {
        String selected = roomList.getSelectedValue();
        if (selected != null && selected.startsWith("[ID:")) {
            try {
                String roomId = selected.split("]")[0].split(":")[1].trim();
                client.sendMessage(Protocol.C2S_JOIN_ROOM + ":" + roomId);
            } catch (Exception ex) {
                System.err.println("잘못된 방 선택: " + selected);
            }
        } else {
            JOptionPane.showMessageDialog(this, "방을 선택하세요.", "알림", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void updateLeaderboard(String data) {
        leaderboardModel.setRowCount(0);

        if (data == null || data.isEmpty()) {
            leaderboardModel.addRow(new Object[]{"-", "데이터 없음", "-"});
            return;
        }

        try {
            String[] scores = data.split(";");
            int rank = 1;
            for (String scoreInfo : scores) {
                String[] parts = scoreInfo.split("/");
                String username = parts[0];
                double score = Double.parseDouble(parts[1]);
                leaderboardModel.addRow(new Object[]{rank++, username, score});
            }
        } catch (Exception e) {
            System.err.println("점수판 파싱 오류: " + data);
            leaderboardModel.addRow(new Object[]{"-", "데이터 오류", "-"});
        }
    }

    public void updateRoomList(String data) {
        roomListModel.clear();
        if (data == null || data.isEmpty()) {
            roomListModel.addElement("생성된 방이 없습니다.");
            return;
        }
        String[] rooms = data.split(";");
        for (String room : rooms) {
            if (room.isBlank()) continue;
            String roomInfo = room.substring(1, room.length() - 1);
            String[] parts = roomInfo.split(",");
            if (parts.length >= 3) {
                roomListModel.addElement(String.format("[ID:%s] %s (%s)", parts[0], parts[1], parts[2]));
            }
        }
    }
}

class RoomScreen extends JPanel {
    private final BlokusClient client;
    private final JLabel roomNameLabel;
    private final JList<String> playerList;
    private final DefaultListModel<String> playerListModel;
    private final JButton startButton;
    private final JButton kickButton;

    private JTabbedPane chatTabs;
    private JTextArea chatArea;
    private JTextPane systemArea;
    private final JTextField chatField;

    private final Color DARK_YELLOW = new Color(204, 153, 0);

    private Style styleDefault;
    private Style styleRed;
    private Style styleBlue;
    private Style styleYellow;
    private Style styleGreen;

    public RoomScreen(BlokusClient client) {
        this.client = client;
        setLayout(new BorderLayout());

        roomNameLabel = new JLabel("방 이름: ", JLabel.CENTER);
        add(roomNameLabel, BorderLayout.NORTH);

        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        add(new JScrollPane(playerList), BorderLayout.CENTER);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatTabs = new JTabbedPane();

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatTabs.addTab("채팅", new JScrollPane(chatArea));

        systemArea = new JTextPane();
        systemArea.setEditable(false);

        StyledDocument doc = systemArea.getStyledDocument();
        styleDefault = systemArea.addStyle("Default", null);
        StyleConstants.setForeground(styleDefault, Color.DARK_GRAY);
        StyleConstants.setFontFamily(styleDefault, "맑은 고딕");
        StyleConstants.setFontSize(styleDefault, 12);

        styleRed = systemArea.addStyle("Red", styleDefault);
        StyleConstants.setForeground(styleRed, Color.RED);
        StyleConstants.setBold(styleRed, true);

        styleBlue = systemArea.addStyle("Blue", styleDefault);
        StyleConstants.setForeground(styleBlue, Color.BLUE);
        StyleConstants.setBold(styleBlue, true);

        styleYellow = systemArea.addStyle("Yellow", styleDefault);
        StyleConstants.setForeground(styleYellow, DARK_YELLOW);
        StyleConstants.setBold(styleYellow, true);

        styleGreen = systemArea.addStyle("Green", styleDefault);
        StyleConstants.setForeground(styleGreen, Color.GREEN.darker());
        StyleConstants.setBold(styleGreen, true);

        chatTabs.addTab("시스템", new JScrollPane(systemArea));

        chatPanel.add(chatTabs, BorderLayout.CENTER);


        JPanel chatInputPanel = new JPanel(new BorderLayout());
        chatField = new JTextField();
        chatField.addActionListener(e -> sendChat());
        JButton sendButton = new JButton("전송");
        sendButton.addActionListener(e -> sendChat());
        chatInputPanel.add(chatField, BorderLayout.CENTER);
        chatInputPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

        chatPanel.setPreferredSize(new Dimension(250, 0));
        add(chatPanel, BorderLayout.WEST);

        JPanel bottomPanel = new JPanel();
        startButton = new JButton("게임 시작");
        startButton.addActionListener(e -> client.sendMessage(Protocol.C2S_START_GAME));
        bottomPanel.add(startButton);

        kickButton = new JButton("강퇴하기");
        kickButton.addActionListener(e -> {
            String selected = playerList.getSelectedValue();
            if (selected != null) {
                String targetUser = selected.split(" ")[0];
                client.sendMessage(Protocol.C2S_KICK_PLAYER + ":" + targetUser);
            }
        });
        bottomPanel.add(kickButton);

        JButton leaveButton = new JButton("방 나가기");
        leaveButton.addActionListener(e -> client.sendMessage(Protocol.C2S_LEAVE_ROOM));
        bottomPanel.add(leaveButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void sendChat() {
        String message = chatField.getText();
        if (!message.trim().isEmpty()) {
            client.sendMessage(Protocol.C2S_CHAT + ":" + message);
            chatField.setText("");
        }
    }

    public void appendChatMessage(String data) {
        String message = data.replaceFirst(":", ": ");

        if (data.startsWith("[시스템]:") || data.startsWith(Protocol.S2C_SYSTEM_MSG)) {
            StyledDocument doc = systemArea.getStyledDocument();
            try {
                if (message.contains("턴 변경 → ")) {
                    String[] parts = message.split("→ ");
                    doc.insertString(doc.getLength(), parts[0] + "→ ", styleDefault);

                    if (parts.length > 1) {
                        String[] colorAndName = parts[1].split(" ", 2);
                        String colorName = colorAndName[0];

                        Style colorStyle = styleDefault;
                        if (colorName.equalsIgnoreCase("Red")) colorStyle = styleRed;
                        else if (colorName.equalsIgnoreCase("Blue")) colorStyle = styleBlue;
                        else if (colorName.equalsIgnoreCase("Yellow")) colorStyle = styleYellow;
                        else if (colorName.equalsIgnoreCase("Green")) colorStyle = styleGreen;

                        doc.insertString(doc.getLength(), colorName, colorStyle);
                        if (colorAndName.length > 1) {
                            doc.insertString(doc.getLength(), " " + colorAndName[1] + "\n", styleDefault);
                        } else {
                            doc.insertString(doc.getLength(), "\n", styleDefault);
                        }
                    } else {
                        doc.insertString(doc.getLength(), "\n", styleDefault);
                    }
                } else {
                    doc.insertString(doc.getLength(), message + "\n", styleDefault);
                }
            } catch (BadLocationException e) {
                // e.printStackTrace();
            }
            systemArea.setCaretPosition(systemArea.getDocument().getLength());

        } else {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }

    public void clearChat() {
        chatArea.setText("");
        systemArea.setText("");
    }

    public void setRoomName(String name) {
        roomNameLabel.setText("방 이름: " + name);
    }

    public void updatePlayerList(String data, String myUsername) {
        playerListModel.clear();
        boolean amIHost = false;

        if (data == null || data.isEmpty()) return;

        String[] players = data.split(";");
        for (String player : players) {
            if (player.isBlank()) continue;
            String playerInfo = player.substring(1, player.length() - 1);
            String[] parts = playerInfo.split(",");
            if (parts.length < 2) continue;
            String username = parts[0];
            String role = parts[1];

            String displayText = username;
            if (role.equals("host")) {
                displayText += " (방장)";
                if (username.equals(myUsername)) {
                    amIHost = true;
                }
            }
            playerListModel.addElement(displayText);
        }
        startButton.setVisible(amIHost);
        kickButton.setVisible(amIHost);
    }
}

class WrapLayout extends FlowLayout {
    public WrapLayout() { super(); }
    public WrapLayout(int align) { super(align); }
    public WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scrollPane != null) {
                targetWidth = ((JScrollPane) scrollPane).getViewport().getSize().width;
            }
            if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    if (rowWidth != 0) rowWidth += hgap;
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }
            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;

            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) dim.height += getVgap();
        dim.height += rowHeight;
    }
}