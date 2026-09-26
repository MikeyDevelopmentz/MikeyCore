package mikey.me.core.communication;

import java.util.function.Consumer;

public interface CommunicationApi extends AutoCloseable {

    CommunicationMode mode();

    void start();

    EventSubscription subscribe(String channel, Consumer<CommunicationEvent> listener);

    void publish(String channel, String payload);

    void flush();

    @Override
    void close();
}
