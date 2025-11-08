// 상태바(한 줄 표시), ㄱ 키 회전, 기권 버튼(RESIGN) 추가를 위해 확장된 버전
package game;

import javax.swing.*;
import javax.swing.border.Border;
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
    private JButton resignButton;

    private ColorIndicatorPanel currentTurnIndicator;
    private JPanel myColorsPanel;

    private JLabel[] timerLabels = new JLabel[4];

    private JTextArea chatArea;
    private JTextField chatField;

    public GameScreen(BlokusClient client) {
        this.client = client;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel topPanel = new JPanel(new BorderLayout(10, 5));

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

        JPanel timerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        for (int i = 0; i < 4; i++) {
            timerLabels[i] = new JLabel("5:00");
            timerLabels[i].setFont(new Font("맑은 고딕", Font.BOLD, 12));
            timerLabels[i].setForeground(getColorForPlayer(i + 1));
            timerPanel.add(timerLabels[i]);
        }
        infoPanel.add(timerPanel, BorderLayout.SOUTH);

        topPanel.add(infoPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton rotateButton = new JButton("회전 (r/ㄱ)");
        rotateButton.addActionListener(e -> rotateSelectedPiece());
        JButton passButton = new JButton("턴 넘기기");
        passButton.addActionListener(e -> {
            client.sendMessage(Protocol.C2S_PASS_TURN);
            deselectPiece();
        });
        resignButton = new JButton("기권");
        resignButton.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this,
                    "정말 기권하시겠습니까? 점수 확정 후 더 이상 관여할 수 없습니다.",
                    "기권 확인",
                    JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                client.sendMessage(ProtocolExt.C2S_RESIGN);
                Object[] opts = {"관전하기", "로비로"};
                int sel = JOptionPane.showOptionDialog(this, "관전을 계속하시겠습니까?",
                        "관전 선택", JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
                if (sel == 1) {
                    // 로비 이동은 서버 SYSTEM_MSG 로직 재활용
                    client.sendMessage(Protocol.C2S_LEAVE_ROOM); // 게임 중이라 실패할 수 있음 -> 서버 기권 후 로비 이동 구현 필요(간단 예시)
                } else {
                    client.sendMessage(ProtocolExt.C2S_SPECTATE + ":" + ""); // roomId 필요시 확장
                }
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
        toggleColorButton = new JButton("내 블록 색상 전환");
        toggleColorButton.setVisible(false);
        toggleColorButton.addActionListener(e -> toggleInventoryColor());
        southPanel.add(toggleColorButton, BorderLayout.NORTH);

        handPanel = new JPanel(new WrapLayout(WrapLayout.LEFT, 5, 5));
        handPanel.setBackground(Color.WHITE);
        handScrollPane = new JScrollPane(handPanel);
        handScrollPane.setPreferredSize(new Dimension(800, 160));

        southPanel.add(handScrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

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

        setupKeyBindings();
    }

    private void setupKeyBindings() {
        InputMap im = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = this.getActionMap();

        im.put(KeyStroke.getKeyStroke('r'), "rotateAction");
        im.put(KeyStroke.getKeyStroke('R'), "rotateAction");
        // 한글 'ㄱ' (유니코드: U+3131)
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
        myColorsPanel.removeAll();
        myColorsPanel.add(new JLabel("내 색상: "));
        for (int i = 0; i < colorsStr.length; i++) {
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

        for (int i = 0; i < 4; i++) {
            timerLabels[i].setText(formatTime(300));
        }
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
        if (data != null && !data.isEmpty()) {
            String[] pieces = data.split(",");
            for (String p : pieces) {
                String[] pieceData = p.split("/");
                if (pieceData.length != 2) continue;
                String id = pieceData[0];
                int color = Integer.parseInt(pieceData[1]);
                myHand.add(new BlokusPiece(id, color));
            }
        }
        updateHandPanelUI();
    }

    public void updateTimer(String data) {
        if (data == null) return;
        String[] times = data.split(",");
        if (times.length == 4) {
            for (int i = 0; i < 4; i++) {
                timerLabels[i].setText(formatTime(Integer.parseInt(times[i])));
            }
        }
    }

    // 확장된 TIME_UPDATE2: RED=180;BLUE=120;...
    public void updateTimerV2(String data) {
        if (data == null) return;
        String[] pairs = data.split(";");
        Map<String, Integer> map = new HashMap<>();
        for (String p : pairs) {
            String[] kv = p.split("=");
            if (kv.length == 2) {
                try {
                    map.put(kv[0], Integer.parseInt(kv[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
        // 표시는 그대로 기존 timerLabels 활용
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

        handPanel.revalidate();
        handPanel.repaint();
        handScrollPane.revalidate();
    }

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
                if (selectedPiece != null && isGhostValid && isMyTurn()) {
                    String message = String.format("%s:%s:%d:%d:%d",
                            Protocol.C2S_PLACE_BLOCK,
                            selectedPiece.getId(),
                            mouseGridPos.x,
                            mouseGridPos.y,
                            currentRotation
                    );
                    client.sendMessage(message);
                    deselectPiece();
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

    private void deselectPiece() {
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
        chatArea.append(data.replaceFirst(":", ": ") + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public void clearChat() {
        chatArea.setText("");
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

    // ColorIndicatorPanel
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

    // PiecePreviewPanel
    private class PiecePreviewPanel extends JPanel {
        private BlokusPiece piece;
        private boolean isSelected = false;
        private final int PREVIEW_CELL_SIZE = 8;
        private final Border selectedBorder = BorderFactory.createLineBorder(Color.CYAN, 3);
        private final Border defaultBorder = BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1);

        public PiecePreviewPanel(BlokusPiece piece) {
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
            int w = piece.getWidth() * PREVIEW_CELL_SIZE + 6;
            int h = piece.getHeight() * PREVIEW_CELL_SIZE + 6;
            setPreferredSize(new Dimension(Math.max(w, 40), Math.max(h, 40)));
            revalidate();
            repaint();
        }

        public void resetRotation(String originalId) {
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

            boolean active = (piece.getColor() == currentTurnColor);
            if (!active) {
                g.setColor(new Color(255, 255, 255, 180));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }
}