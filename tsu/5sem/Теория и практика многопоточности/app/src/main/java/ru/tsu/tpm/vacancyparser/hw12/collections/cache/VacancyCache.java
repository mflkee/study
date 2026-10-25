package ru.tsu.tpm.vacancyparser.hw12.collections.cache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import ru.tsu.tpm.vacancyparser.hw12.collections.events.EventType;
import ru.tsu.tpm.vacancyparser.hw12.collections.model.VacancyCard;

/**
 * Потокобезопасный кэш вакансий с TTL, ограничением ёмкости и событиями.
 *
 * <p><b>Почему не {@code synchronized} вокруг {@code HashMap}.</b> Кэш читают все потоки и постоянно:
 * блокировка на каждое чтение превращает параллельный обход в последовательный. Здесь чтение идёт без
 * блокировки ({@link ConcurrentHashMap#get}), а «проверить и создать» выполняется атомарно в
 * {@code compute}: пока один поток загружает значение, остальные ждут только по этому ключу, а не по
 * всему кэшу. Поэтому загрузчик вызывается ровно один раз на ключ — это проверяется тестом.
 *
 * <p><b>Две стратегии удаления.</b> Ограничение ёмкости — очередь вставки
 * ({@link ConcurrentLinkedDeque}) и вытеснение самых старых записей. Срок жизни —
 * {@link DelayQueue} с ключами истечения и отдельный поток-уборщик. Обе структуры потокобезопасны,
 * поэтому уборщик работает одновременно с чтениями и записями, не блокируя их.
 *
 * <p>События публикуются наружу через приёмник {@code BiConsumer<EventType, String>} (в приложении
 * это шина ДЗ 12). Кэш не знает, кто и как их обрабатывает, и не ждёт подписчиков: нумерацию и доставку
 * делает шина, а не кэш.
 */
public final class VacancyCache implements AutoCloseable {

    /** Имя потока-уборщика. */
    public static final String SWEEPER_THREAD = "hw12-cache-sweeper";

    private final ConcurrentMap<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> insertionOrder = new ConcurrentLinkedDeque<>();
    private final DelayQueue<ExpiringKey> expirations = new DelayQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder loads = new LongAdder();
    private final LongAdder evictions = new LongAdder();
    private final LongAdder expired = new LongAdder();
    private final int capacity;
    private final long ttlNanos;
    private final BiConsumer<EventType, String> events;
    private final Thread sweeper;
    private volatile boolean running = true;

    /**
     * @param capacity  предел числа записей
     * @param ttlMillis срок жизни записи, миллисекунды
     * @param events    приёмник событий «тип + идентификатор» (может быть {@code null})
     */
    public VacancyCache(int capacity, long ttlMillis, BiConsumer<EventType, String> events) {
        if (capacity < 1) {
            throw new IllegalArgumentException("ёмкость кэша должна быть положительной, получено " + capacity);
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("срок жизни должен быть положительным, получено " + ttlMillis);
        }
        this.capacity = capacity;
        this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(ttlMillis);
        this.events = events;
        this.sweeper = new Thread(this::sweep, SWEEPER_THREAD);
        this.sweeper.setDaemon(true);
        this.sweeper.start();
    }

