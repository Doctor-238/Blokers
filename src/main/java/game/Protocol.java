package game;

public class Protocol {

    public static final int C2S_LOGIN = 0x01;
    public static final int S2C_LOGIN_SUCCESS = 0x02;
    public static final int S2C_LOGIN_FAIL = 0x03;

    public static final int C2S_GET_ROOM_LIST = 0x10;
    public static final int S2C_ROOM_LIST = 0x11;
    public static final int C2S_CREATE_ROOM = 0x12;
    public static final int C2S_JOIN_ROOM = 0x13;
    public static final int S2C_JOIN_SUCCESS = 0x14;
    public static final int S2C_JOIN_FAIL = 0x15;
    public static final int C2S_LEAVE_ROOM = 0x16;
    public static final int S2C_ROOM_UPDATE = 0x17;
    public static final int C2S_KICK_PLAYER = 0x18;
    public static final int S2C_KICKED = 0x19;

    public static final int C2S_START_GAME = 0x20;
    public static final int S2C_GAME_START = 0x21;
    public static final int S2C_GAME_STATE = 0x22;
    public static final int C2S_PLACE_BLOCK = 0x23;
    public static final int S2C_VALID_MOVE = 0x24;
    public static final int S2C_INVALID_MOVE = 0x25;
    public static final int S2C_HAND_UPDATE = 0x26;
    public static final int C2S_PASS_TURN = 0x27;
    public static final int S2C_TIME_UPDATE = 0x28;
    public static final int C2S_RESIGN_COLOR = 0x29;
    public static final int S2C_GAME_OVER = 0x2A;

    public static final int C2S_CHAT = 0x30;
    public static final int S2C_CHAT = 0x31;
    public static final int C2S_WHISPER = 0x32;
    public static final int S2C_WHISPER = 0x33;
    public static final int S2C_SYSTEM_MSG = 0x34;

    public static final int C2S_GET_LEADERBOARD = 0x40;
    public static final int S2C_LEADERBOARD_DATA = 0x41;

    public static final int S2C_GAME_START_PEERLESS = 0x50;
    public static final int S2C_PEERLESS_PREP_START = 0x51;
    public static final int S2C_PEERLESS_MAIN_START = 0x52;
    public static final int S2C_PEERLESS_TIMER_UPDATE = 0x53;
    public static final int S2C_PEERLESS_PREP_TIMER_UPDATE = 0x54;
    public static final int S2C_PEERLESS_PLACE_SUCCESS = 0x55;
    public static final int S2C_PEERLESS_PLACE_FAIL = 0x56;
    public static final int S2C_PEERLESS_BOARD_UPDATE = 0x57;
    public static final int C2S_RESIGN_PEERLESS = 0x58;
}