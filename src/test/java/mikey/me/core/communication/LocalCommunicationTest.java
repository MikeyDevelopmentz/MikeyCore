package mikey.me.core.communication;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalCommunicationTest {

    private final LocalCommunication communication = new LocalCommunication();

    @Test
    void reportsPaperMode() {
        assertEquals(CommunicationMode.PAPER, communication.mode());
    }

    @Test
    void startIsIdempotentAndDrivesStartedFlag() {
        assertFalse(communication.isStarted());
        communication.start();
        communication.start();
        assertTrue(communication.isStarted());
        assertFalse(communication.isClosed());
    }

    @Test
    void startAfterCloseIsRejected() {
        communication.close();
        assertThrows(IllegalStateException.class, communication::start);
    }

    @Test
    void deliversEventToSubscriberOfMatchingChannel() {
        List<CommunicationEvent> received = new ArrayList<>();
        communication.subscribe("staff:chat", received::add);

        communication.publish("staff:chat", "hello");

        assertEquals(1, received.size());
        assertEquals("staff:chat", received.get(0).channel());
        assertEquals("hello", received.get(0).payload());
    }

    @Test
    void deliversToEverySubscriberOfTheChannelInRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        communication.subscribe("vanish", event -> calls.add("first"));
        communication.subscribe("vanish", event -> calls.add("second"));
        communication.subscribe("vanish", event -> calls.add("third"));

        communication.publish("vanish", "payload");

        assertEquals(List.of("first", "second", "third"), calls);
    }

    @Test
    void keepsChannelsIsolated() {
        List<CommunicationEvent> chatEvents = new ArrayList<>();
        List<CommunicationEvent> muteEvents = new ArrayList<>();
        communication.subscribe("chat", chatEvents::add);
        communication.subscribe("mute", muteEvents::add);

        communication.publish("chat", "only-chat");

        assertEquals(1, chatEvents.size());
        assertEquals(0, muteEvents.size());
    }

    @Test
    void publishToUnknownChannelIsANoOp() {
        communication.subscribe("chat", event -> {
        });

        communication.publish("missing", "payload");
    }

    @Test
    void subscriptionIsActiveUntilClosed() {
        EventSubscription subscription = communication.subscribe("chat", event -> {
        });

        assertTrue(subscription.isActive());
        subscription.close();
        assertFalse(subscription.isActive());
    }

    @Test
    void closingSubscriptionStopsDelivery() {
        List<CommunicationEvent> received = new ArrayList<>();
        EventSubscription subscription = communication.subscribe("chat", received::add);
        communication.publish("chat", "before");

        subscription.close();
        communication.publish("chat", "after");

        assertEquals(1, received.size());
        assertEquals("before", received.get(0).payload());
    }

    @Test
    void closingSubscriptionTwiceIsSafe() {
        EventSubscription subscription = communication.subscribe("chat", event -> {
        });

        subscription.close();
        subscription.close();

        assertFalse(subscription.isActive());
    }

    @Test
    void closingOneSubscriptionLeavesSiblingsRegistered() {
        List<CommunicationEvent> kept = new ArrayList<>();
        EventSubscription removed = communication.subscribe("chat", event -> {
        });
        communication.subscribe("chat", kept::add);

        removed.close();
        communication.publish("chat", "payload");

        assertEquals(1, kept.size());
    }

    @Test
    void closingCommunicationDeactivatesSubscriptionsAndStopsDelivery() {
        AtomicInteger deliveries = new AtomicInteger();
        EventSubscription subscription = communication.subscribe("chat", event -> deliveries.incrementAndGet());
        communication.publish("chat", "before");

        communication.close();

        assertThrows(IllegalStateException.class, () -> communication.publish("chat", "after"));
        assertEquals(1, deliveries.get());
        assertFalse(subscription.isActive());
    }

    @Test
    void closeIsIdempotent() {
        communication.close();
        communication.close();

        assertTrue(communication.isClosed());
    }

    @Test
    void publishAfterCloseThrowsIllegalStateException() {
        communication.close();

        assertThrows(IllegalStateException.class, () -> communication.publish("chat", "payload"));
    }

    @Test
    void subscribeAfterCloseYieldsInactiveSubscription() {
        communication.close();

        EventSubscription subscription = communication.subscribe("chat", event -> {
        });

        assertFalse(subscription.isActive());
        subscription.close();
    }

    @Test
    void subscriberFailureNeverReachesThePublisher() {
        List<CommunicationEvent> received = new ArrayList<>();
        communication.subscribe("chat", event -> {
            throw new IllegalStateException("subscriber blew up");
        });
        communication.subscribe("chat", received::add);

        communication.publish("chat", "payload");

        assertEquals(1, received.size());
    }

    @Test
    void repeatedSubscriberFailuresKeepTheBusUsable() {
        communication.subscribe("chat", event -> {
            throw new IllegalStateException("subscriber blew up");
        });
        AtomicInteger deliveries = new AtomicInteger();
        communication.subscribe("chat", event -> deliveries.incrementAndGet());

        communication.publish("chat", "first");
        communication.publish("chat", "second");

        assertEquals(2, deliveries.get());
    }

    @Test
    void rethrowingSubscriberDoesNotPreventItsOwnUnsubscription() {
        AtomicInteger deliveries = new AtomicInteger();
        EventSubscription subscription = communication.subscribe("chat", event -> {
            deliveries.incrementAndGet();
            throw new IllegalStateException("subscriber blew up");
        });

        communication.publish("chat", "first");
        subscription.close();
        communication.publish("chat", "second");

        assertEquals(1, deliveries.get());
    }

    @Test
    void flushIsSafeBeforeAndAfterClose() {
        communication.flush();
        communication.close();
        communication.flush();
    }

    @Test
    void rejectsBlankChannelAndNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> communication.subscribe("  ", event -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> communication.subscribe(null, event -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> communication.publish("  ", "payload"));
        assertThrows(NullPointerException.class, () -> communication.subscribe("chat", null));
        assertThrows(NullPointerException.class, () -> communication.publish("chat", null));
    }

    @Test
    void concurrentPublishesReachASingleSubscriberExactlyOnce() throws InterruptedException {
        int publishers = 8;
        int messagesEach = 250;
        AtomicInteger deliveries = new AtomicInteger();
        communication.subscribe("chat", event -> deliveries.incrementAndGet());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(publishers);
        ExecutorService executor = Executors.newFixedThreadPool(publishers);
        try {
            for (int i = 0; i < publishers; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        for (int m = 0; m < messagesEach; m++) {
                            communication.publish("chat", "payload");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(finished.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(publishers * messagesEach, deliveries.get());
    }

    @Test
    void concurrentSubscribesAndUnsubscribesLeaveNoLeak() throws InterruptedException {
        int threads = 8;
        int iterations = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        for (int n = 0; n < iterations; n++) {
                            EventSubscription subscription = communication.subscribe("chat", event -> {
                            });
                            subscription.close();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(finished.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertFalse(communication.isStarted());
        assertFalse(communication.isClosed());
    }
}
