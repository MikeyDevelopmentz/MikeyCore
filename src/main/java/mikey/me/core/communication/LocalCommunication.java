package mikey.me.core.communication;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class LocalCommunication implements CommunicationApi {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<CommunicationEvent>>> subscribers =
            new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    public CommunicationMode mode() {
        return CommunicationMode.PAPER;
    }

    @Override
    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("communication is closed");
        }
        started.set(true);
    }

    @Override
    public EventSubscription subscribe(String channel, Consumer<CommunicationEvent> listener) {
        String key = requireChannel(channel);
        Objects.requireNonNull(listener, "listener");
        if (closed.get()) {
            return new Subscription(key, listener, false);
        }
        subscribers.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        return new Subscription(key, listener, true);
    }

    @Override
    public void publish(String channel, String payload) {
        String key = requireChannel(channel);
        Objects.requireNonNull(payload, "payload");
        if (closed.get()) {
            throw new IllegalStateException("communication is closed");
        }
        CopyOnWriteArrayList<Consumer<CommunicationEvent>> listeners = subscribers.get(key);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        CommunicationEvent event = new CommunicationEvent(key, payload);
        for (Consumer<CommunicationEvent> listener : listeners) {
            deliver(listener, event);
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            subscribers.clear();
        }
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    private void deliver(Consumer<CommunicationEvent> listener, CommunicationEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException ignored) {
        }
    }

    private String requireChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        return channel;
    }

    private final class Subscription implements EventSubscription {

        private final String channel;
        private final Consumer<CommunicationEvent> listener;
        private final AtomicBoolean active;

        private Subscription(String channel, Consumer<CommunicationEvent> listener, boolean active) {
            this.channel = channel;
            this.listener = listener;
            this.active = new AtomicBoolean(active);
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) {
                CopyOnWriteArrayList<Consumer<CommunicationEvent>> listeners = subscribers.get(channel);
                if (listeners != null) {
                    listeners.remove(listener);
                    if (listeners.isEmpty()) {
                        subscribers.remove(channel, listeners);
                    }
                }
            }
        }

        @Override
        public boolean isActive() {
            return active.get() && !closed.get();
        }
    }
}
