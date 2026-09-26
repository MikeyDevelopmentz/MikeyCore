package mikey.me.core.persistence;

public record DatabaseConfig(String jdbcUrl, String username, String password, int maximumPoolSize, String poolName) {

    public DatabaseConfig {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl must not be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (maximumPoolSize <= 0) {
            throw new IllegalArgumentException("maximumPoolSize must be positive");
        }
    }

    @Override
    public String toString() {
        return "DatabaseConfig[jdbcUrl=" + jdbcUrl
                + ", username=" + username
                + ", password=***"
                + ", maximumPoolSize=" + maximumPoolSize
                + ", poolName=" + poolName
                + "]";
    }
}
