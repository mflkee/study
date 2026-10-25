package ru.tsu.tpm.vacancyparser.hw12.collections.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw12.collections.events.EventType;
import ru.tsu.tpm.vacancyparser.hw12.collections.model.VacancyCard;

/**
 * Кэш: однократная загрузка под конкуренцией, срок жизни, вытеснение, события, остановка уборщика.
 */
class VacancyCacheTest {

    private final List<VacancyCache> created = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopCaches() {
        created.forEach(VacancyCache::close);
        created.clear();
    }

    private VacancyCache cache(int capacity, long ttlMillis) {
        VacancyCache cache = new VacancyCache(capacity, ttlMillis, null);
        created.add(cache);
        return cache;
    }

    private static VacancyCard card(String id) {
        return new VacancyCard(id, "Вакансия " + id, "Томск", 100_000, 4.0);
    }

    @Test
    @Timeout(60)
    @DisplayName("При одновременном обращении к одному ключу загрузчик вызывается один раз")
    void singleFlightLoad() throws InterruptedException {
        VacancyCache cache = cache(16, 60_000L);
        AtomicInteger loaderCalls = new AtomicInteger();
        int threads = 16;

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<VacancyCard> returned = ConcurrentHashMap.newKeySet();
        for (int index = 0; index < threads; index++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    returned.add(cache.getOrLoad("same-key", key -> {
                        loaderCalls.incrementAndGet();
                        try {
                            Thread.sleep(20L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return card(key);
                    }));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "cache-test-" + index);
            thread.start();
        }

        ready.await();
        start.countDown();
        done.await();

        assertThat(loaderCalls).hasValue(1);
        assertThat(cache.stats().loads()).isEqualTo(1);
        assertThat(cache.stats().misses()).isEqualTo(1);
        assertThat(cache.stats().hits()).isEqualTo(threads - 1);
        assertThat(returned).as("все потоки получили один и тот же экземпляр").hasSize(1);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @Timeout(30)
    @DisplayName("Попадание и промах считаются, значение переиспользуется")
    void hitsAndMisses() {
        VacancyCache cache = cache(10, 60_000L);
        AtomicInteger loads = new AtomicInteger();

        VacancyCard first = cache.getOrLoad("v-1", key -> {
            loads.incrementAndGet();
            return card(key);
        });
        VacancyCard second = cache.getOrLoad("v-1", key -> {
            loads.incrementAndGet();
            return card(key);
        });

        assertThat(loads).hasValue(1);
        assertThat(second).isSameAs(first);
        assertThat(cache.stats().misses()).isEqualTo(1);
        assertThat(cache.stats().hits()).isEqualTo(1);
        assertThat(cache.stats().hitRatio()).isEqualTo(0.5);
        assertThat(cache.peek("v-1")).contains(first);
        assertThat(cache.contains("v-1")).isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("Запись исчезает по истечении срока жизни, уборщик это фиксирует")
    void entryExpires() throws InterruptedException {
        VacancyCache cache = cache(10, 100L);
        cache.put(card("ttl-1"));

        assertThat(cache.awaitExpirations(1L, 5_000L)).isTrue();

        assertThat(cache.peek("ttl-1")).isEmpty();
        assertThat(cache.stats().expirations()).isEqualTo(1);
        assertThat(cache.awaitSizeAtMost(0, 2_000L)).isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("Старые записи вытесняются, размер не превышает ёмкость")
    void capacityIsEnforced() {
        VacancyCache cache = cache(5, 60_000L);
        for (int index = 0; index < 12; index++) {
            cache.put(card("v-" + index));
        }

        assertThat(cache.size()).isEqualTo(5);
        assertThat(cache.stats().evictions()).isEqualTo(7);
        assertThat(cache.contains("v-0")).isFalse();
        assertThat(cache.contains("v-11")).isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("Инвалидация удаляет запись, повторная — возвращает false")
    void invalidate() {
        VacancyCache cache = cache(5, 60_000L);
        cache.put(card("v-1"));

        assertThat(cache.invalidate("v-1")).isTrue();
        assertThat(cache.invalidate("v-1")).isFalse();
        assertThat(cache.contains("v-1")).isFalse();
    }

    @Test
    @Timeout(30)
    @DisplayName("Кэш публикует события загрузки, попадания, сохранения, вытеснения и истечения")
    void publishesEvents() throws InterruptedException {
        List<EventType> types = new CopyOnWriteArrayList<>();
        VacancyCache cache = new VacancyCache(4, 80L, (type, id) -> types.add(type));
        created.add(cache);

        cache.getOrLoad("v-1", key -> card(key));
        cache.getOrLoad("v-1", key -> card(key));
        cache.put(card("v-2"));
        cache.put(card("v-3"));
        cache.put(card("v-4"));
        cache.put(card("v-5"));
        cache.put(card("v-6"));
        cache.put(card("ttl"));
        assertThat(cache.awaitExpirations(1L, 5_000L)).isTrue();

        assertThat(types).contains(
                EventType.CACHE_LOAD, EventType.CACHE_MISS, EventType.CACHE_HIT,
                EventType.VACANCY_SAVED, EventType.CACHE_EVICT, EventType.CACHE_EXPIRE);
    }

    @Test
    @Timeout(30)
    @DisplayName("Исключение загрузчика уходит вызывающему, запись не кэшируется")
    void loaderFailureIsPropagated() {
        VacancyCache cache = cache(5, 60_000L);

        assertThatThrownBy(() -> cache.getOrLoad("v-1", key -> {
            throw new IllegalStateException("источник недоступен");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cache.size()).isZero();
        assertThat(cache.stats().loads()).isZero();
    }

    @Test
    @Timeout(30)
    @DisplayName("Некорректные параметры отвергаются")
    void validation() {
        assertThatThrownBy(() -> new VacancyCache(0, 100L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VacancyCache(5, 0L, null))
                .isInstanceOf(IllegalArgumentException.class);

        VacancyCache cache = cache(5, 60_000L);
        assertThatThrownBy(() -> cache.getOrLoad(" ", key -> card(key)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.getOrLoad("v-1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.put(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(30)
    @DisplayName("close() идемпотентен, после остановки потоков кэша не остаётся")
    void closeIsIdempotent() {
        VacancyCache cache = cache(5, 60_000L);
        cache.put(card("v-1"));

        cache.close();
        cache.close();

        assertThat(ThreadDump.liveThreadsWithPrefixes(VacancyCache.SWEEPER_THREAD)).isEmpty();
    }

    @Test
    @Timeout(30)
    @DisplayName("Счётчики описывают себя строкой с долей попаданий")
    void statsReportLine() {
        CacheStats stats = new CacheStats(8, 2, 2, 1, 3, 4);
        assertThat(stats.hitRatio()).isEqualTo(0.8);
        assertThat(stats.reportLine())
                .contains("попаданий 8")
                .contains("промахов 2")
                .contains("доля попаданий 80.0%");
        assertThat(new CacheStats(0, 0, 0, 0, 0, 0).hitRatio()).isZero();
    }
}
