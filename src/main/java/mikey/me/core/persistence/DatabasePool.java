package mikey.me.core.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabasePool implements AutoCloseable {

    private final HikariDataSource source;

    private DatabasePool(HikariDataSource source) {
        this.source = source;
    }

    // doesnt try to connect yet. a dead mysql shouldnt stop the plugin from enabling
    public static DatabasePool open(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config");
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("mysql driver missing", e);
        }
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password() == null ? "" : config.password());
        hikari.setMaximumPoolSize(config.maximumPoolSize());
        hikari.setMinimumIdle(0);
        hikari.setInitializationFailTimeout(-1);
        String name = config.poolName();
        hikari.setPoolName(name == null || name.isBlank() ? "mikey" : name);
        return new DatabasePool(new HikariDataSource(hikari));
    }

    public Connection connection() throws SQLException {
        if (source.isClosed()) {
            throw new SQLException("database is closed");
        }
        return source.getConnection();
    }

    @Override
    public void close() {
        if (!source.isClosed()) {
            source.close();
        }
    }
}
