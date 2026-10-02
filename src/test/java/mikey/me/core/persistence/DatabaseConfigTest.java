package mikey.me.core.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {

    @Test
    void redactsPasswordInDiagnostics() {
        DatabaseConfig config = new DatabaseConfig("jdbc:mysql://localhost/db", "user", "secret", 4, "pool");

        assertTrue(config.toString().contains("password=***"));
        assertFalse(config.toString().contains("secret"));

        DatabaseConfig withUrl = new DatabaseConfig(
                "jdbc:mysql://dbuser:secret@localhost/db?password=secret&useSsl=false",
                "user", "secret", 4, "pool");
        String text = withUrl.toString();
        assertTrue(text.contains("password=***"));
        assertTrue(text.contains("dbuser:***@"));
        assertFalse(text.contains("secret"), text);
    }

    @Test
    void rejectsInvalidConnectionSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("", "user", "", 1, "pool"));
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("jdbc:mysql://localhost/db", "", "", 1, "pool"));
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("jdbc:mysql://localhost/db", "user", "", 0, "pool"));
    }
}
