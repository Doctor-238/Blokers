package game;

public class Protocol {

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
    public static final String C2S_GET_LEADERBOARD = "GET_LEADERBOARD";
    public static final String C2S_RESIGN_COLOR = "RESIGN_COLOR";
    public static final String C2S_WHISPER = "WHISPER";

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
    public static final String S2C_LEADERBOARD_DATA = "LEADERBOARD_DATA";
    public static final String S2C_WHISPER = "WHISPER";


    public static final String S2C_TIME_UPDATE = "TIME_UPDATE";

}