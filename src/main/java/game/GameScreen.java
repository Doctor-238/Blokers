package game;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class GameScreen extends JPanel {
    private BlokusClient client;

    private static final int BOARD_SIZE = 20;
    private static final int CELL_SIZE = 25;
    private static final int BOARD_PANEL_SIZE = BOARD_SIZE * CELL_SIZE;

    private final Color DARK_YELLOW = new Color(204, 153, 0);

    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private List<BlokusPiece> myHand = new ArrayList<>();
    private Map<String, PiecePreviewPanel> handPanelCache = new HashMap<>();

    private BlokusPiece selectedPiece = null;
    private PiecePreviewPanel selectedPanel = null;
    private int currentRotation = 0;

    private int[] myColors = new int[0];
    private int inventoryDisplayColor = 0;
    private int currentTurnColor = 0;

    private Set<Integer> myActiveColors = new HashSet<>();
    private Set<Integer> resignedColors = new HashSet<>();

    private Point mouseGridPos = new Point(-1, -1);
    private boolean isGhostValid = false;
    private boolean amISpectating = false;

    private boolean isPeerlessMode = false;
    private boolean isGameFinished = false;

    private JPanel boardPanel;
    private JPanel handPanel;
    private JScrollPane handScrollPane;

    // Top Bar Components
    private StatusPanel statusPanel; // Custom panel for names & colors
    private JLabel centerLabel; // Center label for Total Time (Classic) or Status (Peerless)
    private String[] playerNames = new String[0]; // Stores names of all players in order

    private JButton toggleColorButton;
    private JButton deselectButton;
    private JButton rotateButton;
    private JButton passButton;
    private JButton resignButton;

    private JLabel scoreLabel;

    // Individual timer values (seconds)
    private int[] remainingTimes = {300, 300, 300, 300};

    // Total Game Time Logic
    private Timer totalGameTimer;
    private int totalSecondsElapsed = 0;

    // 채팅 관련 필드
    private JLayeredPane chatPanel;
    private JPanel chatContentPanel;
    private JButton chatFoldButton;
    private boolean isChatExpanded = true;
    private static final int CHAT_EXPANDED_WIDTH = 220;
    private static final int CHAT_COLLAPSED_WIDTH = 30;

    private JTabbedPane chatTabs;
    private JTextPane chatAreaPane;
    private JTextPane systemArea;
    private JTextField chatField;

    private Style styleDefault;
    private Style styleRed;
    private Style styleBlue;
    private Style styleYellow;
    private Style styleGreen;
    private Style styleWhisper;

    private Style styleChatDefault;
    private Style styleChatWhisper;

    private MouseAdapter backgroundClickListener = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getSource() == GameScreen.this || e.getSource() == boardPanel.getParent() || e.getSource() == handScrollPane.getParent()) {
                deselectPiece();
            }
        }
    };

    public GameScreen(BlokusClient client) {
        this.client = client;

        this.enableInputMethods(false);

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        addMouseListener(backgroundClickListener);

        // --- TOP PANEL ---
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setPreferredSize(new Dimension(0, 60)); // Fixed height
        topPanel.addMouseListener(backgroundClickListener);

        // Left: Status Panel (Names, Colors, Timers)
        statusPanel = new StatusPanel();
        statusPanel.setPreferredSize(new Dimension(250, 60));
        topPanel.add(statusPanel, BorderLayout.WEST);

        // Center: Total Time or Game Status
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerLabel = new JLabel("00:00");
        centerLabel.setFont(new Font("맑은 고딕", Font.BOLD, 36));
        centerPanel.add(centerLabel);
        topPanel.add(centerPanel, BorderLayout.CENTER);

        // Right: Controls
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 15));
        controlPanel.addMouseListener(backgroundClickListener);

        rotateButton = new JButton("회전 (r)");
        rotateButton.addActionListener(e -> rotateSelectedPiece());

        passButton = new JButton("턴 넘기기");
        passButton.addActionListener(e -> {
            client.sendMessage(Protocol.C2S_PASS_TURN);
            deselectPiece();
        });

        resignButton = new JButton("점수 확정 (Esc)");
        resignButton.addActionListener(e -> handleResign());

        controlPanel.add(rotateButton);
        controlPanel.add(passButton);
        controlPanel.add(resignButton);
        topPanel.add(controlPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // --- BOARD ---
        boardPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawBoard(g);
                drawGhostPiece(g);
            }
        };
        boardPanel.setPreferredSize(new Dimension(BOARD_PANEL_SIZE, BOARD_PANEL_SIZE));
        boardPanel.setBackground(Color.LIGHT_GRAY);
        boardPanel.enableInputMethods(false);

        addMouseListeners();
        add(boardPanel, BorderLayout.CENTER);

        // --- SOUTH PANEL ---
        JPanel southPanel = new JPanel(new BorderLayout(5, 5));
        southPanel.addMouseListener(backgroundClickListener);

        JPanel southTopPanel = new JPanel(new BorderLayout(10, 5));

        JPanel southWestButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        southWestButtons.setOpaque(false);

        toggleColorButton = new JButton("블록 전환 (e)");
        toggleColorButton.setVisible(false);
        toggleColorButton.addActionListener(e -> toggleInventoryColor());
        southWestButtons.add(toggleColorButton);

        deselectButton = new JButton("선택 취소 (q)");
        deselectButton.addActionListener(e -> deselectPiece());
        southWestButtons.add(deselectButton);

        southTopPanel.add(southWestButtons, BorderLayout.WEST);

        scoreLabel = new JLabel("남은 점수: 89");
        scoreLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        scoreLabel.setHorizontalAlignment(SwingConstants.CENTER);
        southTopPanel.add(scoreLabel, BorderLayout.CENTER);

        southPanel.add(southTopPanel, BorderLayout.NORTH);

        handPanel = new JPanel(new WrapLayout(WrapLayout.LEFT, 5, 5));
        handPanel.setBackground(Color.WHITE);
        handPanel.addMouseListener(backgroundClickListener);
        handPanel.enableInputMethods(false);

        handScrollPane = new JScrollPane(handPanel);
        handScrollPane.setPreferredSize(new Dimension(800, 160));
        handScrollPane.addMouseListener(backgroundClickListener);

        southPanel.add(handScrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        // --- CHAT PANEL ---
        setupChatPanel();

        // Buttons focus
        rotateButton.setFocusable(false);
        passButton.setFocusable(false);
        resignButton.setFocusable(false);
        toggleColorButton.setFocusable(false);
        deselectButton.setFocusable(false);

        setupKeyBindings();
    }

    private void setupChatPanel() {
        chatPanel = new JLayeredPane();
        chatPanel.setPreferredSize(new Dimension(CHAT_EXPANDED_WIDTH, 0));

        chatContentPanel = new JPanel(new BorderLayout());
        chatTabs = new JTabbedPane();

        chatAreaPane = new JTextPane();
        chatAreaPane.setEditable(false);
        StyledDocument chatDoc = chatAreaPane.getStyledDocument();
        styleChatDefault = chatAreaPane.addStyle("ChatDefault", null);
        StyleConstants.setForeground(styleChatDefault, Color.BLACK);
        StyleConstants.setFontFamily(styleChatDefault, "맑은 고딕");
        StyleConstants.setFontSize(styleChatDefault, 12);

        styleChatWhisper = chatAreaPane.addStyle("ChatWhisper", styleChatDefault);
        StyleConstants.setForeground(styleChatWhisper, Color.MAGENTA);
        StyleConstants.setItalic(styleChatWhisper, true);

        chatTabs.addTab("채팅", new JScrollPane(chatAreaPane));

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

        chatContentPanel.add(chatTabs, BorderLayout.CENTER);

        JPanel chatInputPanel = new JPanel(new BorderLayout());
        chatField = new JTextField();
        chatField.addActionListener(e -> sendChat());
        JButton sendButton = new JButton("전송");
        sendButton.addActionListener(e -> sendChat());
        chatInputPanel.add(chatField, BorderLayout.CENTER);
        chatInputPanel.add(sendButton, BorderLayout.EAST);
        chatContentPanel.add(chatInputPanel, BorderLayout.SOUTH);

        chatFoldButton = new JButton("◀");
        chatFoldButton.setFont(new Font("Dialog", Font.BOLD, 10));
        chatFoldButton.setMargin(new Insets(0, 0, 0, 0));
        chatFoldButton.setFocusable(false);
        chatFoldButton.addActionListener(e -> toggleChat());

        chatPanel.add(chatContentPanel, JLayeredPane.DEFAULT_LAYER);
        chatPanel.add(chatFoldButton, JLayeredPane.PALETTE_LAYER);

        chatPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = chatPanel.getWidth();
                int h = chatPanel.getHeight();
                int btnSize = 22;

                if (isChatExpanded) {
                    chatContentPanel.setVisible(true);
                    chatContentPanel.setBounds(0, 0, w, h);
                    chatFoldButton.setBounds(w - btnSize - 2, 2, btnSize, btnSize);
                    chatFoldButton.setText("◀");
                } else {
                    chatContentPanel.setVisible(false);
                    chatFoldButton.setBounds(2, 2, w - 4, btnSize);
                    chatFoldButton.setText("▶");
                }
            }
        });

        add(chatPanel, BorderLayout.WEST);
    }

    // Status Panel for Names, Colors, Timers
    private class StatusPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (playerNames == null || playerNames.length == 0) return;

            int startX = 10;
            int startY = 5;
            int blockWidth = 25;
            int blockHeight = 25;

            g2d.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
            FontMetrics fm = g2d.getFontMetrics();

            boolean isTwoPlayer = (playerNames.length == 2);
            int currentX = startX;

            for (int i = 0; i < playerNames.length; i++) {
                String name = playerNames[i];
                int[] colorsForPlayer;
                if (isTwoPlayer) {
                    // P1: 1,3. P2: 2,4
                    if (i == 0) colorsForPlayer = new int[]{1, 3}; // Red, Yellow
                    else colorsForPlayer = new int[]{2, 4}; // Blue, Green
                } else {
                    colorsForPlayer = new int[]{i + 1};
                }

                int groupWidth = colorsForPlayer.length * blockWidth + (colorsForPlayer.length - 1) * 5;
                int nameWidth = fm.stringWidth(name);
                int drawNameX = currentX + (groupWidth - nameWidth) / 2;
                if (drawNameX < currentX) drawNameX = currentX;

                g2d.setColor(Color.BLACK);
                g2d.drawString(name, drawNameX, startY + 12);

                int blockX = currentX;
                for (int colorCode : colorsForPlayer) {
                    int drawY = startY + 18;
                    boolean isTurn = (currentTurnColor == colorCode);
                    boolean isResigned = resignedColors.contains(colorCode);

                    Color playerColor = getColorForPlayer(colorCode);

                    // Color Box (Fill with transparency if not turn or resigned)
                    if (!isTurn && !isResigned) {
                        g2d.setColor(new Color(playerColor.getRed(), playerColor.getGreen(), playerColor.getBlue(), 100));
                    } else if (isResigned) {
                        g2d.setColor(new Color(playerColor.getRed(), playerColor.getGreen(), playerColor.getBlue(), 50));
                    } else {
                        g2d.setColor(playerColor);
                    }
                    g2d.fillRect(blockX, drawY, blockWidth, blockHeight);

                    // Color Box Border (Always sharp)
                    g2d.setColor(Color.BLACK);
                    g2d.drawRect(blockX, drawY, blockWidth, blockHeight);

                    // Timer Text
                    String timeStr = formatTime(remainingTimes[colorCode - 1]);

                    // Draw Timer (Dark Gray if not turn or resigned)
                    g2d.setFont(new Font("맑은 고딕", Font.BOLD, 11));
                    if (!isTurn || isResigned) {
                        g2d.setColor(Color.DARK_GRAY);
                    } else {
                        g2d.setColor(playerColor);
                        if (colorCode == 3) g2d.setColor(DARK_YELLOW);
                    }
                    g2d.drawString(timeStr, blockX - 2, drawY + blockHeight + 12);

                    // Resigned X Mark
                    if (isResigned) {
                        g2d.setColor(Color.BLACK);
                        g2d.setStroke(new BasicStroke(2));
                        g2d.drawLine(blockX, drawY, blockX + blockWidth, drawY + blockHeight);
                        g2d.drawLine(blockX + blockWidth, drawY, blockX, drawY + blockHeight);
                    }

                    blockX += blockWidth + 5;
                }

                int spaceUsed = Math.max(groupWidth, nameWidth) + 20;
                currentX += spaceUsed;
            }
        }
    }

    private void toggleChat() {
        isChatExpanded = !isChatExpanded;
        if (isChatExpanded) {
            chatPanel.setPreferredSize(new Dimension(CHAT_EXPANDED_WIDTH, 0));
        } else {
            chatPanel.setPreferredSize(new Dimension(CHAT_COLLAPSED_WIDTH, 0));
        }
        revalidate();
        repaint();
    }

    private void setupKeyBindings() {
        InputMap im = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = this.getActionMap();

        im.put(KeyStroke.getKeyStroke('r'), "rotateAction");
        im.put(KeyStroke.getKeyStroke('R'), "rotateAction");
        im.put(KeyStroke.getKeyStroke('ㄱ'), "rotateAction");

        am.put("rotateAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatField.isFocusOwner()) {
                    rotateSelectedPiece();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke('e'), "toggleAction");
        im.put(KeyStroke.getKeyStroke('E'), "toggleAction");
        im.put(KeyStroke.getKeyStroke('ㄷ'), "toggleAction");
        am.put("toggleAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatField.isFocusOwner()) {
                    toggleInventoryColor();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke('q'), "deselectAction");
        im.put(KeyStroke.getKeyStroke('Q'), "deselectAction");
        im.put(KeyStroke.getKeyStroke('ㅂ'), "deselectAction");
        am.put("deselectAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatField.isFocusOwner()) {
                    deselectPiece();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke('c'), "memoAction");
        im.put(KeyStroke.getKeyStroke('C'), "memoAction");
        im.put(KeyStroke.getKeyStroke('ㅊ'), "memoAction");
        am.put("memoAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatField.isFocusOwner() && selectedPanel != null) {
                    selectedPanel.toggleMemo();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "placeAction");
        am.put("placeAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatField.isFocusOwner()) {
                    attemptPlaceBlock();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "resignAction");
        am.put("resignAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!chatField.isFocusOwner()) {
                    handleResign();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke('w'), "navigateUp");
        im.put(KeyStroke.getKeyStroke('W'), "navigateUp");
        im.put(KeyStroke.getKeyStroke('ㅈ'), "navigateUp");

        im.put(KeyStroke.getKeyStroke('a'), "navigateLeft");
        im.put(KeyStroke.getKeyStroke('A'), "navigateLeft");
        im.put(KeyStroke.getKeyStroke('ㅁ'), "navigateLeft");

        im.put(KeyStroke.getKeyStroke('s'), "navigateDown");
        im.put(KeyStroke.getKeyStroke('S'), "navigateDown");
        im.put(KeyStroke.getKeyStroke('ㄴ'), "navigateDown");

        im.put(KeyStroke.getKeyStroke('d'), "navigateRight");
        im.put(KeyStroke.getKeyStroke('D'), "navigateRight");
        im.put(KeyStroke.getKeyStroke('ㅇ'), "navigateRight");

        am.put("navigateUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleNavigate("up");
            }
        });
        am.put("navigateLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleNavigate("left");
            }
        });
        am.put("navigateDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleNavigate("down");
            }
        });
        am.put("navigateRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleNavigate("right");
            }
        });
    }

    public void initializeGame(String data) {
        this.isPeerlessMode = false;
        this.isGameFinished = false;
        resignButton.setText("점수 확정 (Esc)");
        passButton.setVisible(true);

        centerLabel.setVisible(true);
        scoreLabel.setVisible(true);

        String[] parts = data.split(":");
        String[] colorsStr = parts[1].split(",");

        if (parts.length >= 3) {
            this.playerNames = parts[2].split(",");
        }

        myColors = new int[colorsStr.length];
        myActiveColors.clear();
        resignedColors.clear();

        for (int i = 0; i < colorsStr.length; i++) {
            myColors[i] = Integer.parseInt(colorsStr[i]);
            myActiveColors.add(myColors[i]);
        }

        int startScore = (myColors.length == 2) ? 178 : 89;
        scoreLabel.setText("남은 점수: " + startScore);

        toggleColorButton.setVisible(myColors.length > 1);
        inventoryDisplayColor = (myColors.length > 0) ? myColors[0] : 0;

        board = new int[BOARD_SIZE][BOARD_SIZE];
        myHand.clear();
        handPanelCache.clear();
        handPanel.removeAll();
        deselectPiece();

        for (int i = 0; i < 4; i++) {
            remainingTimes[i] = 300;
        }
        statusPanel.repaint();

        setSpectateMode(false);
        startTotalGameTimer();
    }

    public void initializePeerlessGame(String data) {
        this.isPeerlessMode = true;
        this.isGameFinished = false;
        resignButton.setText("점수 확정 (Esc)");
        passButton.setVisible(false);

        centerLabel.setVisible(true);
        scoreLabel.setVisible(true);

        String[] parts = data.split(":");
        String[] colorsStr = parts[1].split(",");

        if (parts.length >= 3) {
            this.playerNames = parts[2].split(",");
        }

        myColors = new int[colorsStr.length];
        myActiveColors.clear();
        resignedColors.clear();

        for (int i = 0; i < colorsStr.length; i++) {
            myColors[i] = Integer.parseInt(colorsStr[i]);
            myActiveColors.add(myColors[i]);
        }

        int startScore = (myColors.length == 2) ? 178 : 89;
        scoreLabel.setText("남은 점수: " + startScore);

        toggleColorButton.setVisible(myColors.length > 1);
        inventoryDisplayColor = (myColors.length > 0) ? myColors[0] : 0;

        board = new int[BOARD_SIZE][BOARD_SIZE];
        myHand.clear();
        handPanelCache.clear();
        handPanel.removeAll();
        deselectPiece();

        setSpectateMode(false);
    }

    private void startTotalGameTimer() {
        if (totalGameTimer != null) totalGameTimer.cancel();
        totalSecondsElapsed = 0;
        centerLabel.setText("00:00");
        centerLabel.setForeground(Color.BLACK);

        totalGameTimer = new Timer();
        totalGameTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isGameFinished) {
                    this.cancel();
                    return;
                }
                totalSecondsElapsed++;
                SwingUtilities.invokeLater(() -> centerLabel.setText(formatTime(totalSecondsElapsed)));
            }
        }, 1000, 1000);
    }

    public void setGameFinished(boolean finished) {
        this.isGameFinished = finished;
        if (totalGameTimer != null) totalGameTimer.cancel();
    }


    private void setSpectateMode(boolean spectating) {
        this.amISpectating = spectating;

        if (spectating) {
            deselectPiece();
        }

        updateButtonStates();
        handPanel.repaint();
    }


    private void handleResign() {
        if (amISpectating) {
            client.sendMessage(Protocol.C2S_LEAVE_ROOM);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "정말 점수를 확정하시겠습니까?\n이 색상으로는 더 이상 블록을 놓을 수 없습니다.", "점수 확정 확인", JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            if (isPeerlessMode) {
                client.sendMessage(Protocol.C2S_RESIGN_PEERLESS);
                setSpectateMode(true);
            } else {
                int colorToResign = currentTurnColor;
                client.sendMessage(Protocol.C2S_RESIGN_COLOR + ":" + colorToResign);

                myActiveColors.remove(colorToResign);
                if (myActiveColors.isEmpty() && !amISpectating) {
                    setSpectateMode(true);
                }
            }
        }
    }

    public void updateGameState(String data) {
        if (isPeerlessMode) return;
        if (data == null) return;
        String[] parts = data.split(":");
        if (parts.length < 3) return;

        int oldTurnColor = this.currentTurnColor;

        String boardData = parts[0];
        int newTurnColor = Integer.parseInt(parts[2]);

        String[] cells = boardData.split(",");
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c] = Integer.parseInt(cells[r * BOARD_SIZE + c]);
            }
        }

        this.currentTurnColor = newTurnColor;

        statusPanel.repaint();

        boolean myTurn = false;
        for (int c : myColors) {
            if (c == newTurnColor) {
                myTurn = true;
                break;
            }
        }

        boolean inventoryFilterChanged = false;
        if (myTurn && this.inventoryDisplayColor != newTurnColor) {
            this.inventoryDisplayColor = newTurnColor;
            inventoryFilterChanged = true;
        }

        boolean oldTurnColorCheck = (oldTurnColor != newTurnColor);
        if (inventoryFilterChanged) {
            updateHandPanelUI();
        } else if (oldTurnColorCheck) {
            handPanel.repaint();
        }

        boardPanel.repaint();

        updateButtonStates();
    }

    private void updateButtonStates() {
        if (amISpectating) {
            passButton.setEnabled(false);
            resignButton.setText("로비로 나가기");
            resignButton.setEnabled(true);
            rotateButton.setEnabled(true);
            toggleColorButton.setEnabled(myColors.length > 1);
            toggleColorButton.setVisible(myColors.length > 1);
        } else {
            resignButton.setText("점수 확정 (Esc)");
            rotateButton.setEnabled(true);
            toggleColorButton.setEnabled(myColors.length > 1);
            toggleColorButton.setVisible(myColors.length > 1);

            if (isPeerlessMode) {
                passButton.setEnabled(false);
                resignButton.setEnabled(true);
            } else {
                boolean myTurn = isMyTurn();
                passButton.setEnabled(myTurn);
                resignButton.setEnabled(myTurn);
            }
        }
    }

    private void calculateAndUpdateScore() {
        int totalScore = 0;
        if (myColors.length == 2) {
            int scoreColor1 = 0;
            int scoreColor2 = 0;
            for (BlokusPiece piece : myHand) {
                if (piece.getColor() == myColors[0]) {
                    scoreColor1 += piece.getSize();
                } else if (piece.getColor() == myColors[1]) {
                    scoreColor2 += piece.getSize();
                }
            }
            totalScore = scoreColor1 + scoreColor2;
            String color1Name = getColorNameForPlayer(myColors[0]);
            String color2Name = getColorNameForPlayer(myColors[1]);
            scoreLabel.setText(String.format("남은 점수: %d (%s %d, %s %d)",
                    totalScore, color1Name, scoreColor1, color2Name, scoreColor2));
        } else {
            for (BlokusPiece piece : myHand) {
                totalScore += piece.getSize();
            }
            scoreLabel.setText("남은 점수: " + totalScore);
        }
    }

    public void updatePlayerHand(String data) {
        myHand.clear();

        if (data != null && !data.isEmpty()) {
            String[] pieces = data.split(",");
            for (String p : pieces) {
                String[] pieceData = p.split("/");
                if (pieceData.length != 2) continue;
                String id = pieceData[0];
                int color = Integer.parseInt(pieceData[1]);
                BlokusPiece newPiece = new BlokusPiece(id, color);
                myHand.add(newPiece);
            }
        }

        calculateAndUpdateScore();
        updateHandPanelUI();
    }

    private String getColorNameForPlayer(int playerColor) {
        switch (playerColor) {
            case 1: return "빨강";
            case 2: return "파랑";
            case 3: return "노랑";
            case 4: return "초록";
            default: return "?";
        }
    }

    public void updateTimer(String data) {
        if (isPeerlessMode) return;
        if (data == null) return;
        String[] times = data.split(",");
        if (times.length == 4) {
            for (int i = 0; i < 4; i++) {
                remainingTimes[i] = Integer.parseInt(times[i]);
            }
            statusPanel.repaint();
        }
    }

    private void updateTimerVisibility() {
    }

    public String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private void updateHandPanelUI() {
        handPanel.removeAll();

        Map<String, Boolean> pieceInHand = new HashMap<>();
        for (BlokusPiece piece : myHand) {
            String key = piece.getId() + "/" + piece.getColor();
            pieceInHand.put(key, true);
        }

        handPanelCache.keySet().removeIf(key -> !pieceInHand.containsKey(key));

        for (BlokusPiece piece : myHand) {
            if (piece.getColor() == this.inventoryDisplayColor) {
                String key = piece.getId() + "/" + piece.getColor();
                PiecePreviewPanel pp = handPanelCache.get(key);
                if (pp == null) {
                    pp = new PiecePreviewPanel(piece);
                    handPanelCache.put(key, pp);
                }
                handPanel.add(pp);
            }
        }

        if (selectedPiece != null && selectedPiece.getColor() != this.inventoryDisplayColor) {
            deselectPiece();
        }

        if (selectedPiece != null) {
            boolean pieceStillInHand = false;
            for (BlokusPiece piece : myHand) {
                if (piece.getId().equals(selectedPiece.getId()) && piece.getColor() == selectedPiece.getColor()) {
                    pieceStillInHand = true;
                    break;
                }
            }
            if (!pieceStillInHand) {
                deselectPiece();
            }
        }

        handPanel.revalidate();
        handPanel.repaint();
        handScrollPane.revalidate();
    }

    private void drawBoard(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                g2d.setColor(Color.DARK_GRAY);
                int x = c * CELL_SIZE;
                int y = r * CELL_SIZE;
                g2d.drawRect(x, y, CELL_SIZE, CELL_SIZE);

                if (board[r][c] != 0) {
                    int pieceColorNum = board[r][c];
                    Color pieceColor = getColorForPlayer(pieceColorNum);

                    g2d.setColor(pieceColor);
                    g2d.fillRect(x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2);

                    g2d.setColor(Color.BLACK);
                    g2d.drawRect(x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2);

                    boolean isMyPiece = false;
                    for (int myC : myColors) {
                        if (myC == pieceColorNum) {
                            isMyPiece = true;
                            break;
                        }
                    }

                    if (isMyPiece) {
                        g2d.setColor(Color.WHITE);
                        g2d.fillOval(x + CELL_SIZE - 7, y + CELL_SIZE - 7, 5, 5);
                        g2d.setColor(Color.BLACK);
                        g2d.drawOval(x + CELL_SIZE - 7, y + CELL_SIZE - 7, 5, 5);
                    }
                }
            }
        }

        drawCornerMarker(g2d, 0, 0, getColorForPlayer(1));
        drawCornerMarker(g2d, 19, 0, getColorForPlayer(2));
        drawCornerMarker(g2d, 19, 19, getColorForPlayer(3));
        drawCornerMarker(g2d, 0, 19, getColorForPlayer(4));
    }

    private void drawCornerMarker(Graphics2D g, int c, int r, Color color) {
        if (board[r][c] == 0) {
            int x = c * CELL_SIZE;
            int y = r * CELL_SIZE;
            g.setColor(color);
            g.setStroke(new BasicStroke(3));
            g.drawRect(x + 1, y + 1, CELL_SIZE - 3, CELL_SIZE - 3);
            g.setStroke(new BasicStroke(1));
        }
    }

    private void drawGhostPiece(Graphics g) {
        if (selectedPiece == null || mouseGridPos.x == -1) {
            return;
        }
        Graphics2D g2d = (Graphics2D) g;
        List<Point> points = selectedPiece.getPoints();

        Color ghostColor = getColorForPlayer(selectedPiece.getColor());
        if (isGhostValid) {
            g2d.setColor(new Color(ghostColor.getRed(), ghostColor.getGreen(), ghostColor.getBlue(), 128));
        } else {
            g2d.setColor(new Color(255, 0, 0, 128));
        }

        for (Point p : points) {
            int drawX = (mouseGridPos.x + p.x) * CELL_SIZE;
            int drawY = (mouseGridPos.y + p.y) * CELL_SIZE;
            g2d.fillRect(drawX, drawY, CELL_SIZE, CELL_SIZE);
        }
    }

    private boolean checkLocalPlacement(BlokusPiece piece, int x, int y) {
        List<Point> points = piece.getPoints();
        for (Point p : points) {
            int boardX = x + p.x;
            int boardY = y + p.y;
            if (boardX < 0 || boardX >= BOARD_SIZE || boardY < 0 || boardY >= BOARD_SIZE) return false;
            if (board[boardY][boardX] != 0) return false;
        }
        return true;
    }

    private void attemptPlaceBlock() {
        if (selectedPiece == null) return;

        if (resignedColors.contains(selectedPiece.getColor())) {
            deselectPiece();
            return;
        }

        if (amISpectating) return;

        if (isPeerlessMode) {
            if (isGhostValid) {
                String message = String.format("%s:%s:%d:%d:%d:%d",
                        Protocol.C2S_PLACE_BLOCK,
                        selectedPiece.getId(),
                        mouseGridPos.x,
                        mouseGridPos.y,
                        currentRotation,
                        selectedPiece.getColor()
                );
                client.sendMessage(message);
            }
        } else {
            if (!isMyTurn()) {
                deselectPiece();
                return;
            }

            if (isGhostValid) {
                String message = String.format("%s:%s:%d:%d:%d",
                        Protocol.C2S_PLACE_BLOCK,
                        selectedPiece.getId(),
                        mouseGridPos.x,
                        mouseGridPos.y,
                        currentRotation
                );
                client.sendMessage(message);
            }
        }
    }

    private void addMouseListeners() {
        boardPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (selectedPiece != null) {
                    int gridX = e.getX() / CELL_SIZE;
                    int gridY = e.getY() / CELL_SIZE;
                    if (gridX != mouseGridPos.x || gridY != mouseGridPos.y) {
                        mouseGridPos.setLocation(gridX, gridY);
                        isGhostValid = checkLocalPlacement(selectedPiece, gridX, gridY);
                        boardPanel.repaint();
                    }
                }
            }
        });

        boardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boardPanel.requestFocusInWindow();
                if (e.getButton() == MouseEvent.BUTTON3) {
                    deselectPiece();
                    return;
                }
                attemptPlaceBlock();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseGridPos.setLocation(-1, -1);
                boardPanel.repaint();
            }
        });
    }

    private void rotateSelectedPiece() {
        if (selectedPiece != null) {
            selectedPiece.rotate();
            selectedPanel.rotatePreview();
            currentRotation = (currentRotation + 1) % 4;
            isGhostValid = checkLocalPlacement(selectedPiece, mouseGridPos.x, mouseGridPos.y);
            boardPanel.repaint();
        }
    }

    public void deselectPiece() {
        if (selectedPanel != null) {
            selectedPanel.setSelected(false);
        }
        selectedPiece = null;
        selectedPanel = null;
        currentRotation = 0;
        mouseGridPos.setLocation(-1, -1);
        boardPanel.repaint();
    }

    private void toggleInventoryColor() {
        if (myColors.length > 1) {
            inventoryDisplayColor = (inventoryDisplayColor == myColors[0]) ? myColors[1] : myColors[0];
            updateHandPanelUI();
        }
    }

    private Color getColorForPlayer(int playerColor) {
        switch (playerColor) {
            case 1: return Color.RED;
            case 2: return Color.BLUE;
            case 3: return Color.YELLOW;
            case 4: return Color.GREEN;
            default: return Color.GRAY;
        }
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

    private void parseColorOut(String message) {
        int colorToRemove = -1;
        if (message.contains("Red") || message.contains("빨강")) colorToRemove = 1;
        else if (message.contains("Blue") || message.contains("파랑")) colorToRemove = 2;
        else if (message.contains("Yellow") || message.contains("노랑")) colorToRemove = 3;
        else if (message.contains("Green") || message.contains("초록")) colorToRemove = 4;

        if (colorToRemove != -1) {
            resignedColors.add(colorToRemove);
            myActiveColors.remove(colorToRemove);

            handPanel.repaint();
            statusPanel.repaint();

            if (myActiveColors.isEmpty() && !amISpectating && !isGameFinished) {
                setSpectateMode(true);
            }
        }
    }

    public void appendChatMessage(String data) {
        appendChatMessage(data, false);
    }

    public void appendChatMessage(String data, boolean isWhisper) {
        String message = data.replaceFirst(":", ": ");

        try {
            if (isWhisper) {
                StyledDocument chatDoc = chatAreaPane.getStyledDocument();
                chatDoc.insertString(chatDoc.getLength(), message + "\n", styleChatWhisper);
                chatAreaPane.setCaretPosition(chatAreaPane.getDocument().getLength());
                chatTabs.setSelectedComponent(chatAreaPane.getParent().getParent());

            } else if (data.startsWith("[시스템]:") || data.startsWith(Protocol.S2C_SYSTEM_MSG)) {
                StyledDocument doc = systemArea.getStyledDocument();
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

                if (message.contains("점수가 확정") || message.contains("시간이 초과되어")) {
                    parseColorOut(message);
                }

                systemArea.setCaretPosition(systemArea.getDocument().getLength());
                chatTabs.setSelectedComponent(systemArea.getParent().getParent());

            } else {
                StyledDocument chatDoc = chatAreaPane.getStyledDocument();
                chatDoc.insertString(chatDoc.getLength(), message + "\n", styleChatDefault);
                chatAreaPane.setCaretPosition(chatAreaPane.getDocument().getLength());
                chatTabs.setSelectedComponent(chatAreaPane.getParent().getParent());
            }
        } catch (Exception e) {
        }
    }

    public void clearChat() {
        chatAreaPane.setText("");
        systemArea.setText("");
    }

    private boolean isMyTurn() {
        if (amISpectating || isPeerlessMode) return false;

        for (int c : myColors) {
            if (c == currentTurnColor) {
                return true;
            }
        }
        return false;
    }

    // setTurnColor method removed (legacy/unused)

    private void handleNavigate(String direction) {
        if (chatField.isFocusOwner()) {
            return;
        }

        if (selectedPanel == null) {
            if (handPanel.getComponentCount() > 0) {
                Component first = handPanel.getComponent(0);
                if (first instanceof PiecePreviewPanel) {
                    PiecePreviewPanel targetPanel = (PiecePreviewPanel) first;

                    targetPanel.setSelected(true);
                    selectedPanel = targetPanel;

                    selectedPiece = new BlokusPiece(targetPanel.originalId, targetPanel.originalColor);
                    currentRotation = targetPanel.previewRotation;
                    for(int i=0; i < currentRotation; i++) {
                        selectedPiece.rotate();
                    }

                    isGhostValid = checkLocalPlacement(selectedPiece, mouseGridPos.x, mouseGridPos.y);
                    boardPanel.repaint();
                }
            }
            return;
        }

        Component[] components = handPanel.getComponents();
        if (components.length <= 1) return;

        Map<Integer, List<Component>> rows = new TreeMap<>();
        for (Component c : components) {
            if (c instanceof PiecePreviewPanel) {
                rows.computeIfAbsent(c.getY(), k -> new ArrayList<>()).add(c);
            }
        }

        for(List<Component> row : rows.values()) {
            row.sort(Comparator.comparingInt(Component::getX));
        }

        List<List<Component>> rowList = new ArrayList<>(rows.values());
        int currentRowIndex = -1;
        int currentInRowIndex = -1;
        int currentX = selectedPanel.getX();

        for (int i = 0; i < rowList.size(); i++) {
            List<Component> row = rowList.get(i);
            for (int j = 0; j < row.size(); j++) {
                if (row.get(j) == selectedPanel) {
                    currentRowIndex = i;
                    currentInRowIndex = j;
                    break;
                }
            }
            if (currentRowIndex != -1) break;
        }

        if (currentRowIndex == -1) return;

        Component targetComponent = null;
        List<Component> currentRow = rowList.get(currentRowIndex);

        switch (direction) {
            case "left":
                if (currentInRowIndex > 0) {
                    targetComponent = currentRow.get(currentInRowIndex - 1);
                }
                break;
            case "right":
                if (currentInRowIndex < currentRow.size() - 1) {
                    targetComponent = currentRow.get(currentInRowIndex + 1);
                }
                break;
            case "up":
                if (currentRowIndex > 0) {
                    List<Component> prevRow = rowList.get(currentRowIndex - 1);
                    targetComponent = findClosestX(prevRow, currentX);
                }
                break;
            case "down":
                if (currentRowIndex < rowList.size() - 1) {
                    List<Component> nextRow = rowList.get(currentRowIndex + 1);
                    targetComponent = findClosestX(nextRow, currentX);
                }
                break;
        }

        if (targetComponent != null && targetComponent instanceof PiecePreviewPanel) {
            PiecePreviewPanel targetPanel = (PiecePreviewPanel) targetComponent;

            if (selectedPanel != null) {
                selectedPanel.setSelected(false);
            }

            targetPanel.setSelected(true);
            selectedPanel = targetPanel;

            selectedPiece = new BlokusPiece(targetPanel.originalId, targetPanel.originalColor);
            currentRotation = targetPanel.previewRotation;
            for(int i=0; i < currentRotation; i++) {
                selectedPiece.rotate();
            }

            isGhostValid = checkLocalPlacement(selectedPiece, mouseGridPos.x, mouseGridPos.y);
            boardPanel.repaint();
        }
    }

    private Component findClosestX(List<Component> row, int targetX) {
        Component bestMatch = null;
        int minDiff = Integer.MAX_VALUE;

        int targetCenterX = targetX + (selectedPanel != null ? selectedPanel.getWidth() / 2 : 0);

        for (Component c : row) {
            int cCenterX = c.getX() + (c.getWidth() / 2);
            int diff = Math.abs(cCenterX - targetCenterX);

            if (diff < minDiff) {
                minDiff = diff;
                bestMatch = c;
            }
        }
        return bestMatch;
    }

    public void setPeerlessTimer(String text, Color color) {
        if (!isPeerlessMode) return;
        centerLabel.setText(text);
        centerLabel.setForeground(color);
    }

    public void removePieceFromHand(String pieceId, int color) {
        BlokusPiece pieceToRemove = null;
        for (BlokusPiece piece : myHand) {
            if (piece.getId().equals(pieceId) && piece.getColor() == color) {
                pieceToRemove = piece;
                break;
            }
        }
        if (pieceToRemove != null) {
            myHand.remove(pieceToRemove);
            calculateAndUpdateScore();
            updateHandPanelUI();
        }
    }

    public void updateBoardState(String data) {
        if (!isPeerlessMode) return;
        if (data == null) return;

        String[] cells = data.split(",");
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c] = Integer.parseInt(cells[r * BOARD_SIZE + c]);
            }
        }
        boardPanel.repaint();
    }


    private class ColorIndicatorPanel extends JPanel {
        private Color color;

        public ColorIndicatorPanel(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(20, 20));
        }

        public void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(color);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Color.BLACK);
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        }
    }

    private class PiecePreviewPanel extends JPanel {
        public final String originalId;
        public final int originalColor;
        public int previewRotation = 0;

        private boolean isSelected = false;
        private boolean isMemo = false;

        private static final int PREVIEW_PANEL_SIZE = 50;
        private final int PREVIEW_CELL_SIZE = 8;

        private final Border selectedBorder = BorderFactory.createLineBorder(Color.CYAN, 3);
        private final Border defaultBorder = BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1);

        public PiecePreviewPanel(BlokusPiece piece) {
            this.originalId = piece.getId();
            this.originalColor = piece.getColor();

            setPreferredSize(new Dimension(PREVIEW_PANEL_SIZE, PREVIEW_PANEL_SIZE));

            setBackground(Color.WHITE);
            setBorder(defaultBorder);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    boardPanel.requestFocusInWindow();

                    if (e.getButton() == MouseEvent.BUTTON3) {
                        toggleMemo();
                        return;
                    }

                    if (isSelected) {
                        deselectPiece();
                    } else {
                        if (selectedPanel != null) {
                            selectedPanel.setSelected(false);
                        }
                        setSelected(true);
                        selectedPanel = PiecePreviewPanel.this;

                        selectedPiece = new BlokusPiece(originalId, originalColor);
                        currentRotation = previewRotation;

                        for(int i=0; i < currentRotation; i++) {
                            selectedPiece.rotate();
                        }
                    }
                }
            });
        }

        private BlokusPiece getPreviewPiece() {
            BlokusPiece piece = new BlokusPiece(originalId, originalColor);
            for (int i = 0; i < previewRotation; i++) {
                piece.rotate();
            }
            return piece;
        }

        public void rotatePreview() {
            this.previewRotation = (this.previewRotation + 1) % 4;
            revalidate();
            repaint();
        }

        public void setSelected(boolean selected) {
            this.isSelected = selected;
            setBorder(selected ? selectedBorder : defaultBorder);
        }

        public void toggleMemo() {
            this.isMemo = !this.isMemo;
            setBackground(isMemo ? Color.GRAY : Color.WHITE);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            BlokusPiece pieceToDraw = getPreviewPiece();

            Color pieceColor = getColorForPlayer(pieceToDraw.getColor());
            g.setColor(pieceColor);
            List<Point> points = pieceToDraw.getPoints();

            int pieceWidth = pieceToDraw.getWidth() * PREVIEW_CELL_SIZE;
            int pieceHeight = pieceToDraw.getHeight() * PREVIEW_CELL_SIZE;

            int offsetX = (PREVIEW_PANEL_SIZE - pieceWidth) / 2;
            int offsetY = (PREVIEW_PANEL_SIZE - pieceHeight) / 2;


            for (Point p : points) {
                int x = offsetX + p.x * PREVIEW_CELL_SIZE;
                int y = offsetY + p.y * PREVIEW_CELL_SIZE;
                g.fillRect(x, y, PREVIEW_CELL_SIZE, PREVIEW_CELL_SIZE);

                g.setColor(Color.BLACK);
                g.drawRect(x, y, PREVIEW_CELL_SIZE, PREVIEW_CELL_SIZE);
                g.setColor(pieceColor);
            }

            boolean isResigned = resignedColors.contains(pieceToDraw.getColor());

            boolean active;
            if (isPeerlessMode) {
                active = true;
            } else {
                active = (pieceToDraw.getColor() == currentTurnColor);
            }

            if (isResigned) {
                active = false;
            }

            if (!active || amISpectating) {
                boolean displayActive = (pieceToDraw.getColor() == inventoryDisplayColor);

                if (amISpectating && displayActive) {
                    // Spectator looking at their own inventory, don't dim
                } else {
                    g.setColor(new Color(255, 255, 255, 180));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        }
    }
}