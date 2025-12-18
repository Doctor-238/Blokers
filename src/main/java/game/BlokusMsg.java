package game;

import java.io.Serializable;

public class BlokusMsg implements Serializable {
    private static final long serialVersionUID = 1L;

    private int code;
    private String username;
    private String data;

    public BlokusMsg(int code) {
        this(code, null, null);
    }

    public BlokusMsg(int code, String data) {
        this(code, null, data);
    }

    public BlokusMsg(int code, String username, String data) {
        this.code = code;
        this.username = username;
        this.data = data;
    }

    // Getters & Setters
    public int getCode() { return code; }
    public String getUsername() { return username; }
    public String getData() { return data; }

    @Override
    public String toString() {
        return "Msg{code=" + code + ", user=" + username + ", data=" + data + "}";
    }
}