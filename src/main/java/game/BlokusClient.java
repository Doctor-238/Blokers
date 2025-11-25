package game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*; // 이벤트 리스너
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class BlokusClient extends JFrame {

    //서버 주소 및 포트 번호
    private String serverHost = "localhost";
    private int serverPort = 12345;

    // 서버와 송수신할 소켓 및 스트림,
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // 서버 명령 읽기 전용 스레드
    private ClientReceiver receiver;

    //유저 이름
    private String username;

    //GUI 구조
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private LoginScreen loginScreen;
    private LobbyScreen lobbyScreen;
    private RoomScreen roomScreen;
    private GameScreen gameScreen;

    public BlokusClient() {
        setTitle("블로커스 (Blokus)");
        // GUI는 단순히 setSize만 사용
        setSize(800, 830);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

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

    //로그인 로직
    public void attemptLogin(String username) {
        //만약 유저 이름이 비어있다면
        if (username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "이름을 입력하세요.", "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            socket = new Socket(serverHost, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            receiver = new ClientReceiver(in, this);
            receiver.start();

            sendMessage("LOGIN" + ":" + username);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결에 실패했습니다: " + e.getMessage(), "연결 오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    //연결 해제 시
    public void handleConnectionLost() {
        JOptionPane.showMessageDialog(this, "서버와의 연결이 끊겼습니다. 로그인 화면으로 돌아갑니다.", "연결 오류", JOptionPane.ERROR_MESSAGE);

        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {}

        this.username = null;
        cardLayout.show(mainPanel, "LOGIN");
    }

    public void handleServerMessage(String message) {
        System.out.println("서버 (S2C): " + message);
        //메세지 파싱
        String[] parts = message.split(":", 2);

        //명령어
        String command = parts[0];
        //데이터
        String data = (parts.length > 1) ? parts[1] : null;

        switch (command) {
            case "LOGIN_SUCCESS":
                this.username = loginScreen.getUsername();
                cardLayout.show(mainPanel, "LOBBY");
                sendMessage("GET_ROOM_LIST");
                break;
            case "LOGIN_FAIL":
                JOptionPane.showMessageDialog(this, "로그인 실패: " + data, "오류", JOptionPane.ERROR_MESSAGE);
                handleConnectionLost();
                break;
            case "ROOM_LIST":
                lobbyScreen.updateRoomList(data);
                break;
            case "JOIN_SUCCESS":
                roomScreen.setRoomName(data.split(":")[1]);
                roomScreen.clearChat();
                cardLayout.show(mainPanel, "ROOM");
                break;
            case "JOIN_FAIL":
                JOptionPane.showMessageDialog(this, "방 참여 실패: " + data, "오류", JOptionPane.ERROR_MESSAGE);
                break;
            case "ROOM_UPDATE":
                roomScreen.updatePlayerList(data, username);
                break;
            case "KICKED":
                JOptionPane.showMessageDialog(this, "방에서 강퇴당했습니다.", "알림", JOptionPane.INFORMATION_MESSAGE);
                cardLayout.show(mainPanel, "LOBBY");
                sendMessage("GET_ROOM_LIST");
                break;
            case "GAME_START":
                gameScreen.initializeGame(data);
                gameScreen.clearChat();
                cardLayout.show(mainPanel, "GAME");
                break;
            case "GAME_STATE":
                gameScreen.updateGameState(data);
                break;
            case "HAND_UPDATE":
                gameScreen.updatePlayerHand(data);
                break;
            case "TIME_UPDATE":
                gameScreen.updateTimer(data);
                break;
            case "INVALID_MOVE":
                JOptionPane.showMessageDialog(this, "잘못된 이동: " + data, "알림", JOptionPane.WARNING_MESSAGE);
                break;
            case "GAME_OVER":
                JOptionPane.showMessageDialog(this, "게임 종료!\n" + data, "게임 종료", JOptionPane.INFORMATION_MESSAGE);
                cardLayout.show(mainPanel, "LOBBY");
                sendMessage("GET_ROOM_LIST");
                break;
            case "CHAT":
                roomScreen.appendChatMessage(data);
                gameScreen.appendChatMessage(data);
                break;
            case "SYSTEM_MSG":
                if (data != null && data.contains("로비로 이동합니다.")) {
                    cardLayout.show(mainPanel, "LOBBY");
                    sendMessage("GET_ROOM_LIST");
                } else {
                    String sysMsg = "[시스템]:" + data;
                    roomScreen.appendChatMessage(sysMsg);
                    gameScreen.appendChatMessage(sysMsg);
                }
                break;
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public String getUsername() { return this.username; }

    public static void main(String[] args) {
        new BlokusClient().setVisible(true);
    }
}

class ClientReceiver extends Thread {
    private BufferedReader in;
    private BlokusClient client;

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

//로그인 UI
class LoginScreen extends JPanel {
    private BlokusClient client;
    private JTextField usernameField;

    public LoginScreen(BlokusClient client) {
        this.client = client;
        setLayout(new FlowLayout());
        add(new JLabel("이름:"));
        usernameField = new JTextField(15);
        add(usernameField);
        JButton loginButton = new JButton("로그인/접속");

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.attemptLogin(usernameField.getText());
            }
        });
        add(loginButton);
    }

    public String getUsername() {
        return usernameField.getText();
    }
}

//로비 UI
class LobbyScreen extends JPanel {
    private BlokusClient client;
    private JList<String> roomList;
    private DefaultListModel<String> roomListModel;

    public LobbyScreen(BlokusClient client) {
        this.client = client;
        setLayout(new BorderLayout());

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        add(new JScrollPane(roomList), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        JTextField roomNameField = new JTextField(15);
        bottomPanel.add(new JLabel("방 이름:"));
        bottomPanel.add(roomNameField);
        JButton createButton = new JButton("방 만들기");
        createButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String roomName = roomNameField.getText();
                if (!roomName.trim().isEmpty()) {
                    client.sendMessage("CREATE_ROOM" + ":" + roomName);
                }
            }
        });
        bottomPanel.add(createButton);

        JButton joinButton = new JButton("방 들어가기");
        joinButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = roomList.getSelectedValue();
                if (selected != null && selected.startsWith("[ID:")) {
                    try {
                        String roomId = selected.split("]")[0].split(":")[1].trim();
                        client.sendMessage("JOIN_ROOM" + ":" + roomId);
                    } catch (Exception ex) {
                        System.err.println("잘못된 방 선택: " + selected);
                    }
                }
            }
        });
        bottomPanel.add(joinButton);

        JButton refreshButton = new JButton("새로고침");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.sendMessage("GET_ROOM_LIST");
            }
        });
        bottomPanel.add(refreshButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void updateRoomList(String data) {
        roomListModel.clear();
        if (data == null || data.isEmpty()) {
            roomListModel.addElement("생성된 방이 없습니다.");
            return;
        }
        String[] rooms = data.split(";");
        for (String room : rooms) {
            String roomInfo = room.substring(1, room.length() - 1);
            String[] parts = roomInfo.split(",");
            if (parts.length >= 3) {
                roomListModel.addElement(String.format("[ID:%s] %s (%s)", parts[0], parts[1], parts[2]));
            }
        }
    }
}

