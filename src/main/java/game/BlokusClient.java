package game;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BlokusClient extends JFrame {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ClientReceiver receiver;

    private String username;

    private CardLayout cardLayout;
    private JPanel mainPanel;
    private LoginScreen loginScreen;
    private LobbyScreen lobbyScreen;
    private RoomScreen roomScreen;
    private GameScreen gameScreen;

    private boolean handlingLoginFail = false;

    private static final String CONFIG_FILE = "src/main/resources/server.txt";

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
        try { if (in != null) in.close(); } catch (IOException e) {}
        try { if (out != null) out.close(); } catch (IOException e) {}
        try { if (socket != null) socket.close(); } catch (IOException e) {}
    }

    private String[] loadServerConfig() {
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            JOptionPane.showMessageDialog(this, CONFIG_FILE + " 파일을 찾을 수 없습니다.", "설정 오류", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String host = br.readLine();
            String portStr = br.readLine();

            if (host == null || host.trim().isEmpty() || portStr == null || portStr.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, CONFIG_FILE + " 파일 형식이 잘못되었습니다.\n첫 줄: IP, 둘째 줄: 포트", "설정 오류", JOptionPane.ERROR_MESSAGE);
                return null;
            }

            int port = Integer.parseInt(portStr.trim());
            return new String[]{host.trim(), String.valueOf(port)};

        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(this, CONFIG_FILE + " 파일을 찾을 수 없습니다.", "설정 오류", JOptionPane.ERROR_MESSAGE);
            return null;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, CONFIG_FILE + " 읽기 오류: " + e.getMessage(), "설정 오류", JOptionPane.ERROR_MESSAGE);
            return null;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "포트 번호가 잘못되었습니다: " + e.getMessage(), "설정 오류", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    public void attemptLogin(String username) {
        if (username.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "이름을 입력하세요.", "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }

        loginScreen.setLoginControlsEnabled(false, "서버 설정(server.txt) 읽는 중...");

        String[] config = loadServerConfig();
        if (config == null) {
            loginScreen.setLoginControlsEnabled(true, "설정 파일 오류.");
            return;
        }

        String host = config[0];
        int port = Integer.parseInt(config[1]);

        loginScreen.setLoginControlsEnabled(false, host + ":" + port + " 서버에 연결 시도 중...");

        SwingWorker<String, Void> loginWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                try {
                    connect(host, port);
                    sendMessage(Protocol.C2S_LOGIN + ":" + username);
                    return "LOGIN_ATTEMPTED";
                } catch (IOException e) {
                    return "CONNECT_FAILED:" + e.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String result = get();

                    if (result.startsWith("CONNECT_FAILED")) {
                        String errorMsg = result.split(":", 2)[1];
                        JOptionPane.showMessageDialog(BlokusClient.this, "서버 연결 실패: " + errorMsg, "연결 오류", JOptionPane.ERROR_MESSAGE);
                        loginScreen.setLoginControlsEnabled(true, " ");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(BlokusClient.this, "로그인 중 알 수 없는 오류: " + e.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
                    loginScreen.setLoginControlsEnabled(true, " ");
                }
            }
        };

        loginWorker.execute();
    }

    private void connect(String host, int port) throws IOException {
        cleanupConnection();
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
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

        if (loginScreen != null) {
            loginScreen.setLoginControlsEnabled(true, "서버 연결 끊김.");
        }
    }

    public void handleServerMessage(String message) {
        System.out.println("서버 (S2C): " + message);
        String[] parts = message.split(":", 2);
        String command = parts[0];
        String data = (parts.length > 1) ? parts[1] : null;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                switch (command) {
                    case Protocol.S2C_LOGIN_SUCCESS:
                        username = loginScreen.getUsername();
                        cardLayout.show(mainPanel, "LOBBY");
                        loginScreen.setLoginControlsEnabled(true, " ");
                        break;
                    case Protocol.S2C_LOGIN_FAIL:
                        handlingLoginFail = true;
                        JOptionPane.showMessageDialog(BlokusClient.this, "로그인 실패: " + data, "오류", JOptionPane.ERROR_MESSAGE);
                        loginScreen.setLoginControlsEnabled(true, "로그인 실패.");
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
                        JOptionPane.showMessageDialog(BlokusClient.this, "방 참여 실패: " + data, "오류", JOptionPane.ERROR_MESSAGE);
                        break;
                    case Protocol.S2C_ROOM_UPDATE:
                        roomScreen.updatePlayerList(data, username);
                        break;
                    case Protocol.S2C_KICKED:
                        JOptionPane.showMessageDialog(BlokusClient.this, "방에서 강퇴당했습니다.", "알림", JOptionPane.INFORMATION_MESSAGE);
                        cardLayout.show(mainPanel, "LOBBY");
                        sendMessage(Protocol.C2S_GET_LEADERBOARD);
                        break;
                    case Protocol.S2C_GAME_START:
                        gameScreen.initializeGame(data);
                        gameScreen.clearChat();
                        cardLayout.show(mainPanel, "GAME");
                        break;
                    case Protocol.S2C_GAME_START_PEERLESS:
                        gameScreen.initializePeerlessGame(data);
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
                        JOptionPane.showMessageDialog(BlokusClient.this, "잘못된 이동: " + data, "알림", JOptionPane.WARNING_MESSAGE);
                        break;

                    case Protocol.S2C_PEERLESS_PREP_START:
                        gameScreen.setPeerlessTimer("준비 시간: 20초 (첫 블록을 배치하세요)", Color.CYAN);
                        break;
                    case Protocol.S2C_PEERLESS_PREP_TIMER_UPDATE:
                        String[] prepData = data.split(":");
                        String time = prepData[0];
                        String phase = prepData[1];
                        if (phase.equals("PREP")) {
                            gameScreen.setPeerlessTimer("준비 시간: " + time + "초", Color.CYAN);
                        } else if (phase.equals("COUNTDOWN")) {
                            gameScreen.setPeerlessTimer("게임 시작 " + time + "초 전!", Color.ORANGE);
                        }
                        break;
                    case Protocol.S2C_PEERLESS_MAIN_START:
                        gameScreen.setPeerlessTimer("게임 시작!", Color.GREEN);
                        break;
                    case Protocol.S2C_PEERLESS_TIMER_UPDATE:
                        gameScreen.setPeerlessTimer("남은 시간: " + gameScreen.formatTime(Integer.parseInt(data)), Color.WHITE);
                        break;
                    case Protocol.S2C_PEERLESS_PLACE_SUCCESS:
                        String[] pieceData = data.split(":");
                        gameScreen.removePieceFromHand(pieceData[0], Integer.parseInt(pieceData[1]));
                        break;
                    case Protocol.S2C_PEERLESS_PLACE_FAIL:
                        JOptionPane.showMessageDialog(BlokusClient.this, "배치 실패: " + data, "알림", JOptionPane.WARNING_MESSAGE);
                        break;
                    case Protocol.S2C_PEERLESS_BOARD_UPDATE:
                        gameScreen.updateBoardState(data);
                        break;

                    case Protocol.S2C_GAME_OVER:
                        JOptionPane.showMessageDialog(BlokusClient.this, "게임 종료!\n" + data, "게임 종료", JOptionPane.INFORMATION_MESSAGE);
                        cardLayout.show(mainPanel, "LOBBY");
                        sendMessage(Protocol.C2S_GET_LEADERBOARD);
                        break;
                    case Protocol.S2C_CHAT:
                        roomScreen.appendChatMessage(data);
                        gameScreen.appendChatMessage(data);
                        break;
                    case Protocol.S2C_WHISPER:
                        roomScreen.appendChatMessage(data, true);
                        gameScreen.appendChatMessage(data, true);
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
            }
        });
    }

    public void sendMessage(String msg) {
        if (out != null) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (IOException e) {
                System.err.println("C2S Send Error: " + e.getMessage());
                handleConnectionLost();
            }
        }
    }

    public String getUsername() { return username; }

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new BlokusClient().setVisible(true);
            }
        });
    }
}

