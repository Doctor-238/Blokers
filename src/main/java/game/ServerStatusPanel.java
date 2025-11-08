package game.ui;

import game.ProtocolExt;

import javax.swing.*;
import java.awt.*;

public class ServerStatusPanel extends JPanel {
    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final JList<String> users = new JList<>(usersModel);

    private final DefaultListModel<String> roomsModel = new DefaultListModel<>();
    private final JList<String> rooms = new JList<>(roomsModel);

    private final JButton banButton = new JButton("밴");
    private final JButton refreshButton = new JButton("새로고침");
    private final JButton pwdChangeButton = new JButton("비밀번호 변경");

    private final java.util.function.Consumer<String> send; // client.sendMessage 래핑

    private boolean isAdmin = false;

    public ServerStatusPanel(java.util.function.Consumer<String> sender) {
        this.send = sender;
        setLayout(new BorderLayout());

        JPanel lists = new JPanel(new GridLayout(1,2,8,0));
        lists.add(new JScrollPane(users));
        lists.add(new JScrollPane(rooms));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("접속자 / 방 현황"));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        refreshButton.addActionListener(e -> {
            send.accept(ProtocolExt.C2S_GET_USER_LIST);
            // 방 목록은 기존 C2S_GET_ROOM_LIST 사용
            send.accept(game.Protocol.C2S_GET_ROOM_LIST);
        });
        banButton.addActionListener(e -> {
            String sel = users.getSelectedValue();
            if (sel != null) {
                String target = sel.split("\\s")[0];
                int c = JOptionPane.showConfirmDialog(this, target + " 을(를) 밴하시겠습니까?", "확인", JOptionPane.YES_NO_OPTION);
                if (c == JOptionPane.YES_OPTION) send.accept(ProtocolExt.C2S_BAN_USER + ":" + target);
            }
        });
        pwdChangeButton.addActionListener(e -> {
            JPasswordField oldF = new JPasswordField();
            JPasswordField newF = new JPasswordField();
            Object[] msg = {"현재 비밀번호:", oldF, "새 비밀번호:", newF};
            int c = JOptionPane.showConfirmDialog(this, msg, "비밀번호 변경", JOptionPane.OK_CANCEL_OPTION);
            if (c == JOptionPane.OK_OPTION) {
                String oldP = new String(oldF.getPassword());
                String newP = new String(newF.getPassword());
                send.accept(ProtocolExt.C2S_CHANGE_PASSWORD + ":" + oldP + "|" + newP);
            }
        });
        bottom.add(pwdChangeButton);
        bottom.add(refreshButton);
        bottom.add(banButton);

        add(top, BorderLayout.NORTH);
        add(lists, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        updateAdmin(false);
    }

    public void updateAdmin(boolean admin) {
        isAdmin = admin;
        banButton.setEnabled(isAdmin);
    }

    public void setUsers(java.util.List<String> list) {
        usersModel.clear();
        for (String s : list) usersModel.addElement(s);
    }

    public void setRooms(java.util.List<String> list) {
        roomsModel.clear();
        for (String s : list) roomsModel.addElement(s);
    }
}