package mikey.me.core.communication;

public interface EventSubscription extends AutoCloseable {

    @Override
    void close();

    boolean isActive();
}