    /**
     * Получить карточку из кэша или загрузить её.
     *
     * <p>Загрузчик вызывается не более одного раза на ключ даже при одновременных обращениях: проверка
     * и вставка выполняются внутри {@code compute}, который держит блокировку только этого ключа.
     *
     * @param id     идентификатор вакансии
     * @param loader источник значения (может бросить исключение — оно уйдёт вызывающему)
     */
    public VacancyCard getOrLoad(String id, Function<String, VacancyCard> loader) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("идентификатор вакансии не задан");
        }
        if (loader == null) {
            throw new IllegalArgumentException("загрузчик не задан");
        }
        AtomicBoolean loaded = new AtomicBoolean();
        CacheEntry entry = entries.compute(id, (key, current) -> {
            if (current != null && !isExpired(current)) {
                return current;
            }
            VacancyCard card = loader.apply(key);
            long version = sequence.incrementAndGet();
            loaded.set(true);
            loads.increment();
            insertionOrder.addLast(key);
            expirations.offer(new ExpiringKey(key, version, System.nanoTime() + ttlNanos));
            publish(EventType.CACHE_LOAD, key);
            return new CacheEntry(card, System.nanoTime() + ttlNanos, version);
        });

        if (loaded.get()) {
            misses.increment();
            publish(EventType.CACHE_MISS, id);
            evictIfNeeded();
        } else {
            hits.increment();
            publish(EventType.CACHE_HIT, id);
        }
        return entry.card();
    }

    /** Положить карточку в кэш напрямую (без загрузчика). */
    public void put(VacancyCard card) {
        if (card == null) {
            throw new IllegalArgumentException("карточка не задана");
        }
        long version = sequence.incrementAndGet();
        CacheEntry previous = entries.put(
                card.id(), new CacheEntry(card, System.nanoTime() + ttlNanos, version));
        if (previous == null) {
            insertionOrder.addLast(card.id());
        }
        expirations.offer(new ExpiringKey(card.id(), version, System.nanoTime() + ttlNanos));
        publish(EventType.VACANCY_SAVED, card.id());
        evictIfNeeded();
    }

    /** Значение из кэша без загрузки и без изменения счётчиков. */
    public Optional<VacancyCard> peek(String id) {
        CacheEntry entry = entries.get(id);
        if (entry == null || isExpired(entry)) {
            return Optional.empty();
        }
        return Optional.of(entry.card());
    }

    /** Есть ли живая (не истёкшая) запись. */
    public boolean contains(String id) {
        return peek(id).isPresent();
    }

    /** Текущее число записей (включая ещё не убранные истёкшие). */
    public int size() {
        return entries.size();
    }

    /** Удалить запись по ключу. */
    public boolean invalidate(String id) {
        CacheEntry removed = entries.remove(id);
        if (removed != null) {
            insertionOrder.remove(id);
            return true;
        }
        return false;
    }

    /** Снимок счётчиков. */
    public CacheStats stats() {
        return new CacheStats(
                hits.sum(), misses.sum(), loads.sum(), evictions.sum(), expired.sum(), entries.size());
    }

    /** Ёмкость кэша. */
    public int capacity() {
        return capacity;
    }

    /** Срок жизни записи, миллисекунды. */
    public long ttlMillis() {
        return TimeUnit.NANOSECONDS.toMillis(ttlNanos);
    }

    /** Дождаться, пока уборщик обработает не меньше {@code expected} истечений. */
    public boolean awaitExpirations(long expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (expired.sum() < expected) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(2L);
        }
        return true;
    }

    /** Дождаться, пока размер кэша не станет не больше {@code expected}. */
    public boolean awaitSizeAtMost(int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (size() > expected) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(2L);
        }
        return true;
    }

    /** Остановить уборщик. */
    @Override
    public void close() {
        running = false;
        sweeper.interrupt();
        try {
            sweeper.join(1_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sweep() {
        while (running) {
            try {
                ExpiringKey key = expirations.poll(100, TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }
                CacheEntry entry = entries.get(key.vacancyId());
                if (entry != null && entry.sequence() == key.sequence()) {
                    if (entries.remove(key.vacancyId(), entry)) {
                        insertionOrder.remove(key.vacancyId());
                        expired.increment();
                        publish(EventType.CACHE_EXPIRE, key.vacancyId());
                    }
                }
            } catch (InterruptedException e) {
                if (!running) {
                    return;
                }
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void evictIfNeeded() {
        while (entries.size() > capacity) {
            String victim = insertionOrder.pollFirst();
            if (victim == null) {
                return;
            }
            CacheEntry removed = entries.remove(victim);
            if (removed != null) {
                evictions.increment();
                publish(EventType.CACHE_EVICT, victim);
            }
        }
    }

    private boolean isExpired(CacheEntry entry) {
        return entry.expiresAtNanos() <= System.nanoTime();
    }

    private void publish(EventType type, String id) {
        if (events != null) {
            events.accept(type, id);
        }
    }
}
