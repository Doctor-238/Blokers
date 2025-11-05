package game;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [수정됨] 실제 블로커스 게임이 이루어지는 JPanel.
 * 채팅(좌), 보드(중), 인벤토리(하), 정보/조작(상)
 */
public class GameScreen extends JPanel {
    private BlokusClient client;

    private static final int BOARD_SIZE = 20;
    private static final int CELL_SIZE = 25;
    private static final int BOARD_PANEL_SIZE = BOARD_SIZE * CELL_SIZE; // 500x500

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
    private JPanel myColorsPanel;

    // 타이머 레이블
    private JLabel[] timerLabels = new JLabel[4];

    private JTextArea chatArea;
    private JTextField chatField;

    public GameScreen(BlokusClient client) {
        this.client = client;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // 1. 상단 패널 (정보 + 조작 버튼)
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));

        // 1-1. 정보 (턴, 내 색상, 타이머)
        JPanel infoPanel = new JPanel(new BorderLayout(10, 5));

        JPanel turnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        turnLabel = new JLabel("게임 대기 중...");
        turnLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        currentTurnIndicator = new ColorIndicatorPanel(Color.GRAY);
        turnPanel.add(new JLabel("현재 턴: "));
        turnPanel.add(currentTurnIndicator);
        turnPanel.add(turnLabel);
        infoPanel.add(turnPanel, BorderLayout.NORTH);

        myColorsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        myColorsPanel.add(new JLabel("내 색상: "));
        infoPanel.add(myColorsPanel, BorderLayout.CENTER);

        // 타이머 패널
        JPanel timerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        for(int i=0; i<4; i++) {
            timerLabels[i] = new JLabel("5:00");
            timerLabels[i].setFont(new Font("맑은 고딕", Font.BOLD, 12));
            timerLabels[i].setForeground(getColorForPlayer(i+1));
            timerPanel.add(timerLabels[i]);
        }
        infoPanel.add(timerPanel, BorderLayout.SOUTH);

        topPanel.add(infoPanel, BorderLayout.CENTER);

        // 1-2. 조작 버튼 (회전, 턴 넘기기)
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton rotateButton = new JButton("회전");
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

        // 2. 게임 보드 패널 (중앙)
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

        // 3. 하단 패널 (인벤토리 + 토글 버튼)
        JPanel southPanel = new JPanel(new BorderLayout(5, 5));

        toggleColorButton = new JButton("내 블록 색상 전환");
        toggleColorButton.setVisible(false);
        toggleColorButton.addActionListener(e -> toggleInventoryColor());
        southPanel.add(toggleColorButton, BorderLayout.NORTH);

        // [수정됨] WrapLayout 사용
        handPanel = new JPanel(new WrapLayout(WrapLayout.LEFT, 5, 5));
        handPanel.setBackground(Color.WHITE);
        handScrollPane = new JScrollPane(handPanel);
        handScrollPane.setPreferredSize(new Dimension(800, 160)); // 2~3줄 높이

        southPanel.add(handScrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        // 4. 채팅 패널 (좌측)
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

        // 'R' 키보드 바인딩 설정
        setupKeyBindings();
    }

    // 'R' 키 바인딩
    private void setupKeyBindings() {
        InputMap im = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = this.getActionMap();

        im.put(KeyStroke.getKeyStroke('r'), "rotateAction");
        im.put(KeyStroke.getKeyStroke('R'), "rotateAction");

        am.put("rotateAction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 채팅창에 포커스가 없을 때만 회전
                if (!chatField.isFocusOwner()) {
                    rotateSelectedPiece();
                }
            }
        });
    }

    // S2C_GAME_START:<playerCount>:<yourColor(s)>
    public void initializeGame(String data) {
        String[] parts = data.split(":");
        String[] colorsStr = parts[1].split(",");

        myColors = new int[colorsStr.length];
        myColorsPanel.removeAll();
        myColorsPanel.add(new JLabel("내 색상: "));
        for (int i=0; i < colorsStr.length; i++) {
            myColors[i] = Integer.parseInt(colorsStr[i]);
            myColorsPanel.add(new ColorIndicatorPanel(getColorForPlayer(myColors[i])));
        }
        myColorsPanel.revalidate();
        myColorsPanel.repaint();

        toggleColorButton.setVisible(myColors.length > 1);
        inventoryDisplayColor = (myColors.length > 0) ? myColors[0] : 0;

        board = new int[BOARD_SIZE][BOARD_SIZE];
        myHand.clear();
        handPanelCache.clear();
        handPanel.removeAll();
        deselectPiece();

        for (int i=0; i<4; i++) {
            timerLabels[i].setText(formatTime(300));
        }
    }

    // S2C_GAME_STATE:<board_data>:<currentPlayerName (Color 턴)>:<colorId>
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
            handPanel.repaint(); // 턴만 바뀐 경우 (활성/비활성)
        }

        boardPanel.repaint();
    }

    // S2C_HAND_UPDATE:<pieceId1/color1>,<pieceId2/color2>...
    public void updatePlayerHand(String data) {
        myHand.clear();
        if (data != null && !data.isEmpty()) {
            String[] pieces = data.split(",");
            for (String p : pieces) {
                String[] pieceData = p.split("/");
                String id = pieceData[0];
                int color = Integer.parseInt(pieceData[1]);
                myHand.add(new BlokusPiece(id, color));
            }
        }
        updateHandPanelUI();
    }

    // S2C_TIME_UPDATE:<r_sec>,<b_sec>,<y_sec>,<g_sec>
    public void updateTimer(String data) {
        if (data == null) return;
        String[] times = data.split(",");
        if (times.length == 4) {
            for(int i=0; i<4; i++) {
                timerLabels[i].setText(formatTime(Integer.parseInt(times[i])));
            }
        }
    }

    // 초 -> M:SS 포맷
    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // 하단 인벤토리가 inventoryDisplayColor만 표시하도록 수정
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

        handPanel.revalidate();
        handPanel.repaint();
        handScrollPane.revalidate();
    }

    // 보드 그리기
    private void drawBoard(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                g2d.setColor(Color.DARK_GRAY);
                g2d.drawRect(c * CELL_SIZE, r * CELL_SIZE, CELL_SIZE, CELL_SIZE);

                if (board[r][c] != 0) {
                    g2d.setColor(getColorForPlayer(board[r][c]));
                    g2d.fillRect(c * CELL_SIZE + 1, r * CELL_SIZE + 1, CELL_SIZE - 2, CELL_SIZE - 2);
                }
            }
        }

        // 시작 코너 표시
        drawCornerMarker(g2d, 0, 0, getColorForPlayer(1)); // Red
        drawCornerMarker(g2d, 19, 0, getColorForPlayer(2)); // Blue
        drawCornerMarker(g2d, 19, 19, getColorForPlayer(3)); // Yellow
        drawCornerMarker(g2d, 0, 19, getColorForPlayer(4)); // Green
    }

    // 시작 코너 마커 그리기 헬퍼
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

    // 고스트 조각 그리기
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

    // 로컬 유효성 검사 (경계, 겹침)
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

    // 마우스 리스너
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
                // 보드 클릭 시 채팅창 포커스 해제
                boardPanel.requestFocusInWindow();

                if (e.getButton() == MouseEvent.BUTTON3) { // 우클릭
                    deselectPiece();
                    return;
                }

                // [수정됨] isMyTurn() 체크 추가
                if (selectedPiece != null && isGhostValid && isMyTurn()) {
                    // C2S_PLACE_BLOCK: <pieceId>:<x>:<y>:<rotation>
                    String message = String.format("%s:%s:%d:%d:%d",
                            Protocol.C2S_PLACE_BLOCK,
                            selectedPiece.getId(),
                            mouseGridPos.x,
                            mouseGridPos.y,
                            currentRotation
                    );
                    client.sendMessage(message);
                    deselectPiece();

                } else if (selectedPiece != null && !isGhostValid) {
                    System.out.println("놓을 수 없는 위치입니다. (Local Check)");
                } else if (selectedPiece != null && !isMyTurn()) {
                    System.out.println("당신의 턴이 아닙니다.");
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseGridPos.setLocation(-1, -1);
                boardPanel.repaint();
            }
        });
    }

    // [수정됨] 인벤토리 패널도 같이 회전
    private void rotateSelectedPiece() {
        if (selectedPiece != null) {
            selectedPiece.rotate(); // 고스트 회전
            selectedPanel.rotatePreview(); // 인벤토리 회전
            currentRotation = (currentRotation + 1) % 4;
            isGhostValid = checkLocalPlacement(selectedPiece, mouseGridPos.x, mouseGridPos.y);
            boardPanel.repaint();
        }
    }

    private void deselectPiece() {
        if (selectedPanel != null) {
            selectedPanel.setSelected(false);
            // [신규] 회전한 인벤토리 조각 원복
            selectedPanel.resetRotation(selectedPiece.getId());
        }
        selectedPiece = null;
        selectedPanel = null;
        currentRotation = 0;
        mouseGridPos.setLocation(-1, -1);
        boardPanel.repaint();
    }

    // 1v1 인벤토리 토글
    private void toggleInventoryColor() {
        if (myColors.length > 1) {
            if (inventoryDisplayColor == myColors[0]) {
                inventoryDisplayColor = myColors[1];
            } else {
                inventoryDisplayColor = myColors[0];
            }
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

    // 채팅 전송
    private void sendChat() {
        String message = chatField.getText();
        if (!message.trim().isEmpty()) {
            client.sendMessage(Protocol.C2S_CHAT + ":" + message);
            chatField.setText("");
        }
    }

    // 채팅 수신
    public void appendChatMessage(String data) {
        chatArea.append(data.replaceFirst(":", ": ") + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // 채팅 클리어
    public void clearChat() {
        chatArea.setText("");
    }

    // 현재 내 턴인지 확인
    private boolean isMyTurn() {
        for (int c : myColors) {
            if (c == currentTurnColor) {
                return true;
            }
        }
        return false;
    }


    /**
     * 턴/색상 표시용 사각형 패널
     */
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


    /**
     * 인벤토리용 블록 조각 시각화 패널
     */
    private class PiecePreviewPanel extends JPanel {
        private BlokusPiece piece; // [수정됨] 회전/선택 상태를 가지는 인스턴스
        private boolean isSelected = false;
        private final int PREVIEW_CELL_SIZE = 8;
        private final Border selectedBorder = BorderFactory.createLineBorder(Color.CYAN, 3);
        private final Border defaultBorder = BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1);

        public PiecePreviewPanel(BlokusPiece piece) {
            // [수정됨] 원본을 복사해서 사용
            this.piece = new BlokusPiece(piece);

            int w = piece.getWidth() * PREVIEW_CELL_SIZE + 6;
            int h = piece.getHeight() * PREVIEW_CELL_SIZE + 6;
            setPreferredSize(new Dimension(Math.max(w, 40), Math.max(h, 40)));
            setBackground(Color.WHITE);
            setBorder(defaultBorder);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    boardPanel.requestFocusInWindow();

                    // [수정됨] 턴 제한 제거 (Req 4)
                    // if (!isMyTurn() || piece.getColor() != currentTurnColor) {
                    //     return;
                    // }

                    if (e.getButton() == MouseEvent.BUTTON3) {
                        deselectPiece();
                        return;
                    }

                    if (isSelected) {
                        deselectPiece();
                    } else {
                        if (selectedPanel != null) {
                            selectedPanel.setSelected(false);
                            // [신규] 이전 선택 패널 회전 리셋
                            selectedPanel.resetRotation(selectedPiece.getId());
                        }

                        setSelected(true);
                        selectedPanel = PiecePreviewPanel.this;
                        // [수정됨] 인벤토리 패널의 현재 상태를 복사
                        selectedPiece = new BlokusPiece(PiecePreviewPanel.this.piece);
                        currentRotation = 0; // 고스트는 항상 0에서 시작
                    }
                }
            });
        }

        // [신규] 인벤토리 패널 자체를 회전
        public void rotatePreview() {
            this.piece.rotate(); // 인벤토리 조각 회전

            int w = piece.getWidth() * PREVIEW_CELL_SIZE + 6;
            int h = piece.getHeight() * PREVIEW_CELL_SIZE + 6;
            setPreferredSize(new Dimension(Math.max(w, 40), Math.max(h, 40)));

            revalidate();
            repaint();
        }

        // [신규] 선택 해제 시 회전 초기화
        public void resetRotation(String originalId) {
            // ID와 색상으로 새 조각을 만들어 원본 모양으로 복구
            this.piece = new BlokusPiece(originalId, this.piece.getColor());

            int w = piece.getWidth() * PREVIEW_CELL_SIZE + 6;
            int h = piece.getHeight() * PREVIEW_CELL_SIZE + 6;
            setPreferredSize(new Dimension(Math.max(w, 40), Math.max(h, 40)));

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
            g.setColor(getColorForPlayer(piece.getColor()));
            List<Point> points = piece.getPoints();

            for (Point p : points) {
                g.fillRect(3 + p.x * PREVIEW_CELL_SIZE,
                        3 + p.y * PREVIEW_CELL_SIZE,
                        PREVIEW_CELL_SIZE,
                        PREVIEW_CELL_SIZE);
            }

            // [수정됨] 비활성화 로직 (현재 턴의 색상인가?)
            boolean isActive = (piece.getColor() == currentTurnColor);
            if (!isActive) {
                g.setColor(new Color(255, 255, 255, 180)); // 70%
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }
}