class RoomScreen extends JPanel {
    private BlokusClient client;
    private JLabel roomNameLabel;
    private JList<String> playerList;
    private DefaultListModel<String> playerListModel;
    private JButton startButton;
    private JButton kickButton;

    private JTextArea chatArea;
    private JTextField chatField;

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
        chatField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendChat();
            }
        });
        JButton sendButton = new JButton("전송");
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendChat();
            }
        });
        chatInputPanel.add(chatField, BorderLayout.CENTER);
        chatInputPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

        chatPanel.setPreferredSize(new Dimension(250, 0));
        add(chatPanel, BorderLayout.WEST);


        JPanel bottomPanel = new JPanel();
        startButton = new JButton("게임 시작");
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Protocol.C2S_START_GAME
                client.sendMessage("START_GAME");
            }
        });
        bottomPanel.add(startButton);

        kickButton = new JButton("강퇴하기");
        kickButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = playerList.getSelectedValue();
                if (selected != null) {
                    String targetUser = selected.split(" ")[0];
                    // Protocol.C2S_KICK_PLAYER
                    client.sendMessage("KICK" + ":" + targetUser);
                }
            }
        });
        bottomPanel.add(kickButton);

        JButton leaveButton = new JButton("방 나가기");
        leaveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Protocol.C2S_LEAVE_ROOM
                client.sendMessage("LEAVE_ROOM");
            }
        });
        bottomPanel.add(leaveButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void sendChat() {
        String message = chatField.getText();
        if (!message.trim().isEmpty()) {
            // Protocol.C2S_CHAT
            client.sendMessage("CHAT" + ":" + message);
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

// 자동 줄바꿈을 위한 WrapLayout (GUI 헬퍼, 이벤트랑 무관)
class WrapLayout extends FlowLayout {
    private Dimension preferredLayoutSize;

    public WrapLayout() {
        super();
    }

    public WrapLayout(int align) {
        super(align);
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

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

            // 스크롤 패널의 뷰포트 너비 사용
            int targetWidth = target.getSize().width;
            Container scrollPaneContainer = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            JScrollPane scrollPane = null;
            if (scrollPaneContainer != null) {
                scrollPane = (JScrollPane) scrollPaneContainer;
                targetWidth = scrollPane.getViewport().getSize().width;
            }

            if (targetWidth == 0)
                targetWidth = Integer.MAX_VALUE;

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
                    if (rowWidth != 0) {
                        rowWidth += hgap;
                    }
                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }
            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;

            if (scrollPane != null) {
                dim.width = targetWidth;
            }

            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) {
            dim.height += getVgap();
        }
        dim.height += rowHeight;
    }
}
