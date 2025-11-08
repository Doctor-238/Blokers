// 주: 기존 기능 + 확장 프로토콜 처리 + 회원가입/로그인2/유저목록/밴/턴변경/색탈락/타이머확장
package game;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

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

    public BlokusClient() {
        setTitle("블로커스 (Blokus)");
        Dimension minSize = new Dimension(1000, 830);
        setSize(minSize);
        setMinimumSize(minSize);
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

    // legacy login (아이디만)
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

    public void attemptLogin2(String username, String password) {
        if (username.trim().isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "이름/비밀번호를 입력하세요.", "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            connect();
            sendMessage(ProtocolExt.C2S_LOGIN2 + ":" + username + "|" + password);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage(), "연결 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void attemptSignup(String username, String password) {
        if (username.trim().isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "이름/비밀번호를 입력하세요.", "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            connect();
            sendMessage(ProtocolExt.C2S_SIGNUP + ":" + username + "|" + password);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage(), "연결 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void connect() throws IOException {
        cleanupConnection();
        socket = new Socket(serverHost, serverPort);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        receiver = new ClientReceiver(in, this);
        receiver.start();
    }

    public void handleConnectionLost() {
        JOptionPane.showMessageDialog(this, "서버와의 연결이 끊겼습니다. 로그인 화면으로 돌아갑니다.", "연결 오류", JOptionPane.ERROR_MESSAGE);
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
                    sendMessage(Protocol.C2S_GET_ROOM_LIST);
                    sendMessage(ProtocolExt.C2S_GET_USER_LIST);
                    break;
                case Protocol.S2C_LOGIN_FAIL:
                    JOptionPane.showMessageDialog(this, "로그인 실패: " + data, "오류", JOptionPane.ERROR_MESSAGE);
                    handleConnectionLost();
                    break;

                case ProtocolExt.S2C_SIGNUP_SUCCESS:
                    JOptionPane.showMessageDialog(this, "회원가입 성공! 이제 로그인하세요.", "알림", JOptionPane.INFORMATION_MESSAGE);
                    handleConnectionLost(); // 재접속 필요
                    break;
                case ProtocolExt.S2C_SIGNUP_FAIL:
                    JOptionPane.showMessageDialog(this, "회원가입 실패: " + data, "오류", JOptionPane.ERROR_MESSAGE);
                    handleConnectionLost();
                    break;

                case ProtocolExt.S2C_YOU_ARE_ADMIN:
                    lobbyScreen.setAdmin(true);
                    break;
                case ProtocolExt.S2C_USER_LIST:
                    lobbyScreen.updateUserList(data);
                    break;
                case ProtocolExt.S2C_BAN_RESULT:
                    JOptionPane.showMessageDialog(this, "밴 결과: " + data, "알림", JOptionPane.INFORMATION_MESSAGE);
                    sendMessage(ProtocolExt.C2S_GET_USER_LIST);
                    break;
                case ProtocolExt.S2C_CHANGE_PASSWORD_OK:
                    JOptionPane.showMessageDialog(this, "비밀번호 변경 성공", "알림", JOptionPane.INFORMATION_MESSAGE);
                    break;
                case ProtocolExt.S2C_CHANGE_PASSWORD_FAIL:
                    JOptionPane.showMessageDialog(this, "비밀번호 변경 실패: " + data, "오류", JOptionPane.ERROR_MESSAGE);
                    break;

                case Protocol.S2C_ROOM_LIST:
                    lobbyScreen.updateRoomList(data);
                    break;
                case Protocol.S2C_JOIN_SUCCESS:
                    roomScreen.setRoomName(data.split(":")[2]);
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
                    sendMessage(Protocol.C2S_GET_ROOM_LIST);
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
                    break;
                case Protocol.S2C_TIME_UPDATE:
                    gameScreen.updateTimer(data);
                    break;
                case ProtocolExt.S2C_TIME_UPDATE2:
                    gameScreen.updateTimerV2(data);
                    break;
                case Protocol.S2C_INVALID_MOVE:
                    JOptionPane.showMessageDialog(this, "잘못된 이동: " + data, "알림", JOptionPane.WARNING_MESSAGE);
                    break;
                case Protocol.S2C_GAME_OVER:
                    JOptionPane.showMessageDialog(this, "게임 종료!\n" + data, "게임 종료", JOptionPane.INFORMATION_MESSAGE);
                    cardLayout.show(mainPanel, "LOBBY");
                    sendMessage(Protocol.C2S_GET_ROOM_LIST);
                    break;
                case Protocol.S2C_CHAT:
                    roomScreen.appendChatMessage(data);
                    gameScreen.appendChatMessage(data);
                    break;
                case Protocol.S2C_SYSTEM_MSG:
                    if (data != null && data.contains("로비로")) {
                        cardLayout.show(mainPanel, "LOBBY");
                        sendMessage(Protocol.C2S_GET_ROOM_LIST);
                    }
                    String sysMsg = "[시스템]:" + data;
                    roomScreen.appendChatMessage(sysMsg);
                    gameScreen.appendChatMessage(sysMsg);
                    break;
                case ProtocolExt.S2C_TURN_CHANGED:
                    if (data != null) {
                        String[] ps = data.split("\\|");
                        if (ps.length >= 1) gameScreen.setTurnColor(ps[0]);
                        gameScreen.appendChatMessage("[시스템]: 턴 변경 → " + ps[0] + " (" + (ps.length > 1 ? ps[1] : "") + ")");
                    }
                    break;
                case ProtocolExt.S2C_COLOR_ELIMINATED:
                    if (data != null) {
                        String[] ps = data.split("\\|");
                        gameScreen.appendChatMessage("[시스템]: " + ps[0] + " 색 탈락 (" + (ps.length > 1 ? ps[1] : "원인없음") + ")");
                    }
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

// LoginScreen (회원가입/로그인2)
class LoginScreen extends JPanel {
    private final BlokusClient client;
    private final JTextField usernameField;
    private final JPasswordField passwordField;

    public LoginScreen(BlokusClient client) {
        this.client = client;
        setLayout(new FlowLayout());
        add(new JLabel("이름:"));
        usernameField = new JTextField(12);
        add(usernameField);

        add(new JLabel("비밀번호:"));
        passwordField = new JPasswordField(12);
        add(passwordField);

        JButton loginButton = new JButton("로그인");
        loginButton.addActionListener(e -> client.attemptLogin2(usernameField.getText(), new String(passwordField.getPassword())));
        add(loginButton);

        JButton signupButton = new JButton("회원가입");
        signupButton.addActionListener(e -> client.attemptSignup(usernameField.getText(), new String(passwordField.getPassword())));
        add(signupButton);
    }

    public String getUsername() {
        return usernameField.getText();
    }
}

// LobbyScreen (유저 목록, 밴, 비번 변경)
class LobbyScreen extends JPanel {
    private final BlokusClient client;
    private final JList<String> roomList;
    private final DefaultListModel<String> roomListModel;

    private final DefaultListModel<String> userListModel;
    private final JList<String> userList;
    private final JButton banButton;
    private final JButton refreshButton;
    private final JButton pwdChangeButton;
    private boolean isAdmin = false;

    public LobbyScreen(BlokusClient client) {
        this.client = client;
        setLayout(new BorderLayout());

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        add(new JScrollPane(roomList), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setPreferredSize(new Dimension(300, 0));
        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rightTop.add(new JLabel("접속자 목록"));
        rightPanel.add(rightTop, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        rightPanel.add(new JScrollPane(userList), BorderLayout.CENTER);

        JPanel rightBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pwdChangeButton = new JButton("비번변경");
        pwdChangeButton.addActionListener(e -> {
            JPasswordField oldF = new JPasswordField();
            JPasswordField newF = new JPasswordField();
            Object[] msg = {"기존 비번:", oldF, "새 비번:", newF};
            int c = JOptionPane.showConfirmDialog(this, msg, "비밀번호 변경", JOptionPane.OK_CANCEL_OPTION);
            if (c == JOptionPane.OK_OPTION) {
                String oldP = new String(oldF.getPassword());
                String newP = new String(newF.getPassword());
                client.sendMessage(ProtocolExt.C2S_CHANGE_PASSWORD + ":" + oldP + "|" + newP);
            }
        });

        refreshButton = new JButton("새로고침");
        refreshButton.addActionListener(e -> {
            client.sendMessage(Protocol.C2S_GET_ROOM_LIST);
            client.sendMessage(ProtocolExt.C2S_GET_USER_LIST);
        });

        banButton = new JButton("밴");
        banButton.addActionListener(e -> {
            String sel = userList.getSelectedValue();
            if (sel != null) {
                String target = sel.split("[,\\s]")[0];
                int c = JOptionPane.showConfirmDialog(this, target + " 을(를) 밴하시겠습니까?", "확인", JOptionPane.YES_NO_OPTION);
                if (c == JOptionPane.YES_OPTION) {
                    client.sendMessage(ProtocolExt.C2S_BAN_USER + ":" + target);
                }
            }
        });
        rightBottom.add(pwdChangeButton);
        rightBottom.add(refreshButton);
        rightBottom.add(banButton);
        rightPanel.add(rightBottom, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.EAST);
        updateAdmin(false);

        JPanel bottomPanel = new JPanel();
        JTextField roomNameField = new JTextField(15);
        bottomPanel.add(new JLabel("방 이름:"));
        bottomPanel.add(roomNameField);
        JButton createButton = new JButton("방 만들기");
        createButton.addActionListener(e -> {
            String roomName = roomNameField.getText();
            if (!roomName.trim().isEmpty()) {
                client.sendMessage(Protocol.C2S_CREATE_ROOM + ":" + roomName);
            }
        });
        bottomPanel.add(createButton);

        JButton joinButton = new JButton("방 들어가기");
        joinButton.addActionListener(e -> {
            String selected = roomList.getSelectedValue();
            if (selected != null && selected.startsWith("[ID:")) {
                try {
                    String roomId = selected.split("]")[0].split(":")[1].trim();
                    client.sendMessage(Protocol.C2S_JOIN_ROOM + ":" + roomId);
                } catch (Exception ex) {
                    System.err.println("잘못된 방 선택: " + selected);
                }
            }
        });
        bottomPanel.add(joinButton);

        JButton refreshRoomsButton = new JButton("방 새로고침");
        refreshRoomsButton.addActionListener(e -> client.sendMessage(Protocol.C2S_GET_ROOM_LIST));
        bottomPanel.add(refreshRoomsButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void updateAdmin(boolean admin) {
        this.isAdmin = admin;
        banButton.setEnabled(isAdmin);
    }

    public void setAdmin(boolean admin) {
        updateAdmin(admin);
    }

    public void updateUserList(String data) {
        userListModel.clear();
        if (data == null || data.isEmpty()) return;
        String[] users = data.split(";");
        for (String u : users) {
            if (u.isBlank()) continue;
            String body = u;
            if (u.startsWith("[") && u.endsWith("]")) {
                body = u.substring(1, u.length() - 1);
            }
            userListModel.addElement(body); // name,status
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

// RoomScreen (기존 + 게임 시작 / 강퇴 / 나가기)
class RoomScreen extends JPanel {
    private final BlokusClient client;
    private final JLabel roomNameLabel;
    private final JList<String> playerList;
    private final DefaultListModel<String> playerListModel;
    private final JButton startButton;
    private final JButton kickButton;

    private final JTextArea chatArea;
    private final JTextField chatField;

    public RoomScreen(BlokusClient client) {
        this.client = client;
        setLayout(new BorderLayout());

        roomNameLabel = new JLabel("방 이름: ", JLabel.CENTER);
        add(roomNameLabel, BorderLayout.NORTH);

        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        add(new JScrollPane(playerList), BorderLayout.CENTER);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

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
        chatArea.append(data.replaceFirst(":", ": ") + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public void clearChat() {
        chatArea.setText("");
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

// WrapLayout 그대로 (자동 줄바꿈)
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