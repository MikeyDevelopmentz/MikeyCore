package mikey.me.core.communication.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginProtocolTest {

    private static final String SECRET = "test-secret";
    private static final String PAYLOAD = "{\"message\":\"hello\"}";

    @Test
    void wrapsAndUnwrapsValidMessages() {
        String envelope = PluginProtocol.wrap(PluginProtocol.CH_STAFFCHAT, PAYLOAD, SECRET).orElseThrow();

        assertEquals(PAYLOAD, PluginProtocol.unwrap(PluginProtocol.CH_STAFFCHAT, envelope, SECRET).orElseThrow());
    }

    @Test
    void rejectsWrongChannelSecretAndTampering() {
        String envelope = PluginProtocol.wrap(PluginProtocol.CH_MUTE, PAYLOAD, SECRET).orElseThrow();
        String tampered = envelope.replace("hello", "goodbye");

        assertTrue(PluginProtocol.unwrap(PluginProtocol.CH_FREEZE, envelope, SECRET).isEmpty());
        assertTrue(PluginProtocol.unwrap(PluginProtocol.CH_MUTE, envelope, "wrong").isEmpty());
        assertTrue(PluginProtocol.unwrap(PluginProtocol.CH_MUTE, tampered, SECRET).isEmpty());
    }

    @Test
    void rejectsReplayAndBlankSecrets() {
        String envelope = PluginProtocol.wrap(PluginProtocol.CH_VANISH, PAYLOAD, SECRET).orElseThrow();

        assertTrue(PluginProtocol.unwrap(PluginProtocol.CH_VANISH, envelope, SECRET).isPresent());
        assertTrue(PluginProtocol.unwrap(PluginProtocol.CH_VANISH, envelope, SECRET).isEmpty());
        assertTrue(PluginProtocol.wrap(PluginProtocol.CH_VANISH, PAYLOAD, "").isEmpty());
    }

    @Test
    void readsSpacedEnvelopeWithoutChangingTheSignedPayload() {
        String envelope = PluginProtocol.wrap(PluginProtocol.CH_STAFFCHAT, PAYLOAD, SECRET).orElseThrow();
        String spaced = envelope.replace("\"timestamp\":", "\"timestamp\" : ")
                .replace("\"nonce\":", "\"nonce\" : ")
                .replace("\"payload\":", "\"payload\" : ")
                .replace("\"mac\":", "\"mac\" : ");
        assertEquals(PAYLOAD, PluginProtocol.unwrap(PluginProtocol.CH_STAFFCHAT, spaced, SECRET).orElseThrow());
        assertTrue(PluginProtocol.unwrap(PluginProtocol.CH_STAFFCHAT, spaced, SECRET).isEmpty());
    }

    // nonce cache used to scan everything and drop new msgs at capacity, server lost all traffic
    @Test
    void keepsWorkingPastTheNonceCacheLimit() {
        PluginProtocol.clearSeenNonces();
        String secret = "cache-pressure";
        int accepted = 0;
        int total = 12_000;
        for (int i = 0; i < total; i++) {
            String envelope = PluginProtocol.wrap(PluginProtocol.CH_STAFFCHAT,
                    "{\"n\":" + i + "}", secret).orElseThrow();
            if (PluginProtocol.unwrap(PluginProtocol.CH_STAFFCHAT, envelope, secret).isPresent()) {
                accepted++;
            }
        }
        assertTrue(accepted > total / 2,
                "most messages should still be accepted past the cache limit, got " + accepted + "/" + total);
        PluginProtocol.clearSeenNonces();
    }

    // replayed msg must stay rejected while cached, eviction cant be an anti-replay bypass
    @Test
    void stillRejectsReplayOfAnAcceptedEnvelope() {
        PluginProtocol.clearSeenNonces();
        String secret = "replay-after-eviction";
        String envelope = PluginProtocol.wrap(PluginProtocol.CH_BAN_NOTIFY, PAYLOAD, secret).orElseThrow();
        assertTrue(PluginProtocol.unwrap(PluginProtocol.CH_BAN_NOTIFY, envelope, secret).isPresent());
        assertTrue(PluginProtocol.unwrap(PluginProtocol.CH_BAN_NOTIFY, envelope, secret).isEmpty());
        // flood past the cache while the first nonce is still inside the 30s window
        for (int i = 0; i < 9_000; i++) {
            String extra = PluginProtocol.wrap(PluginProtocol.CH_BAN_NOTIFY,
                    "{\"n\":" + i + "}", secret).orElseThrow();
            PluginProtocol.unwrap(PluginProtocol.CH_BAN_NOTIFY, extra, secret);
        }
        assertTrue(PluginProtocol.unwrap(PluginProtocol.CH_BAN_NOTIFY, envelope, secret).isEmpty());
        PluginProtocol.clearSeenNonces();
    }
}
