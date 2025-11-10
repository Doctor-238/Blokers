package game;

/**
 * 서버와 클라이언트 간의 통신 프로토콜 정의
 * C2S: Client to Server
 * S2C: Server to Client
 */
public class Protocol {

    // C2S: 클라이언트 -> 서버 (명령어 끝에 : 제거)
    public static final String C2S_LOGIN = "LOGIN";
    public static final String C2S_GET_ROOM_LIST = "GET_ROOM_LIST";
    public static final String C2S_CREATE_ROOM = "CREATE_ROOM";
    public static final String C2S_JOIN_ROOM = "JOIN_ROOM";
    public static final String C2S_LEAVE_ROOM = "LEAVE_ROOM";
    public static final String C2S_START_GAME = "START_GAME";
    public static final String C2S_KICK_PLAYER = "KICK";
    public static final String C2S_PLACE_BLOCK = "PLACE";
    public static final String C2S_PASS_TURN = "PASS_TURN";
    public static final String C2S_CHAT = "CHAT";
    public static final String C2S_GET_LEADERBOARD = "GET_LEADERBOARD"; // (1번) 추가

    // (기권) C2S_RESIGN -> C2S_RESIGN_COLOR (어떤 색상을 기권하는지 명시)
    public static final String C2S_RESIGN_COLOR = "RESIGN_COLOR";

    // S2C: 서버 -> 클라이언트 (명령어 끝에 : 제거)
    public static final String S2C_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String S2C_LOGIN_FAIL = "LOGIN_FAIL";
    public static final String S2C_ROOM_LIST = "ROOM_LIST";
    public static final String S2C_JOIN_SUCCESS = "JOIN_SUCCESS";
    public static final String S2C_JOIN_FAIL = "JOIN_FAIL";
    public static final String S2C_ROOM_UPDATE = "ROOM_UPDATE";
    public static final String S2C_KICKED = "KICKED";
    public static final String S2C_GAME_START = "GAME_START";
    public static final String S2C_GAME_STATE = "GAME_STATE";
    public static final String S2C_HAND_UPDATE = "HAND_UPDATE";
    public static final String S2C_VALID_MOVE = "VALID_MOVE";
    public static final String S2C_INVALID_MOVE = "INVALID_MOVE";
    public static final String S2C_GAME_OVER = "GAME_OVER";
    public static final String S2C_CHAT = "CHAT";
    public static final String S2C_SYSTEM_MSG = "SYSTEM_MSG";
    public static final String S2C_LEADERBOARD_DATA = "LEADERBOARD_DATA"; // (1번) 추가


    // 시간제한 프로토콜
    public static final String S2C_TIME_UPDATE = "TIME_UPDATE"; // TIME_UPDATE:<r_sec>,<b_sec>,<y_sec>,<g_sec>

}