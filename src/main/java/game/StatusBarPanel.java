package game.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatusBarPanel extends JPanel {
    private final JLabel label = new JLabel();
    private volatile String myColor = "";
    private volatile String turnColor = "";
    private final Map<String, Integer> secondsLeft = new ConcurrentHashMap<>();

    public StatusBarPanel() {
        setLayout(new BorderLayout());
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(label, BorderLayout.CENTER);
        refresh();
    }

    public void setMyColor(String color) {
        this.myColor = color == null ? "" : color;
        refresh();
    }

    public void setTurnColor(String color) {
        this.turnColor = color == null ? "" : color;
        refresh();
    }

    public void setSecondsLeft(String color, int sec) {
        if (color != null) {
            secondsLeft.put(color, Math.max(0, sec));
            refresh();
        }
    }

    public void setAllSeconds(Map<String, Integer> map) {
        secondsLeft.clear();
        if (map != null) secondsLeft.putAll(map);
        refresh();
    }

    private static String fmt(int sec) {
        int m = sec / 60, s = sec % 60;
        return String.format("%02d:%02d", m, s);
    }

    private Color mix(Color a, Color b, double t) {
        return new Color(
                (int)(a.getRed() * (1-t) + b.getRed() * t),
                (int)(a.getGreen() * (1-t) + b.getGreen() * t),
                (int)(a.getBlue() * (1-t) + b.getBlue() * t)
        );
    }

    private void refresh() {
        String myTime = secondsLeft.containsKey(myColor) ? fmt(secondsLeft.get(myColor)) : "--:--";
        String turnTime = secondsLeft.containsKey(turnColor) ? fmt(secondsLeft.get(turnColor)) : "--:--";

        StringBuilder sb = new StringBuilder();
        sb.append("현재 턴: ").append(turnColor.isEmpty() ? "-" : turnColor);
        sb.append("  |  내 색상: ").append(myColor.isEmpty() ? "-" : myColor);
        sb.append("  |  내 시간: ").append(myTime);

        if (!turnColor.isEmpty() && !turnColor.equals(myColor)) {
            sb.append("  |  ").append(turnColor).append(" 시간: ").append(turnTime);
        }

        label.setText(sb.toString());

        boolean myTurn = !myColor.isEmpty() && myColor.equals(turnColor);
        setBackground(myTurn ? mix(new Color(0xDFFFD6), Color.WHITE, 0.2) : Color.WHITE);
        label.setForeground(myTurn ? new Color(0x0F6B00) : Color.DARK_GRAY);
        repaint();
    }
}