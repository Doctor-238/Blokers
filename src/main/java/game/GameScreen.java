package game;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.*;
import java.util.List;

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

    private OutlineLabel turnLabel;
    private JButton toggleColorButton;
    private JButton deselectButton;
    private JButton rotateButton;
    private JButton passButton;
    private JButton resignButton;

    private ColorIndicatorPanel currentTurnIndicator;
    private JPanel turnInfoPanel;
    private JPanel turnBlockAndTimer;
    private JPanel myColorsInfoPanel;
    private JLabel scoreLabel;

    private JLabel[] timerLabels = new JLabel[4];

    private JTabbedPane chatTabs;
    private JTextPane chatAreaPane; // JTextArea -> JTextPane
    private JTextPane systemArea;
    private JTextField chatField;

    private Style styleDefault;
    private Style styleRed;
    private Style styleBlue;
    private Style styleYellow;
    private Style styleGreen;
    private Style styleWhisper;

    // Styles for the main chat pane
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
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        addMouseListener(backgroundClickListener);

        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        topPanel.addMouseListener(backgroundClickListener);

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        infoPanel.addMouseListener(backgroundClickListener);

        for (int i = 0; i < 4; i++) {
            timerLabels[i] = new JLabel("5:00");
            timerLabels[i].setFont(new Font("맑은 고딕", Font.BOLD, 12));

            Color timerColor = getColorForPlayer(i + 1);
            if (i + 1 == 3) {
                timerColor = DARK_YELLOW;
            }
            timerLabels[i].setForeground(timerColor);
            timerLabels[i].setHorizontalAlignment(SwingConstants.CENTER);
        }

        turnInfoPanel = new JPanel(new BorderLayout(5, 0));
        turnInfoPanel.addMouseListener(backgroundClickListener);

        turnInfoPanel.add(new JLabel("현재 턴: "), BorderLayout.WEST);

        turnBlockAndTimer = new JPanel(new BorderLayout(0, 2));
        turnBlockAndTimer.addMouseListener(backgroundClickListener);
        currentTurnIndicator = new ColorIndicatorPanel(Color.GRAY);
        turnBlockAndTimer.add(currentTurnIndicator, BorderLayout.NORTH);

        turnBlockAndTimer.add(timerLabels[0], BorderLayout.CENTER);

        turnInfoPanel.add(turnBlockAndTimer, BorderLayout.CENTER);

        turnLabel = new OutlineLabel("게임 대기 중...");
        turnLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        turnInfoPanel.add(turnLabel, BorderLayout.EAST);

        myColorsInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        myColorsInfoPanel.addMouseListener(backgroundClickListener);
        myColorsInfoPanel.add(new JLabel("내 색상: "));

        infoPanel.add(turnInfoPanel);
        infoPanel.add(myColorsInfoPanel);

        topPanel.add(infoPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        controlPanel.addMouseListener(backgroundClickListener);

        rotateButton = new JButton("회전 (r)");
        rotateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rotateSelectedPiece();
            }
        });
        passButton = new JButton("턴 넘기기");
        passButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.sendMessage(Protocol.C2S_PASS_TURN);
                deselectPiece();
            }
        });

        resignButton = new JButton("점수 확정 (Esc)");
        resignButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleResign();
            }
        });

        controlPanel.add(rotateButton);
        controlPanel.add(passButton);
        controlPanel.add(resignButton);
        topPanel.add(controlPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

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

        addMouseListeners();
        add(boardPanel, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout(5, 5));
        southPanel.addMouseListener(backgroundClickListener);

        JPanel southTopPanel = new JPanel(new BorderLayout(10, 5));

        JPanel southWestButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        southWestButtons.setOpaque(false);

        toggleColorButton = new JButton("블록 전환 (e)");
        toggleColorButton.setVisible(false);
        toggleColorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleInventoryColor();
            }
        });
        southWestButtons.add(toggleColorButton);

        deselectButton = new JButton("선택 취소 (q)");
        deselectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deselectPiece();
            }
        });
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

        handScrollPane = new JScrollPane(handPanel);
        handScrollPane.setPreferredSize(new Dimension(800, 160));
        handScrollPane.addMouseListener(backgroundClickListener);

        southPanel.add(handScrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        JPanel chatPanel = new JPanel(new BorderLayout());
        chatTabs = new JTabbedPane();

        // JTextArea -> JTextPane
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

        chatTabs.addTab("채팅", new JScrollPane(chatAreaPane)); // Add new pane

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

        chatPanel.setPreferredSize(new Dimension(220, 0));

        add(chatPanel, BorderLayout.WEST);

        // 버튼 포커스 비활성화 (스페이스바 문제 해결)
        rotateButton.setFocusable(false);
        passButton.setFocusable(false);
        resignButton.setFocusable(false);
        toggleColorButton.setFocusable(false);
        deselectButton.setFocusable(false);
        sendButton.setFocusable(false);

        setupKeyBindings();
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
        im.put(KeyStroke.getKeyStroke('a'), "navigateLeft");
        im.put(KeyStroke.getKeyStroke('A'), "navigateLeft");
        im.put(KeyStroke.getKeyStroke('s'), "navigateDown");
        im.put(KeyStroke.getKeyStroke('S'), "navigateDown");
        im.put(KeyStroke.getKeyStroke('d'), "navigateRight");
        im.put(KeyStroke.getKeyStroke('D'), "navigateRight");

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
        turnInfoPanel.setVisible(true);
        turnBlockAndTimer.setVisible(true);
        turnInfoPanel.getComponent(0).setVisible(true);
        turnLabel.setText("게임 대기 중...");
        turnLabel.setForeground(UIManager.getColor("Label.foreground"));
        turnLabel.setOutlineEnabled(false);
        myColorsInfoPanel.setVisible(true);
        scoreLabel.setVisible(true);

        String[] parts = data.split(":");
        String[] colorsStr = parts[1].split(",");

        myColors = new int[colorsStr.length];
        myActiveColors.clear();
        resignedColors.clear();

        myColorsInfoPanel.removeAll();
        myColorsInfoPanel.add(new JLabel("내 색상: "));
        for (int i = 0; i < colorsStr.length; i++) {
            myColors[i] = Integer.parseInt(colorsStr[i]);
            int colorNum = myColors[i];

            myActiveColors.add(colorNum);

            JPanel myColorGroup = new JPanel(new BorderLayout(0, 2));
            myColorGroup.add(new ColorIndicatorPanel(getColorForPlayer(colorNum)), BorderLayout.NORTH);

            JLabel timer = timerLabels[colorNum - 1];
            if (timer.getParent() != null) {
                ((Container)timer.getParent()).remove(timer);
            }
            myColorGroup.add(timer, BorderLayout.CENTER);
            myColorsInfoPanel.add(myColorGroup);
        }
        myColorsInfoPanel.revalidate();
        myColorsInfoPanel.repaint();

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
            timerLabels[i].setText(formatTime(300));
        }

        setSpectateMode(false);
        updateTimerVisibility();
    }

    public void initializePeerlessGame(String data) {
        this.isPeerlessMode = true;
        this.isGameFinished = false;
        resignButton.setText("점수 확정 (Esc)");
        passButton.setVisible(false);
        turnInfoPanel.setVisible(true);
        turnBlockAndTimer.setVisible(false);
        turnInfoPanel.getComponent(0).setVisible(false);
        turnLabel.setText("피어리스 모드");
        turnLabel.setOutlineEnabled(true);
        myColorsInfoPanel.setVisible(false);
        scoreLabel.setVisible(true);

        String[] parts = data.split(":");
        String[] colorsStr = parts[1].split(",");

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

    public void setGameFinished(boolean finished) {
        this.isGameFinished = finished;
    }


    private void setSpectateMode(boolean spectating) {
        this.amISpectating = spectating;

        if (spectating) {
            deselectPiece();
        }

        updateButtonStates(); // Update buttons based on new state
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
        String currentPlayerName = parts[1].split(" ")[0];
        int newTurnColor = Integer.parseInt(parts[2]);

        String[] cells = boardData.split(",");
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c] = Integer.parseInt(cells[r * BOARD_SIZE + c]);
            }
        }

        turnLabel.setText(currentPlayerName);
        currentTurnIndicator.setColor(getColorForPlayer(newTurnColor));

        for (Component c : turnBlockAndTimer.getComponents()) {
            if (c instanceof JLabel) {
                turnBlockAndTimer.remove(c);
                break;
            }
        }

        JLabel newTurnTimer = timerLabels[newTurnColor - 1];
        if (newTurnTimer.getParent() != null) {
            ((Container)newTurnTimer.getParent()).remove(newTurnTimer);
        }

        turnBlockAndTimer.add(newTurnTimer, BorderLayout.CENTER);

        this.currentTurnColor = newTurnColor;

        myColorsInfoPanel.removeAll();
        myColorsInfoPanel.add(new JLabel("내 색상: "));
        for (int myColor : myColors) {
            JPanel myColorGroup = new JPanel(new BorderLayout(0, 2));
            myColorGroup.add(new ColorIndicatorPanel(getColorForPlayer(myColor)), BorderLayout.NORTH);
            JLabel timer = timerLabels[myColor - 1];
            if (timer.getParent() != null) {
                ((Container)timer.getParent()).remove(timer);
            }
            myColorGroup.add(timer, BorderLayout.CENTER);
            myColorsInfoPanel.add(myColorGroup);
        }

        turnBlockAndTimer.revalidate();
        turnBlockAndTimer.repaint();
        myColorsInfoPanel.revalidate();
        myColorsInfoPanel.repaint();

        updateTimerVisibility();

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
                timerLabels[i].setText(formatTime(Integer.parseInt(times[i])));
            }
            updateTimerVisibility();
        }
    }

    private void updateTimerVisibility() {
        if (currentTurnColor == 0 || isPeerlessMode) return;

        boolean turnIsMyColor = false;
        for (int c : myColors) {
            if (c == currentTurnColor) {
                turnIsMyColor = true;
                break;
            }
        }

        JLabel turnTimer = timerLabels[currentTurnColor - 1];
        turnTimer.setVisible(!turnIsMyColor);

        for (int myColor : myColors) {
            timerLabels[myColor - 1].setVisible(true);
        }

        for (int i = 0; i < 4; i++) {
            int color = i + 1;
            if (color == currentTurnColor) continue;

            boolean isMyColor = false;
            for (int c : myColors) {
                if (c == color) {
                    isMyColor = true;
                    break;
                }
            }
            if (isMyColor) continue;

            timerLabels[i].setVisible(false);
        }
    }

    public String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
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

            handPanel.repaint(); // Repaint hand to show resigned overlay

            if (myActiveColors.isEmpty() && !amISpectating && !isGameFinished) {
                setSpectateMode(true); // Auto-spectate, no popup
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
                // Add to main chat pane with whisper style
                StyledDocument chatDoc = chatAreaPane.getStyledDocument();
                chatDoc.insertString(chatDoc.getLength(), message + "\n", styleChatWhisper);
                chatAreaPane.setCaretPosition(chatAreaPane.getDocument().getLength());
                chatTabs.setSelectedComponent(chatAreaPane.getParent().getParent());

            } else if (data.startsWith("[시스템]:") || data.startsWith(Protocol.S2C_SYSTEM_MSG)) {
                // Add to system pane
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
                // Add regular chat to main chat pane
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

    public void setTurnColor(String colorStr) {
        if (isPeerlessMode) return;
        try {
            int c = Integer.parseInt(colorStr);
            currentTurnColor = c;
            currentTurnIndicator.setColor(getColorForPlayer(c));
            repaint();
        } catch (NumberFormatException ignored) {}
    }

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
        turnLabel.setText(text);
        turnLabel.setForeground(color);
        turnLabel.setOutlineEnabled(true);
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
                        deselectPiece();
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
                    // Dim if spectating (not own inventory) OR if piece is not active (resigned or not turn)
                    g.setColor(new Color(255, 255, 255, 180));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        }
    }

    private class OutlineLabel extends JLabel {
        private Color outlineColor = Color.BLACK;
        private int outlineThickness = 1;
        private boolean outlineEnabled = true;

        public OutlineLabel(String text) {
            super(text);
        }

        public void setOutlineEnabled(boolean enabled) {
            this.outlineEnabled = enabled;
            repaint();
        }

        @Override
        public void paintComponent(Graphics g) {
            if (!outlineEnabled) {
                super.paintComponent(g);
                return;
            }

            String text = getText();
            if (text == null || text.isEmpty()) {
                super.paintComponent(g);
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            FontMetrics fm = g2.getFontMetrics();
            Insets insets = getInsets();

            int x;
            if (getHorizontalAlignment() == CENTER) {
                x = (getWidth() - fm.stringWidth(text) - insets.left - insets.right) / 2 + insets.left;
            } else if (getHorizontalAlignment() == RIGHT) {
                x = getWidth() - fm.stringWidth(text) - insets.right;
            } else { // LEFT
                x = insets.left;
            }

            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

            g2.setColor(outlineColor);
            int t = outlineThickness;
            g2.drawString(text, x - t, y - t);
            g2.drawString(text, x - t, y + t);
            g2.drawString(text, x + t, y - t);
            g2.drawString(text, x + t, y + t);
            g2.drawString(text, x - t, y);
            g2.drawString(text, x + t, y);
            g2.drawString(text, x, y - t);
            g2.drawString(text, x, y + t);

            g2.setColor(getForeground());
            g2.drawString(text, x, y);

            g2.dispose();
        }
    }
}