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
        return "DatabaseConfig[jdbcUrl=" + redactUrl(jdbcUrl)
                + ", username=" + username
                + ", password=***"
                + ", maximumPoolSize=" + maximumPoolSize
                + ", poolName=" + poolName
                + "]";
    }

    // same idea as password=***, user:secret@ and ?password= leak it too
    private static String redactUrl(String url) {
        String out = redactUserInfo(url);
        return redactPasswordParam(out);
    }

    private static String redactUserInfo(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int start = scheme + 3;
        int at = url.indexOf('@', start);
        if (at < 0) {
            return url;
        }
        int slash = url.indexOf('/', start);
        int question = url.indexOf('?', start);
        if ((slash >= 0 && slash < at) || (question >= 0 && question < at)) {
            return url;
        }
        int colon = url.indexOf(':', start);
        if (colon < 0 || colon > at) {
            return url;
        }
        return url.substring(0, colon + 1) + "***" + url.substring(at);
    }

    private static String redactPasswordParam(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < url.length()) {
            int at = lower.indexOf("password", i);
            if (at < 0) {
                out.append(url, i, url.length());
                break;
            }
            char before = at == 0 ? 0 : url.charAt(at - 1);
            int eq = at + "password".length();
            if ((before != '?' && before != '&' && before != ';') || eq >= url.length() || url.charAt(eq) != '=') {
                out.append(url, i, at + 1);
                i = at + 1;
                continue;
            }
            int valueEnd = eq + 1;
            while (valueEnd < url.length()) {
                char c = url.charAt(valueEnd);
                if (c == '&' || c == ';') {
                    break;
                }
                valueEnd++;
            }
            out.append(url, i, eq + 1).append("***");
            i = valueEnd;
        }
        return out.toString();
    }
}
