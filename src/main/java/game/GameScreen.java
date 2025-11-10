package game;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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

    private Point mouseGridPos = new Point(-1, -1);
    private boolean isGhostValid = false;

    private JPanel boardPanel;
    private JPanel handPanel;
    private JScrollPane handScrollPane;

    private JLabel turnLabel;
    private JButton toggleColorButton;

    private ColorIndicatorPanel currentTurnIndicator;
    private JPanel turnInfoPanel;
    private JPanel myColorsInfoPanel;
    private JLabel scoreLabel;

    private JLabel[] timerLabels = new JLabel[4];
    private JPanel turnTimerContainer;

    private JTabbedPane chatTabs;
    private JTextArea chatArea;
    private JTextPane systemArea;
    private JTextField chatField;

    private Style styleDefault;
    private Style styleRed;
    private Style styleBlue;
    private Style styleYellow;
    private Style styleGreen;

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

        // --- (2번) '현재 턴' UI 그룹 레이아웃 수정 ---
        // 1. turnInfoPanel (BorderLayout, hgap=5)
        turnInfoPanel = new JPanel(new BorderLayout(5, 0));
        turnInfoPanel.addMouseListener(backgroundClickListener);

        // 2. [현재 턴:] 라벨 (WEST)
        turnInfoPanel.add(new JLabel("현재 턴: "), BorderLayout.WEST);

        // 3. [블록 + 타이머] 그룹 (BorderLayout) (CENTER)
        JPanel turnBlockAndTimer = new JPanel(new BorderLayout(0, 2));
        turnBlockAndTimer.addMouseListener(backgroundClickListener);
        currentTurnIndicator = new ColorIndicatorPanel(Color.GRAY);
        turnBlockAndTimer.add(currentTurnIndicator, BorderLayout.NORTH); // 블록

        // 4. "타이머 자리 표시자" (BorderLayout) - (점프 현상 방지)
        turnTimerContainer = new JPanel(new BorderLayout());
        turnTimerContainer.addMouseListener(backgroundClickListener);
        turnTimerContainer.setPreferredSize(timerLabels[0].getPreferredSize()); // 높이 고정
        turnBlockAndTimer.add(turnTimerContainer, BorderLayout.CENTER); // 타이머 컨테이너

        turnInfoPanel.add(turnBlockAndTimer, BorderLayout.CENTER);

        // 5. [asd] 플레이어 이름 라벨 (EAST)
        turnLabel = new JLabel("게임 대기 중...");
        turnLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        turnInfoPanel.add(turnLabel, BorderLayout.EAST);
        // ------------------------------------

        myColorsInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        myColorsInfoPanel.addMouseListener(backgroundClickListener);
        myColorsInfoPanel.add(new JLabel("내 색상: "));

        scoreLabel = new JLabel("남은 점수: 89");
        scoreLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));

        infoPanel.add(turnInfoPanel);
        infoPanel.add(myColorsInfoPanel);
        infoPanel.add(new JSeparator(SwingConstants.VERTICAL));
        infoPanel.add(scoreLabel);

        topPanel.add(infoPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        controlPanel.addMouseListener(backgroundClickListener);
        JButton rotateButton = new JButton("회전 (r/ㄱ)");
        rotateButton.addActionListener(e -> rotateSelectedPiece());
        JButton passButton = new JButton("턴 넘기기");
        passButton.addActionListener(e -> {
            client.sendMessage(Protocol.C2S_PASS_TURN);
            deselectPiece();
        });

        controlPanel.add(rotateButton);
        controlPanel.add(passButton);
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
        toggleColorButton = new JButton("내 블록 색상 전환");
        toggleColorButton.setVisible(false);
        toggleColorButton.addActionListener(e -> toggleInventoryColor());
        southPanel.add(toggleColorButton, BorderLayout.NORTH);

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

        chatPanel.setPreferredSize(new Dimension(220, 0));

        add(chatPanel, BorderLayout.WEST);

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
    }

    public void initializeGame(String data) {
        String[] parts = data.split(":");
        String[] colorsStr = parts[1].split(",");

        myColors = new int[colorsStr.length];

        myColorsInfoPanel.removeAll();
        myColorsInfoPanel.add(new JLabel("내 색상: "));
        for (int i = 0; i < colorsStr.length; i++) {
            myColors[i] = Integer.parseInt(colorsStr[i]);
            int colorNum = myColors[i];

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
        updateTimerVisibility();
    }

    public void updateGameState(String data) {
        if (data == null) return;
        String[] parts = data.split(":");
        if (parts.length < 3) return;

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

        int oldTurnColor = this.currentTurnColor;
        this.currentTurnColor = newTurnColor;

        turnTimerContainer.removeAll();

        JLabel newTurnTimer = timerLabels[newTurnColor - 1];
        if (newTurnTimer.getParent() != null) {
            ((Container)newTurnTimer.getParent()).remove(newTurnTimer);
        }
        turnTimerContainer.add(newTurnTimer, BorderLayout.CENTER);

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

        turnTimerContainer.revalidate();
        turnTimerContainer.repaint();
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

        if (inventoryFilterChanged) {
            updateHandPanelUI();
        } else if (oldTurnColor != newTurnColor) {
            handPanel.repaint();
        }

        boardPanel.repaint();
    }

    public void updatePlayerHand(String data) {
        myHand.clear();
        int totalScore = 0;

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
        if (currentTurnColor == 0) return;

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


    public void updateTimerV2(String data) {
    }

    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void updateHandPanelUI() {
        handPanel.removeAll();
        handPanelCache.clear();

        for (BlokusPiece piece : myHand) {
            if (piece.getColor() == this.inventoryDisplayColor) {
                PiecePreviewPanel pp = new PiecePreviewPanel(piece);
                String key = piece.getId() + "/" + piece.getColor();
                handPanelCache.put(key, pp);
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

                if (selectedPiece == null) return;

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
            selectedPanel.resetRotation(selectedPiece.getId());
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

    private boolean isMyTurn() {
        for (int c : myColors) {
            if (c == currentTurnColor) {
                return true;
            }
        }
        return false;
    }

    public void setTurnColor(String colorStr) {
        try {
            int c = Integer.parseInt(colorStr);
            currentTurnColor = c;
            currentTurnIndicator.setColor(getColorForPlayer(c));
            repaint();
        } catch (NumberFormatException ignored) {}
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
        private BlokusPiece piece;
        private boolean isSelected = false;

        private static final int PREVIEW_PANEL_SIZE = 50;
        private final int PREVIEW_CELL_SIZE = 8;

        private final Border selectedBorder = BorderFactory.createLineBorder(Color.CYAN, 3);
        private final Border defaultBorder = BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1);

        public PiecePreviewPanel(BlokusPiece piece) {
            this.piece = new BlokusPiece(piece);

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
                            selectedPanel.resetRotation(selectedPiece.getId());
                        }
                        setSelected(true);
                        selectedPanel = PiecePreviewPanel.this;
                        selectedPiece = new BlokusPiece(PiecePreviewPanel.this.piece);
                        currentRotation = 0;
                    }
                }
            });
        }

        public void rotatePreview() {
            this.piece.rotate();
            revalidate();
            repaint();
        }

        public void resetRotation(String originalId) {
            this.piece = new BlokusPiece(originalId, this.piece.getColor());
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
            Color pieceColor = getColorForPlayer(piece.getColor());
            g.setColor(pieceColor);
            List<Point> points = piece.getPoints();

            int pieceWidth = piece.getWidth() * PREVIEW_CELL_SIZE;
            int pieceHeight = piece.getHeight() * PREVIEW_CELL_SIZE;

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

            boolean active = (piece.getColor() == currentTurnColor);
            if (!active) {
                g.setColor(new Color(255, 255, 255, 180));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }
}