class ClientReceiver extends Thread {
    private final ObjectInputStream in;
    private final BlokusClient client;

    public ClientReceiver(ObjectInputStream in, BlokusClient client) {
        this.in = in;
        this.client = client;
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = (String) in.readObject()) != null) {
                client.handleServerMessage(message);
            }
        } catch (ClassNotFoundException e) {
            System.err.println("잘못된 객체 수신: " + e.getMessage());
        } catch (IOException e) {
        } finally {
            client.handleConnectionLost();
        }
    }
}

class LoginScreen extends JPanel {
    private final BlokusClient client;

    final JTextField usernameField;
    final JButton loginButton;
    final JLabel statusLabel;

    public LoginScreen(BlokusClient client) {
        this.client = client;

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("이름:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        usernameField = new JTextField(15);
        add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        loginButton = new JButton("접속");
        add(loginButton, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(statusLabel, gbc);

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.attemptLogin(usernameField.getText());
            }
        });

        usernameField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.attemptLogin(usernameField.getText());
            }
        });
    }

    public String getUsername() {
        return usernameField.getText();
    }

    public void setLoginControlsEnabled(boolean enabled, String status) {
        this.usernameField.setEnabled(enabled);
        this.loginButton.setEnabled(enabled);
        this.statusLabel.setText(status);
    }
}

