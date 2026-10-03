package mikey.me.core.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class WebFiles {

    private WebFiles() {
    }

    public static String text(Class<?> type, String name) {
        if (type == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("missing page");
        }
        try (InputStream in = type.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("missing " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("missing " + name, e);
        }
    }
}
