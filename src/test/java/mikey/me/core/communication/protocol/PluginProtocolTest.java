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
}
