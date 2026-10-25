package ru.tsu.tpm.vacancyparser.hw12.collections.benchmark;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Сравнение конкурентных коллекций с «обычными плюс блокировка».
 *
 * <p>Числа нужны, чтобы утверждение «ConcurrentHashMap быстрее synchronizedMap» было не лозунгом, а
 * измерением. Нагрузка одинаковая: несколько потоков с общим набором ключей делают смесь чтений и
 * записей. Синхронизированная карта сериализует <b>каждую</b> операцию, конкурентная — только запись в
 * свой бин, а чтения идут без блокировки.
 *
 * <p>Второе сравнение — счётчики: {@link AtomicLong} обновляет одну ячейку и под нагрузкой упирается в
 * CAS-конфликты, {@link LongAdder} раскладывает счёт по ячейкам и складывает их при чтении.
 *
 * <p>Потоки создаются на один прогон и обязательно дожидаются: замер без ожидания измерил бы только
 * запуск потоков.
 */
public final class ConcurrentBenchmarks {

    private ConcurrentBenchmarks() {
    }

    /**
     * Результат замера карты.
     *
     * @param label         название варианта
     * @param threads       число потоков
     * @param opsPerThread  операций на поток
     * @param durationNanos длительность
     * @param finalSize     итоговый размер карты
     */
    public record MapRun(String label, int threads, int opsPerThread, long durationNanos, int finalSize) {

        /** Всего операций. */
        public long operations() {
            return (long) threads * opsPerThread;
        }

        /** Операций в секунду. */
        public double opsPerSecond() {
            return durationNanos <= 0 ? 0.0 : operations() * 1_000_000_000.0 / durationNanos;
        }

        /** Строка для отчёта. */
        public String reportLine() {
            return String.format(
                    Locale.ROOT,
                    "%s: %d операций за %.3f мс, %.0f оп/с, записей %d",
                    label, operations(), durationNanos / 1_000_000.0, opsPerSecond(), finalSize);
        }
    }

    /**
     * Результат замера счётчика.
     *
     * @param label         название варианта
     * @param expected      ожидаемое значение
     * @param actual        фактическое значение
     * @param durationNanos длительность
     */
    public record CounterRun(String label, long expected, long actual, long durationNanos) {

        /** Совпало ли фактическое значение с ожидаемым (проверка отсутствия потерь). */
        public boolean correct() {
            return expected == actual;
        }

        /** Инкрементов в секунду. */
        public double opsPerSecond() {
            return durationNanos <= 0 ? 0.0 : expected * 1_000_000_000.0 / durationNanos;
        }

        /** Строка для отчёта. */
        public String reportLine() {
            return String.format(
                    Locale.ROOT,
                    "%s: %d инкрементов за %.3f мс, %.0f оп/с, итог %d (ожидалось %d), корректно: %s",
                    label, expected, durationNanos / 1_000_000.0, opsPerSecond(), actual, expected,
                    correct() ? "да" : "НЕТ");
        }
    }

    /** Конкурентная карта: смесь чтений и записей из нескольких потоков (90% чтений). */
    public static MapRun concurrentMap(int threads, int keys, int opsPerThread) throws InterruptedException {
        return runMap("ConcurrentHashMap", new ConcurrentHashMap<>(), threads, keys, opsPerThread);
    }

    /** Обычная карта под синхронизацией — база сравнения. */
    public static MapRun synchronizedMap(int threads, int keys, int opsPerThread) throws InterruptedException {
        return runMap(
                "synchronizedMap(HashMap)",
                Collections.synchronizedMap(new HashMap<>()),
                threads,
                keys,
                opsPerThread);
    }

    /** Замер произвольной карты с общей нагрузкой. */
    public static MapRun runMap(
            String label, Map<String, String> map, int threads, int keys, int opsPerThread)
            throws InterruptedException {
        if (map == null || label == null || label.isBlank()) {
            throw new IllegalArgumentException("карта и название обязательны");
        }
        validate(threads, opsPerThread);
        if (keys < 1) {
            throw new IllegalArgumentException("нужен хотя бы один ключ");
        }

        long durationNanos = runThreads(threads, "hw12-bench-map-", index -> {
            for (int operation = 0; operation < opsPerThread; operation++) {
                String key = "k-" + ((operation + index) % keys);
                // Нагрузка как у кэша: в основном чтения, запись — редко.
                if (operation % 10 == 0) {
                    map.put(key, key);
                } else {
                    map.get(key);
                }
            }
        });
        return new MapRun(label, threads, opsPerThread, durationNanos, map.size());
    }

    /** Счётчик на {@link AtomicLong}. */
    public static CounterRun atomicLong(int threads, int opsPerThread) throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        return runCounter(
                "AtomicLong", threads, opsPerThread, value -> counter.incrementAndGet(), counter::get);
    }

    /** Счётчик на {@link LongAdder}. */
    public static CounterRun longAdder(int threads, int opsPerThread) throws InterruptedException {
        LongAdder counter = new LongAdder();
        return runCounter(
                "LongAdder", threads, opsPerThread, value -> counter.increment(), counter::sum);
    }

    private static CounterRun runCounter(
            String label, int threads, int opsPerThread, LongConsumer increment, LongSupplier value)
            throws InterruptedException {
        validate(threads, opsPerThread);
        long durationNanos = runThreads(threads, "hw12-bench-counter-", index -> {
            for (int operation = 0; operation < opsPerThread; operation++) {
                increment.accept(index);
            }
        });
        long expected = (long) threads * opsPerThread;
        return new CounterRun(label, expected, value.getAsLong(), durationNanos);
    }

    /** Общий каркас: потоки стартуют одновременно, время считается от общего старта до последнего финиша. */
    private static long runThreads(int threads, String namePrefix, IndexedBody body) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Thread> workers = new java.util.ArrayList<>(threads);

        for (int index = 0; index < threads; index++) {
            int threadIndex = index;
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    body.run(threadIndex);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, namePrefix + (index + 1));
            thread.setDaemon(true);
            workers.add(thread);
            thread.start();
        }

        ready.await();
        long startNanos = System.nanoTime();
        start.countDown();
        done.await();
        long durationNanos = System.nanoTime() - startNanos;
        for (Thread worker : workers) {
            worker.join(1_000L);
        }
        return durationNanos;
    }

    private static void validate(int threads, int opsPerThread) {
        if (threads < 1) {
            throw new IllegalArgumentException("нужен хотя бы один поток");
        }
        if (opsPerThread < 1) {
            throw new IllegalArgumentException("нужна хотя бы одна операция на поток");
        }
    }

    /** Тело работы потока, которому нужен его номер. */
    @FunctionalInterface
    private interface IndexedBody {
        void run(int threadIndex) throws InterruptedException;
    }
}
