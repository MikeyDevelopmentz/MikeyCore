package mikey.me.core.persistence;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabasePoolTest {

    @Test
    void opensWithoutConnectingAndCloseRejects() {
        DatabaseConfig config = new DatabaseConfig("jdbc:mysql://127.0.0.1:1/mikey", "root", "", 1, "test");
        DatabasePool pool = DatabasePool.open(config);
        try {
            pool.close();
            assertThrows(SQLException.class, pool::connection);
        } finally {
            pool.close();
        }
    }
}
