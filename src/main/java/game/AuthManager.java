package game.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {
    private final Map<String, String> userHash = new ConcurrentHashMap<>(); // username -> pwdHash
    private final Set<String> banned = ConcurrentHashMap.newKeySet();

    public boolean signup(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) return false;
        if (userHash.containsKey(username)) return false;
        userHash.put(username, hash(password));
        return true;
    }

    public boolean login(String username, String password) {
        if (!userHash.containsKey(username)) return false;
        if (isBanned(username)) return false;
        return userHash.get(username).equals(hash(password));
    }

    public boolean changePassword(String username, String oldPwd, String newPwd) {
        if (!userHash.containsKey(username)) return false;
        if (!userHash.get(username).equals(hash(oldPwd))) return false;
        userHash.put(username, hash(newPwd));
        return true;
    }

    public boolean ban(String username) {
        if (!userHash.containsKey(username)) return false;
        banned.add(username);
        return true;
    }

    public boolean unban(String username) {
        return banned.remove(username);
    }

    public boolean isBanned(String username) {
        return banned.contains(username);
    }

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}