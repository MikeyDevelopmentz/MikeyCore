package mikey.me.core.web;

import java.io.IOException;

public interface WebHandler {

    void handle(WebExchange exchange) throws IOException;
}
