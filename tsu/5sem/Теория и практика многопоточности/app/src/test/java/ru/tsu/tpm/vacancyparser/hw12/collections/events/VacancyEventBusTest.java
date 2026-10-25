package ru.tsu.tpm.vacancyparser.hw12.collections.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;

/**
 * Шина событий: доставка всем подписчикам, порядок, счётчики, ограниченный журнал, сбой подписчика.
 */
class VacancyEventBusTest {

    private final List<VacancyEventBus> created = new ArrayList<>();

    @AfterEach
    void stopBuses() {
        created.forEach(VacancyEventBus::close);
        created.clear();
    }

    private VacancyEventBus bus(int logLimit) {
        VacancyEventBus bus = new VacancyEventBus(logLimit);
        created.add(bus);
        return bus;
    }

    @Test
    @Timeout(60)
    @DisplayName("Все подписчики получают все события в одинаковом порядке")
    void allSubscribersGetAllEventsInSameOrder() throws InterruptedException {
        VacancyEventBus bus = bus(10);
        List<ConcurrentLinkedQueue<Long>> orders = new ArrayList<>();
        List<AtomicInteger> counters = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            ConcurrentLinkedQueue<Long> order = new ConcurrentLinkedQueue<>();
            AtomicInteger counter = new AtomicInteger();
            orders.add(order);
            counters.add(counter);
            bus.subscribe(event -> {
                order.add(event.sequence());
                counter.incrementAndGet();
            });
        }

        for (int index = 0; index < 200; index++) {
            bus.publish(EventType.CACHE_HIT, "v-" + (index % 10));
        }

        assertThat(bus.flush(5_000L)).isTrue();
        assertThat(bus.published()).isEqualTo(200);
        assertThat(bus.delivered()).isEqualTo(200);
        assertThat(bus.rejected()).isZero();
        assertThat(bus.subscriberCount()).isEqualTo(3);
        assertThat(counters).allSatisfy(counter -> assertThat(counter).hasValue(200));

        List<Long> reference = new ArrayList<>(orders.get(0));
        assertThat(reference).hasSize(200).isSorted();
        for (ConcurrentLinkedQueue<Long> order : orders) {
            assertThat(new ArrayList<>(order)).isEqualTo(reference);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Счётчики по типам считают опубликованные события")
    void countsByType() throws InterruptedException {
        VacancyEventBus bus = bus(10);
        bus.publish(EventType.CACHE_HIT, "v-1");
        bus.publish(EventType.CACHE_HIT, "v-2");
        bus.publish(EventType.CACHE_MISS, "v-3");
        bus.publish(EventType.VACANCY_SAVED, "v-4");
        assertThat(bus.flush(2_000L)).isTrue();

        assertThat(bus.count(EventType.CACHE_HIT)).isEqualTo(2);
        assertThat(bus.count(EventType.CACHE_MISS)).isEqualTo(1);
        assertThat(bus.count(EventType.CACHE_EVICT)).isZero();
        assertThat(bus.counts()).containsEntry(EventType.CACHE_HIT, 2L).containsEntry(EventType.CACHE_MISS, 1L);
    }

    @Test
    @Timeout(60)
    @DisplayName("Журнал ограничен и хранит самые свежие события первыми")
    void recentLogIsBounded() throws InterruptedException {
        VacancyEventBus bus = bus(10);
        for (int index = 1; index <= 25; index++) {
            bus.publish(EventType.CACHE_HIT, "v-" + index);
        }
        assertThat(bus.flush(2_000L)).isTrue();

        List<VacancyEvent> recent = bus.recent();
        assertThat(recent).hasSize(10);
        assertThat(recent.get(0).sequence()).isEqualTo(25L);
        assertThat(recent.get(9).sequence()).isEqualTo(16L);
        assertThat(recent.get(0).reportLine()).contains("попадание").contains("v-25");
    }

    @Test
    @Timeout(60)
    @DisplayName("Сбой одного подписчика не мешает остальным")
    void brokenSubscriberDoesNotStopOthers() throws InterruptedException {
        VacancyEventBus bus = bus(10);
        AtomicInteger good = new AtomicInteger();
        bus.subscribe(event -> {
            throw new IllegalStateException("подписчик сломан");
        });
        bus.subscribe(event -> good.incrementAndGet());

        bus.publish(EventType.CACHE_HIT, "v-1");
        bus.publish(EventType.CACHE_HIT, "v-2");

        assertThat(bus.flush(2_000L)).isTrue();
        assertThat(good).hasValue(2);
        assertThat(bus.subscriberFailures()).isEqualTo(2);
        assertThat(bus.delivered()).isEqualTo(2);
    }

    @Test
    @Timeout(60)
    @DisplayName("События из многих потоков не теряются")
    void eventsFromManyThreadsAreNotLost() throws InterruptedException {
        VacancyEventBus bus = bus(20);
        AtomicInteger received = new AtomicInteger();
        bus.subscribe(event -> received.incrementAndGet());
        int threads = 8;
        int perThread = 100;

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int index = 0; index < threads; index++) {
            int threadIndex = index;
            new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int event = 0; event < perThread; event++) {
                        bus.publish(EventType.CACHE_MISS, "v-" + threadIndex + "-" + event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "bus-publisher-" + (index + 1)).start();
        }

        ready.await();
        start.countDown();
        done.await();

        assertThat(bus.flush(10_000L)).isTrue();
        assertThat(bus.published()).isEqualTo((long) threads * perThread);
        assertThat(bus.delivered()).isEqualTo((long) threads * perThread);
        assertThat(received).hasValue(threads * perThread);
    }

    @Test
    @Timeout(60)
    @DisplayName("После остановки шина не принимает события, но не падает")
    void publishAfterCloseIsRejected() throws InterruptedException {
        VacancyEventBus bus = bus(10);
        bus.subscribe(event -> { });
        bus.close();

        bus.publish(EventType.CACHE_HIT, "v-1");

        assertThat(bus.published()).isEqualTo(1);
        assertThat(bus.rejected()).isEqualTo(1);
        assertThat(bus.flush(1_000L)).isTrue();
        assertThat(ThreadDump.liveThreadsWithPrefixes(VacancyEventBus.DISPATCHER_THREAD)).isEmpty();
    }

    @Test
    @DisplayName("Некорректные параметры отвергаются")
    void validation() {
        assertThatThrownBy(() -> new VacancyEventBus(0)).isInstanceOf(IllegalArgumentException.class);
        VacancyEventBus bus = bus(5);
        assertThatThrownBy(() -> bus.publish(null, "v-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bus.subscribe(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
