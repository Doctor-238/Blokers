package game;

public final class ProtocolExt {
    private ProtocolExt() {}

    // Auth
    public static final String C2S_SIGNUP = "C2S_SIGNUP";           // data: username|password
    public static final String S2C_SIGNUP_SUCCESS = "S2C_SIGNUP_SUCCESS";
    public static final String S2C_SIGNUP_FAIL = "S2C_SIGNUP_FAIL"; // reason

    // 기존 LOGIN은 username만이었음 -> password 포함으로 확장 버전 추가(기존과 병행 가능)
    public static final String C2S_LOGIN2 = "C2S_LOGIN2";           // data: username|password

    public static final String C2S_CHANGE_PASSWORD = "C2S_CHANGE_PASSWORD"; // data: old|new
    public static final String S2C_CHANGE_PASSWORD_OK = "S2C_CHANGE_PASSWORD_OK";
    public static final String S2C_CHANGE_PASSWORD_FAIL = "S2C_CHANGE_PASSWORD_FAIL"; // reason

    // Users / Rooms
    public static final String C2S_GET_USER_LIST = "C2S_GET_USER_LIST";
    public static final String S2C_USER_LIST = "S2C_USER_LIST";     // data: [name,status];...
    // status: online|in_room|in_game|banned

    public static final String C2S_BAN_USER = "C2S_BAN_USER";       // data: targetName
    public static final String S2C_BAN_RESULT = "S2C_BAN_RESULT";   // data: ok|fail:reason

    // Game progress broadcast
    public static final String S2C_TURN_CHANGED = "S2C_TURN_CHANGED";         // data: color|username
    public static final String S2C_COLOR_ELIMINATED = "S2C_COLOR_ELIMINATED"; // data: color|reason

    // Resign/Spectate
    public static final String C2S_RESIGN = "C2S_RESIGN";           // no data
    public static final String C2S_SPECTATE = "C2S_SPECTATE";       // data: roomId

    // Timer (확장): color=seconds;... 형식 권장
    public static final String S2C_TIME_UPDATE2 = "S2C_TIME_UPDATE2";

    // Admin hint (선택): 누가 admin인지 알림
    public static final String S2C_YOU_ARE_ADMIN = "S2C_YOU_ARE_ADMIN";
}