class LobbyScreen extends JPanel {
    private final BlokusClient client;
    private final CardLayout cardLayout;

    private final JTable leaderboardTable;
    private final DefaultTableModel leaderboardModel;

    private final JList<String> roomList;
    private final DefaultListModel<String> roomListModel;

    private static final String LEADERBOARD_PANEL = "LEADERBOARD";
    private static final String ROOM_LIST_PANEL = "ROOM_LIST";

    public LobbyScreen(BlokusClient client) {
        this.client = client;
        cardLayout = new CardLayout();
        setLayout(cardLayout);

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

        JPanel leaderboardBottomPanel = new JPanel();

        JButton showRoomsButton = new JButton("방 목록 / 생성");
        showRoomsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showRoomList();
            }
        });

        JButton refreshLeaderboardButton = new JButton("점수 갱신");
        refreshLeaderboardButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.sendMessage(Protocol.C2S_GET_LEADERBOARD);
            }
        });

        leaderboardBottomPanel.add(showRoomsButton);
        leaderboardBottomPanel.add(refreshLeaderboardButton);
        leaderboardPanel.add(leaderboardBottomPanel, BorderLayout.SOUTH);

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

        JPanel roomListBottomPanel = new JPanel();

        JButton createButtonInRoomList = new JButton("방 만들기");
        createButtonInRoomList.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createRoom();
            }
        });

        JButton joinButton = new JButton("선택한 방 접속");
        joinButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                joinSelectedRoom();
            }
        });

        JButton backButton = new JButton("로비로 돌아가기");
        backButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cardLayout.show(LobbyScreen.this, LEADERBOARD_PANEL);
            }
        });

        JButton refreshRoomsButton = new JButton("방 새로고침");
        refreshRoomsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.sendMessage(Protocol.C2S_GET_ROOM_LIST);
            }
        });

        roomListBottomPanel.add(createButtonInRoomList);
        roomListBottomPanel.add(joinButton);
        roomListBottomPanel.add(refreshRoomsButton);
        roomListBottomPanel.add(backButton);
        roomListPanel.add(roomListBottomPanel, BorderLayout.SOUTH);

        this.add(leaderboardPanel, LEADERBOARD_PANEL);
        this.add(roomListPanel, ROOM_LIST_PANEL);

        cardLayout.show(this, LEADERBOARD_PANEL);
    }

    private void createRoom() {
        JPanel panel = new JPanel(new GridLayout(0, 1));
        JTextField roomNameField = new JTextField(15);
        JComboBox<String> modeComboBox = new JComboBox<>(new String[]{"클래식 (Classic)", "피어리스 (Peerless)"});

        panel.add(new JLabel("방 이름:"));
        panel.add(roomNameField);
        panel.add(new JLabel("게임 모드:"));
        panel.add(modeComboBox);

        int result = JOptionPane.showConfirmDialog(this, panel, "방 만들기", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String roomName = roomNameField.getText();
            String mode = (modeComboBox.getSelectedIndex() == 0) ? "CLASSIC" : "PEERLESS";

            if (roomName != null && !roomName.trim().isEmpty()) {
                client.sendMessage(Protocol.C2S_CREATE_ROOM + ":" + roomName + ":" + mode);
            } else {
                JOptionPane.showMessageDialog(this, "방 이름을 입력해야 합니다.", "오류", JOptionPane.ERROR_MESSAGE);
            }
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
            if (parts.length >= 4) {
                String modeDisplay = parts[3].equalsIgnoreCase("PEERLESS") ? "피어리스" : "클래식";
                roomListModel.addElement(String.format("[ID:%s] %s (%s) - %s", parts[0], parts[1], parts[2], modeDisplay));
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
    private Style styleWhisper;

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

        styleWhisper = systemArea.addStyle("Whisper", styleDefault);
        StyleConstants.setForeground(styleWhisper, Color.MAGENTA);
        StyleConstants.setItalic(styleWhisper, true);

        chatTabs.addTab("시스템", new JScrollPane(systemArea));

        chatPanel.add(chatTabs, BorderLayout.CENTER);


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
                client.sendMessage(Protocol.C2S_START_GAME);
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
                    client.sendMessage(Protocol.C2S_KICK_PLAYER + ":" + targetUser);
                }
            }
        });
        bottomPanel.add(kickButton);

        JButton leaveButton = new JButton("방 나가기");
        leaveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.sendMessage(Protocol.C2S_LEAVE_ROOM);
            }
        });
        bottomPanel.add(leaveButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void sendChat() {
        String message = chatField.getText();
        if (message.trim().isEmpty()) {
            return;
        }

        if (message.startsWith("/")) {
            String[] parts = message.split(" ", 3);
            String command = parts[0].toLowerCase();

            if (command.equals("/r")) {
                if (parts.length == 3 && !parts[1].trim().isEmpty() && !parts[2].trim().isEmpty()) {
                    client.sendMessage(Protocol.C2S_WHISPER + ":" + parts[1] + ":" + parts[2]);
                } else if (parts.length < 2) {
                    appendChatMessage("[시스템]: 귓속말 사용법: /r [닉네임] [메세지]");
                } else if (parts.length == 2) {
                    appendChatMessage("[시스템]: 귓속말할 메세지를 입력해야 합니다.");
                } else {
                    appendChatMessage("[시스템]: 귓속말 사용법: /r [닉네임] [메세지]");
                }
            } else {
                appendChatMessage("[시스템]: 알 수 없는 명령어입니다. (사용 가능: /r)");
            }
        } else {
            client.sendMessage(Protocol.C2S_CHAT + ":" + message);
        }
        chatField.setText("");
    }

    public void appendChatMessage(String data) {
        appendChatMessage(data, false);
    }

    public void appendChatMessage(String data, boolean isWhisper) {
        String message = data.replaceFirst(":", ": ");
        StyledDocument doc = systemArea.getStyledDocument();

        try {
            if (isWhisper) {
                doc.insertString(doc.getLength(), message + "\n", styleWhisper);
                systemArea.setCaretPosition(systemArea.getDocument().getLength());
                chatTabs.setSelectedComponent(systemArea.getParent().getParent());
            } else if (data.startsWith("[시스템]:") || data.startsWith(Protocol.S2C_SYSTEM_MSG)) {
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
                systemArea.setCaretPosition(systemArea.getDocument().getLength());
                chatTabs.setSelectedComponent(systemArea.getParent().getParent());

            } else {
                chatArea.append(message + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
                chatTabs.setSelectedComponent(chatArea.getParent().getParent());
            }
        } catch (Exception e) {
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