package game;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    // (기권) 내가 보유한 활성 색상 추적 (1v1 기권 로직용)
    private Set<Integer> myActiveColors = new HashSet<>();

    private Point mouseGridPos = new Point(-1, -1);
    private boolean isGhostValid = false;
    private boolean amISpectating = false; // (기권) 관전 모드 플래그

    private JPanel boardPanel;
    private JPanel handPanel;
    private JScrollPane handScrollPane;

    private JLabel turnLabel;
    private JButton toggleColorButton;
    private JButton rotateButton; // (기권) 버튼 참조를 위해 인스턴스 변수로
    private JButton passButton;   // (기권) 버튼 참조를 위해 인스턴스 변수로
    private JButton resignButton; // (기권) 버튼 참조를 위해 인스턴스 변수로

    private ColorIndicatorPanel currentTurnIndicator;
    private JPanel turnInfoPanel;
    private JPanel turnBlockAndTimer; // (1번) 타이머 교체를 위해 인스턴스 변수로
    private JPanel myColorsInfoPanel;
    private JLabel scoreLabel;

    private JLabel[] timerLabels = new JLabel[4];
    // private JPanel turnTimerContainer; // (1번) 불필요한 컨테이너 제거

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
        // (1번) 인스턴스 변수로 변경
        turnBlockAndTimer = new JPanel(new BorderLayout(0, 2));
        turnBlockAndTimer.addMouseListener(backgroundClickListener);
        currentTurnIndicator = new ColorIndicatorPanel(Color.GRAY);
        turnBlockAndTimer.add(currentTurnIndicator, BorderLayout.NORTH); // 블록

        // 4. "타이머 자리 표시자" (BorderLayout) - (점프 현상 방지)
        // (1번) turnTimerContainer 제거, timerLabels[0] (Red)을 기본 플레이스홀더로 사용
        turnBlockAndTimer.add(timerLabels[0], BorderLayout.CENTER); // 타이머 컨테이너

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

        // (기권) 버튼들 인스턴스 변수로 초기화
        rotateButton = new JButton("회전 (r/ㄱ)");
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

        // (기권) 기권 버튼 추가
        resignButton = new JButton("기권");
        resignButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleResign();
            }
        });

        controlPanel.add(rotateButton);
        controlPanel.add(passButton);
        controlPanel.add(resignButton); // (기권) 패널에 추가
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
        toggleColorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleInventoryColor();
            }
        });
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

        // (3번) WASD 네비게이션 바인딩 추가
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
        String[] parts = data.split(":");
        String[] colorsStr = parts[1].split(",");

        myColors = new int[colorsStr.length];

        // (기권) 나의 활성 색상 Set 초기화
        myActiveColors.clear();

        myColorsInfoPanel.removeAll();
        myColorsInfoPanel.add(new JLabel("내 색상: "));
        for (int i = 0; i < colorsStr.length; i++) {
            myColors[i] = Integer.parseInt(colorsStr[i]);
            int colorNum = myColors[i];

            myActiveColors.add(colorNum); // (기권) Set에 추가

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

        // (기권) 게임 시작 시 관전 모드 해제 및 버튼 활성화
        setSpectateMode(false);
        updateTimerVisibility();
    }

    // (기권) 관전 모드 설정 및 UI 갱신 (요청사항 반영)
    private void setSpectateMode(boolean spectating) {
        this.amISpectating = spectating;

        // 관전 모드일 때 조작 버튼 비활성화 (회전, 색상전환 제외)
        passButton.setEnabled(!spectating);
        resignButton.setEnabled(!spectating);

        // (기권) 회전, 색상전환은 관전 중에도 활성화
        rotateButton.setEnabled(true);
        toggleColorButton.setEnabled(myColors.length > 1);
        toggleColorButton.setVisible(myColors.length > 1); // 혹시 모르니

        // 관전 모드가 되면 블록 선택 해제 및 갱신
        if (spectating) {
            deselectPiece();
        }

        handPanel.repaint();
    }

    // (기권) 기권 처리 로직 (1v1 분기 처리)
    private void handleResign() {
        int result = JOptionPane.showConfirmDialog(this,
                "정말 기권하시겠습니까?\n(현재 턴의 색상만 기권됩니다)", "기권 확인", JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            int colorToResign = currentTurnColor;

            // 1. 서버에 '현재 턴 색상' 기권 메시지 전송
            client.sendMessage(Protocol.C2S_RESIGN_COLOR + ":" + colorToResign);

            // 2. 클라이언트 측 활성 색상 목록에서 즉시 제거 (예측)
            myActiveColors.remove(colorToResign);

            // 3. 나의 활성 색상이 0개가 되었는지 확인
            if (myActiveColors.isEmpty()) {
                // 4. 모든 색상이 탈락했다면 관전 여부 확인
                int spectateResult = JOptionPane.showConfirmDialog(this,
                        "모든 색상을 기권했습니다. 관전하시겠습니까? (채팅 가능)", "관전", JOptionPane.YES_NO_OPTION);

                if (spectateResult == JOptionPane.YES_OPTION) {
                    // 5. 관전 모드 활성화
                    setSpectateMode(true);
                } else {
                    // 6. 방 나가기 (로비로)
                    client.sendMessage(Protocol.C2S_LEAVE_ROOM);
                }
            }
            // 7. (else) 1v1이고 아직 색상이 남았다면, 아무것도 묻지 않고 게임 계속
        }
    }

    public void updateGameState(String data) {
        if (data == null) return;
        String[] parts = data.split(":");
        if (parts.length < 3) return;

        // (2번) 수정: 턴 컬러가 바뀌기 전, 현재 턴 컬러를 oldTurnColor에 저장
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

        // (1번) 타이머 교체 로직 수정
        // 1. 기존 타이머(JLabel)를 turnBlockAndTimer에서 제거
        for (Component c : turnBlockAndTimer.getComponents()) {
            if (c instanceof JLabel) {
                turnBlockAndTimer.remove(c);
                break; // 하나만 제거
            }
        }

        // 2. 새 타이머가 다른 곳(myColorsInfoPanel)에 있다면 부모로부터 제거
        JLabel newTurnTimer = timerLabels[newTurnColor - 1];
        if (newTurnTimer.getParent() != null) {
            ((Container)newTurnTimer.getParent()).remove(newTurnTimer);
        }

        // 3. turnBlockAndTimer에 새 타이머 추가
        turnBlockAndTimer.add(newTurnTimer, BorderLayout.CENTER);

        // (2번) this.currentTurnColor 갱신
        this.currentTurnColor = newTurnColor;


        // (1번) myColorsInfoPanel 갱신 (여기도 타이머를 포함하므로)
        myColorsInfoPanel.removeAll();
        myColorsInfoPanel.add(new JLabel("내 색상: "));
        for (int myColor : myColors) {
            JPanel myColorGroup = new JPanel(new BorderLayout(0, 2));
            myColorGroup.add(new ColorIndicatorPanel(getColorForPlayer(myColor)), BorderLayout.NORTH);
            JLabel timer = timerLabels[myColor - 1];
            if (timer.getParent() != null) { // (1번) 새 턴 타이머가 여기 있었을 수 있으므로
                ((Container)timer.getParent()).remove(timer);
            }
            myColorGroup.add(timer, BorderLayout.CENTER);
            myColorsInfoPanel.add(myColorGroup);
        }

        // (1번) `turnTimerContainer` 관련 코드 제거
        // (1번) 부모 패널 갱신
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

        // (2번) 수정: oldTurnColorCheck 로직 수정
        boolean oldTurnColorCheck = (oldTurnColor != newTurnColor);
        if (inventoryFilterChanged) {
            updateHandPanelUI();
        } else if (oldTurnColorCheck) { // 턴이 바뀌었으면 (색상 필터 변경이 아니더라도)
            handPanel.repaint(); // (2번) handPanel을 repaint하여 블록 활성화/비활성화
        }

        boardPanel.repaint();

        // (기권) 턴 갱신 시 버튼 상태 갱신
        updateButtonStates();
    }

    // (기권) 턴/관전 상태에 따라 버튼 활성화/비활성화
    private void updateButtonStates() {
        if (amISpectating) {
            // 관전 모드일 때
            passButton.setEnabled(false);
            resignButton.setEnabled(false);
            rotateButton.setEnabled(true);
            toggleColorButton.setEnabled(myColors.length > 1);
        } else {
            // 플레이 중일 때
            boolean myTurn = isMyTurn();
            passButton.setEnabled(myTurn);
            resignButton.setEnabled(myTurn);
            rotateButton.setEnabled(true); // 회전은 항상 활성화
            toggleColorButton.setEnabled(myColors.length > 1); // 색상 전환도 항상 활성화
        }
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

        // (1번) 타이머 가시성 로직은 '현재 턴' 타이머(가 부모 패널에 있음)와
        // '내 색상' 타이머(가 '내 색상' 패널에 있음)를 구분해야 함.
        // updateGameState에서 컴포넌트를 올바른 부모에게 재배치하므로,
        // 여기서는 visibility만 설정하면 됨.

        // 1. '현재 턴' 위치에 있는 타이머
        JLabel turnTimer = timerLabels[currentTurnColor - 1];
        // 내 턴일 경우, '현재 턴'의 타이머는 숨김 (내 색상 쪽에만 표시)
        turnTimer.setVisible(!turnIsMyColor);


        // 2. '내 색상' 위치에 있는 타이머
        for (int myColor : myColors) {
            timerLabels[myColor - 1].setVisible(true);
        }


        // 3. 그 외 타이머 ('현재 턴'도 아니고 '내 색상'도 아닌) - 숨김
        for (int i = 0; i < 4; i++) {
            int color = i + 1;
            if (color == currentTurnColor) continue; // 1번에서 처리

            boolean isMyColor = false;
            for (int c : myColors) {
                if (c == color) {
                    isMyColor = true;
                    break;
                }
            }
            if (isMyColor) continue; // 2번에서 처리

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

        // (3번) WASD 네비게이션을 위해 정렬된 순서로 추가 (항상 보장되진 않음)
        // -> 어차피 handleNavigate에서 Y/X 기준으로 정렬하므로 상관 없음
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

                if (!isMyTurn()) { // isMyTurn()이 false를 반환 (관전 중)
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
            // (2번) 회전 상태 유지를 위해 resetRotation 제거
            // selectedPanel.resetRotation(selectedPiece.getId());
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

    // (기권) 시스템 메시지에서 색상 탈락/기권/초과 파싱
    private void parseColorOut(String message) {
        int colorToRemove = -1;
        if (message.contains("Red") || message.contains("빨강")) colorToRemove = 1;
        else if (message.contains("Blue") || message.contains("파랑")) colorToRemove = 2;
        else if (message.contains("Yellow") || message.contains("노랑")) colorToRemove = 3;
        else if (message.contains("Green") || message.contains("초록")) colorToRemove = 4;

        if (colorToRemove != -1) {
            myActiveColors.remove(colorToRemove);

            // (기권) 서버 메시지로 인해 내 색상이 모두 탈락했는지 확인
            if (myActiveColors.isEmpty() && !amISpectating) {
                setSpectateMode(true);
                JOptionPane.showMessageDialog(this, "모든 색상이 탈락하여 관전 모드로 자동 전환됩니다.", "관전", JOptionPane.INFORMATION_MESSAGE);
            }
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

                // (기권) Check for color out messages
                if (message.contains("탈락했습니다") || message.contains("시간이 초과되어") || message.contains("기권했습니다")) {
                    parseColorOut(message);
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
        // (기권) 관전 중이면 내 턴이 아님
        if (amISpectating) return false;

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

    // (3번) WASD 네비게이션 로직
    private void handleNavigate(String direction) {
        if (chatField.isFocusOwner()) {
            return;
        }

        // (기권) 블록 미선택 시 첫 번째 블록 선택
        if (selectedPanel == null) {
            if (handPanel.getComponentCount() > 0) {
                Component first = handPanel.getComponent(0);
                if (first instanceof PiecePreviewPanel) {
                    PiecePreviewPanel targetPanel = (PiecePreviewPanel) first;

                    // (기권) 첫 번째 블록 선택 로직
                    targetPanel.setSelected(true);
                    selectedPanel = targetPanel;
                    selectedPiece = new BlokusPiece(targetPanel.piece);
                    currentRotation = 0;
                    isGhostValid = checkLocalPlacement(selectedPiece, mouseGridPos.x, mouseGridPos.y);
                    boardPanel.repaint();
                }
            }
            return; // 첫 블록 선택 후 종료 (네비게이션 X)
        }

        Component[] components = handPanel.getComponents();
        if (components.length <= 1) return;

        // 1. 컴포넌트를 Y좌표(행) 기준으로 그룹화하고, 각 행을 X좌표 기준으로 정렬
        Map<Integer, List<Component>> rows = new TreeMap<>();
        for (Component c : components) {
            if (c instanceof PiecePreviewPanel) {
                rows.computeIfAbsent(c.getY(), k -> new ArrayList<>()).add(c);
            }
        }

        // 각 행 내부를 X좌표 기준으로 정렬
        for(List<Component> row : rows.values()) {
            row.sort(Comparator.comparingInt(Component::getX));
        }

        List<List<Component>> rowList = new ArrayList<>(rows.values());
        int currentRowIndex = -1;
        int currentInRowIndex = -1;
        int currentX = selectedPanel.getX();

        // 2. 현재 선택된 패널의 행/열 인덱스 찾기
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

        if (currentRowIndex == -1) return; // 찾지 못함 (오류)

        // 3. 방향에 따라 타겟 컴포넌트 찾기
        Component targetComponent = null;
        List<Component> currentRow = rowList.get(currentRowIndex);

        switch (direction) {
            case "left": // 'A'
                if (currentInRowIndex > 0) {
                    targetComponent = currentRow.get(currentInRowIndex - 1);
                }
                break;
            case "right": // 'D'
                if (currentInRowIndex < currentRow.size() - 1) {
                    targetComponent = currentRow.get(currentInRowIndex + 1);
                }
                break;
            case "up": // 'W'
                if (currentRowIndex > 0) {
                    List<Component> prevRow = rowList.get(currentRowIndex - 1);
                    targetComponent = findClosestX(prevRow, currentX);
                }
                break;
            case "down": // 'S'
                if (currentRowIndex < rowList.size() - 1) {
                    List<Component> nextRow = rowList.get(currentRowIndex + 1);
                    targetComponent = findClosestX(nextRow, currentX);
                }
                break;
        }

        // 4. 타겟 컴포넌트가 있다면 (새로운 블록) 선택
        if (targetComponent != null && targetComponent instanceof PiecePreviewPanel) {
            PiecePreviewPanel targetPanel = (PiecePreviewPanel) targetComponent;

            // 4.1. 기존 블록 선택 해제
            if (selectedPanel != null) {
                selectedPanel.setSelected(false);
                // (2번) 회전 상태는 유지
            }

            // 4.2. 새 블록 선택
            targetPanel.setSelected(true);
            selectedPanel = targetPanel;
            // (3번) PiecePreviewPanel의 piece는 이미 회전된 상태일 수 있음
            // 이를 그대로 복사하고, 로컬 회전 카운터만 0으로 리셋
            selectedPiece = new BlokusPiece(targetPanel.piece);
            currentRotation = 0;

            // (1번) 수정: WASD로 블록 변경 시 고스트 갱신
            isGhostValid = checkLocalPlacement(selectedPiece, mouseGridPos.x, mouseGridPos.y);
            boardPanel.repaint();
        }
    }

    // (3번) WASD 네비게이션 헬퍼: 주어진 행(row)에서 targetX와 가장 가까운 컴포넌트 찾기
    private Component findClosestX(List<Component> row, int targetX) {
        Component bestMatch = null;
        int minDiff = Integer.MAX_VALUE;

        // 중앙 정렬을 고려하여, X좌표 + (너비/2) 를 기준으로 비교
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
        public BlokusPiece piece; // (3번) WASD 접근을 위해 public으로 변경
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
                            // (2번) 회전 상태 유지를 위해 resetRotation 제거
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

            // (2번) 수정: 턴 컬러와 조각 컬러가 일치하는지 확인
            boolean active = (piece.getColor() == currentTurnColor);

            // (기권) 관전 중일 때는 (내 턴이 아니므로) 모든 블록을 비활성화
            if (!active || amISpectating) {
                // (기권) 1v1 관전 중 인벤토리 색상 전환 시,
                // 현재 턴이 아니더라도 inventoryDisplayColor와 일치하면 활성화된 것처럼 보임
                // -> currentTurnColor 대신 inventoryDisplayColor로 활성화 여부 판단
                boolean displayActive = (piece.getColor() == inventoryDisplayColor);

                // 관전 모드이거나, 현재 턴의 색상이 아니면 비활성화
                if (amISpectating || !active) {
                    // 단, 관전 모드일 때 '내 블록 색상 전환'을 누른 경우
                    // inventoryDisplayColor는 활성화된 것처럼 보여야 함 (요청사항)
                    if (amISpectating && displayActive) {
                        // 활성화 (아무것도 덮어쓰지 않음)
                    } else {
                        // 비활성화 (반투명 오버레이)
                        g.setColor(new Color(255, 255, 255, 180));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                }
            }
        }
    }
}