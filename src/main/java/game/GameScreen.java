package game;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameScreen extends JPanel {

    private BlokusClient client;

    // 보드가 20×20 칸
    private static final int BOARD_SIZE = 20;
    // 각 칸 픽셀 크기 25
    private static final int CELL_SIZE = 25;
    // 전체 보드 패널 픽셀 크기 = 25 × 20 = 500
    private static final int BOARD_PANEL_SIZE = BOARD_SIZE * CELL_SIZE; // 500x500
    // 실제 보드판
    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    // 플레이어가 가지고 있는 조각 리스트
    private List<BlokusPiece> myHand = new ArrayList<>();
    // 조각 미리보기 패널을 매핑해 두는 캐시
    private Map<String, PiecePreviewPanel> handPanelCache = new HashMap<>();
    // 현재 선택한 조각
    private BlokusPiece selectedPiece = null;
    // 선택된 블록의 미리보기
    private PiecePreviewPanel selectedPanel = null;
    // 현재 블록의 회전 수
    private int currentRotation = 0;
    // 플레이어가 담당하는 색
    private int[] myColors = new int[0];
    // 인벤토리에서 보여줄 색 조각
    private int inventoryDisplayColor = 0;
    // 지금 턴인 색
    private int currentTurnColor = 0;
    // 현재 보드에서의 마우스 좌표
    private Point mouseGridPos = new Point(-1, -1);
    // 미리보기의 유효성 검증 변수
    private boolean isGhostValid = false;

    //보드 GUI
    private JPanel boardPanel;
    //조각 미리보기 패널
    private JPanel handPanel;
    private JScrollPane handScrollPane;

    private JLabel turnLabel;
    // 2인용 게임에서 색 전환 버튼
    private JButton toggleColorButton;
    // 지금 턴 색을 칸 색깔로 보여주는 패널
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

        // 상단 패널, 정보 + 조작 버튼
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));

        // 정보 패널, 턴, 내 색상, 타이머
        JPanel infoPanel = new JPanel(new BorderLayout(10, 5));

        // 턴 안내 패널
        JPanel turnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        turnLabel = new JLabel("게임 대기 중...");
        turnLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        currentTurnIndicator = new ColorIndicatorPanel(Color.GRAY);
        turnPanel.add(new JLabel("현재 턴: "));
        turnPanel.add(currentTurnIndicator);
        turnPanel.add(turnLabel);
        infoPanel.add(turnPanel, BorderLayout.NORTH);

        // 색 패널
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

        // 조작 패널, 회전, 턴 넘기기
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton rotateButton = new JButton("회전");
        rotateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rotateSelectedPiece();
            }
        });

        JButton passButton = new JButton("턴 넘기기");
        passButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.sendMessage("PASS_TURN");
                deselectPiece();
            }
        });

        controlPanel.add(rotateButton);
        controlPanel.add(passButton);
        topPanel.add(controlPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        //게임 보드 패널
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

        //하단 패널, 인벤토리 + 토글 버튼
        JPanel southPanel = new JPanel(new BorderLayout(5, 5));

        toggleColorButton = new JButton("내 블록 색상 전환");
        toggleColorButton.setVisible(false);
        toggleColorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleInventoryColor();
            }
        });
        southPanel.add(toggleColorButton, BorderLayout.NORTH);

        handPanel = new JPanel(new WrapLayout(WrapLayout.LEFT, 5, 5));
        handPanel.setBackground(Color.WHITE);
        handScrollPane = new JScrollPane(handPanel);
        handScrollPane.setPreferredSize(new Dimension(800, 160)); // 2~3줄 높이

        southPanel.add(handScrollPane, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        // 채팅 패널
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
    }

    // 게임 시작 관련 ex) data = GAME_START:플레이어수:내색1,내색2
    public void initializeGame(String data) {
        String[] parts = data.split(":");
        String[] colorsStr = parts[1].split(",");

        // 현재 색을 파싱하여 myColors에 저장
        myColors = new int[colorsStr.length];
        myColorsPanel.removeAll();
        myColorsPanel.add(new JLabel("내 색상: "));
        for (int i=0; i < colorsStr.length; i++) {
            myColors[i] = Integer.parseInt(colorsStr[i]);
            myColorsPanel.add(new ColorIndicatorPanel(getColorForPlayer(myColors[i])));
        }
        myColorsPanel.revalidate();
        myColorsPanel.repaint();

        // 색이 2 이상이면 토글 버튼을 킨다
        toggleColorButton.setVisible(myColors.length > 1);
        inventoryDisplayColor = (myColors.length > 0) ? myColors[0] : 0;

        // 게임 시작시 전체 데이터 초기화
        board = new int[BOARD_SIZE][BOARD_SIZE];
        myHand.clear();
        handPanelCache.clear();
        handPanel.removeAll();
        deselectPiece();

        // 시간 초기화
        for (int i=0; i<4; i++) {
            timerLabels[i].setText(formatTime(300));
        }
    }

    // 게임 상태 업데이트
    public void updateGameState(String data) {
        if (data == null) return;
        String[] parts = data.split(":");
        if (parts.length < 3) return;

        // 보드의 정보를 담고 있는 직렬화된 문자열
        String boardData = parts[0];
        // 현재 플레이어 이름
        String currentPlayerName = parts[1].split(" ")[0];
        // 현재 턴 색 번홀
        int newTurnColor = Integer.parseInt(parts[2]);
        // 1자원 문자열 배열을 2차원 정수 배열로 복구
        String[] cells = boardData.split(",");
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                board[r][c] = Integer.parseInt(cells[r * BOARD_SIZE + c]);
            }
        }
        // 현재 턴과 색 표시
        turnLabel.setText(currentPlayerName);
        currentTurnIndicator.setColor(getColorForPlayer(newTurnColor));
        // 이전 턴 색 저장 후 업데이터
        int oldTurnColor = this.currentTurnColor;
        this.currentTurnColor = newTurnColor;
        // 내 턴인지 체크
        boolean myTurn = false;
        for (int c : myColors) {
            if (c == newTurnColor) {
                myTurn = true;
                break;
            }
        }
        boolean inventoryFilterChanged = false;
        //내 턴이면서 현재 턴 색이 아니면
        if (myTurn && this.inventoryDisplayColor != newTurnColor) {
            //현재 색으로 전환
            this.inventoryDisplayColor = newTurnColor;
            inventoryFilterChanged = true;
        }
        //인벤토리 색이 바뀌었으면
        if (inventoryFilterChanged) {
            updateHandPanelUI();
        // 안바뀌었으면
        } else if (oldTurnColor != newTurnColor) {
            handPanel.repaint(); // 턴만 바뀐 경우 (활성/비활성)
        }

        boardPanel.repaint();
    }

    // 플레이어 블록 목록 갱신
    public void updatePlayerHand(String data) {
        myHand.clear();
        if (data != null && !data.isEmpty()) {
            // 데이터 파싱
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

    // 각 색깔 타이머 갱신 data = "TIME_UPDATE:r,b,y,g"
    public void updateTimer(String data) {
        if (data == null) return;
        String[] times = data.split(",");
        if (times.length == 4) {
            for(int i=0; i<4; i++) {
                timerLabels[i].setText(formatTime(Integer.parseInt(times[i])));
            }
        }
    }

    // 시간 출력 변경
    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // 인벤토리가 inventoryDisplayColor만 출력
    private void updateHandPanelUI() {
        // 기존 값 제거
        handPanel.removeAll();
        handPanelCache.clear();

        // 목록 중에 같은 조각만 인벤토리에 그림
        for (BlokusPiece piece : myHand) {
            if (piece.getColor() == this.inventoryDisplayColor) {
                PiecePreviewPanel pp = new PiecePreviewPanel(piece);
                String key = piece.getId() + "/" + piece.getColor();
                handPanelCache.put(key, pp);
                handPanel.add(pp);
            }
        }

        // 색이 아니라면 선택 해제
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

                // 해당 칸이 0이 아니면 채우기
                if (board[r][c] != 0) {
                    g2d.setColor(getColorForPlayer(board[r][c]));
                    g2d.fillRect(c * CELL_SIZE + 1, r * CELL_SIZE + 1, CELL_SIZE - 2, CELL_SIZE - 2);
                }
            }
        }

        // 시작 코너 표시
        drawCornerMarker(g2d, 0, 0, getColorForPlayer(1));  // Red
        drawCornerMarker(g2d, 19, 0, getColorForPlayer(2)); // Blue
        drawCornerMarker(g2d, 19, 19, getColorForPlayer(3)); // Yellow
        drawCornerMarker(g2d, 0, 19, getColorForPlayer(4)); // Green
    }

    // 시작 코너 마커 그리기
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
        // 조각 선택 안했거나 격자에 없으면
        if (selectedPiece == null || mouseGridPos.x == -1) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g;
        // 블록의 내부 좌표들
        List<Point> points = selectedPiece.getPoints();
        // 선택한 블록 색상
        Color ghostColor = getColorForPlayer(selectedPiece.getColor());
        // 블록이 유효하다면 초록색으로 알파값 128로 반투명하게
        if (isGhostValid) {
            g2d.setColor(new Color(ghostColor.getRed(), ghostColor.getGreen(), ghostColor.getBlue(), 128));
            // 블록이 유효하지 않다면 초록색으로 알파값 128로 반투명하게
        } else {
            g2d.setColor(new Color(255, 0, 0, 128));
        }

        for (Point p : points) {
            int drawX = (mouseGridPos.x + p.x) * CELL_SIZE;
            int drawY = (mouseGridPos.y + p.y) * CELL_SIZE;
            g2d.fillRect(drawX, drawY, CELL_SIZE, CELL_SIZE);
        }
    }

    // 로컬 유효성 검사
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
                boardPanel.requestFocusInWindow();

                if (e.getButton() == MouseEvent.BUTTON3) { // 우클릭
                    deselectPiece();
                    return;
                }

                if (selectedPiece != null && isGhostValid && isMyTurn()) {
                    String message = String.format("PLACE:%s:%d:%d:%d",
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

    // 인벤토리, 고스트 회전
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
            selectedPanel.resetRotation(selectedPiece.getId());
        }
        selectedPiece = null;
        selectedPanel = null;
        currentRotation = 0;
        mouseGridPos.setLocation(-1, -1);
        boardPanel.repaint();
    }

    // 2인용 인벤토리 토글
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

    // 플레이어 색상 반환 로직
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
            // Protocol.C2S_CHAT -> "CHAT"
            client.sendMessage("CHAT:" + message);
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

    // 인벤토리용 블록 조각 시각화 패널
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

            boolean isActive = (piece.getColor() == currentTurnColor);
            if (!isActive) {
                g.setColor(new Color(255, 255, 255, 180));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }
}
