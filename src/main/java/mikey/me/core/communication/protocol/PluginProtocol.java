package mikey.me.core.communication.protocol;

import mikey.me.core.json.JsonUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PluginProtocol {

    public static final int VERSION = 2;
    public static final long MAX_MESSAGE_AGE_MILLIS = 30_000L;

    public static final String CH_HELLO        = "advancedstaff:hello";
    public static final String CH_STAFFCHAT    = "advancedstaff:staffchat";
    public static final String CH_KICK         = "advancedstaff:kick";
    public static final String CH_BAN_NOTIFY   = "advancedstaff:ban_notify";
    public static final String CH_MUTE         = "advancedstaff:mute_sync";
    public static final String CH_VANISH       = "advancedstaff:vanish_sync";
    public static final String CH_FREEZE       = "advancedstaff:freeze_sync";
    public static final String CH_PLAYER_LIST  = "advancedstaff:player_list";

    public static final List<String> ALL_CHANNELS = List.of(
            CH_HELLO, CH_STAFFCHAT, CH_KICK, CH_BAN_NOTIFY,
            CH_MUTE, CH_VANISH, CH_FREEZE, CH_PLAYER_LIST);

    private static final int MAX_CACHED_NONCES = 8192;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Long> SEEN_NONCES = new ConcurrentHashMap<>();

    private PluginProtocol() {}

    public static Optional<String> wrap(String channel, String payload, String secret) {
        if (!canSign(channel, payload, secret) || !isJsonPayload(payload)) return Optional.empty();
        byte[] nonceBytes = new byte[16];
        RANDOM.nextBytes(nonceBytes);
        String nonce = HexFormat.of().formatHex(nonceBytes);
        long timestamp = System.currentTimeMillis();
        String mac = calculateMac(timestamp, channel, nonce, payload, secret);
        if (mac == null) return Optional.empty();
        return Optional.of("{\"timestamp\":" + timestamp
                + ",\"nonce\":\"" + nonce
                + "\",\"payload\":\"" + JsonUtil.escape(payload)
                + "\",\"mac\":\"" + mac + "\"}");
    }

    public static Optional<String> unwrap(String channel, String envelope, String secret) {
        if (!canSign(channel, envelope, secret) || !isJsonPayload(envelope)) return Optional.empty();
        long timestamp = JsonUtil.extractLong(envelope, "timestamp", Long.MIN_VALUE);
        String nonce = JsonUtil.extractString(envelope, "nonce");
        String payload = JsonUtil.extractString(envelope, "payload");
        String mac = JsonUtil.extractString(envelope, "mac");
        if (timestamp == Long.MIN_VALUE || !isLowerHex(nonce, 32) || !isLowerHex(mac, 64)
                || !isJsonPayload(payload)) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        if (timestamp > now + MAX_MESSAGE_AGE_MILLIS || timestamp < now - MAX_MESSAGE_AGE_MILLIS) {
            return Optional.empty();
        }
        String expected = calculateMac(timestamp, channel, nonce, payload, secret);
        if (expected == null) return Optional.empty();
        byte[] expectedBytes = HexFormat.of().parseHex(expected);
        byte[] providedBytes = HexFormat.of().parseHex(mac);
        if (!MessageDigest.isEqual(expectedBytes, providedBytes) || !rememberNonce(nonce, timestamp)) {
            return Optional.empty();
        }
        return Optional.of(payload);
    }

    private static boolean canSign(String channel, String value, String secret) {
        return ALL_CHANNELS.contains(channel) && value != null && !value.isBlank()
                && secret != null && !secret.isBlank();
    }

    private static boolean isJsonPayload(String payload) {
        return !payload.isBlank() && payload.charAt(0) == '{' && payload.charAt(payload.length() - 1) == '}';
    }

    private static boolean isLowerHex(String value, int length) {
        if (value.length() != length) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private static String calculateMac(long timestamp, String channel, String nonce,
                                      String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signed = timestamp + "\n" + channel + "\n" + nonce + "\n" + payload;
            return HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    private static boolean rememberNonce(String nonce, long timestamp) {
        long cutoff = System.currentTimeMillis() - MAX_MESSAGE_AGE_MILLIS;
        SEEN_NONCES.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        // cache full = drop new msgs, better than letting replays through
        if (SEEN_NONCES.size() >= MAX_CACHED_NONCES && !SEEN_NONCES.containsKey(nonce)) return false;
        return SEEN_NONCES.putIfAbsent(nonce, timestamp) == null;
    }
}
