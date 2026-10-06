package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Гонка за пустой кэш: много потоков одновременно обращаются к нему и получают значения.
 *
 * <p>Все потоки стартуют по общему затвору — без него гонка не возникает, и корректная, и дефектная
 * реализации покажут «один экземпляр». По результатам считаются фактические создания, вызовы фабрики
 * и число различных полученных экземпляров (сравнение по ссылке).
 */
public final class CacheRaceRunner {

    /** Префикс имён потоков кэша. */
    public static final String THREAD_PREFIX = "hw06-cache-";

    /**
     * Итог гонки.
     *
     * @param threads           число потоков
     * @param creations         успешных сохранений значения
     * @param factoryInvocations вызовов фабрики
     * @param distinctInstances различных экземпляров, полученных потоками
     * @param elapsedNanos      время прогона
     */
    public record RaceOutcome(
            int threads, int creations, int factoryInvocations, int distinctInstances, long elapsedNanos) {

        /** Один ли экземпляр получили все потоки. */
        public boolean singleInstance() {
            return distinctInstances == 1;
        }

        /** Создано ли значение ровно один раз. */
        public boolean createdOnce() {
            return creations == 1;
        }
    }

    private CacheRaceRunner() {
    }

    /** Провести гонку {@code threads} потоков за пустой кэш. */
    public static RaceOutcome run(SingletonValue<Object> cache, int threads) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(threads);
        Object[] results = new Object[threads];
        List<Thread> workers = new ArrayList<>(threads);

        for (int i = 0; i < threads; i++) {
            int index = i;
            Thread worker = new Thread(() -> {
                ready.countDown();
                try {
                    if (!startGate.await(60, TimeUnit.SECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                results[index] = cache.get(Object::new);
            }, THREAD_PREFIX + i);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }

        ready.await(60, TimeUnit.SECONDS);
        long start = System.nanoTime();
        startGate.countDown();
        for (Thread worker : workers) {
            worker.join(60_000L);
        }
        long elapsedNanos = System.nanoTime() - start;

        int distinctInstances = (int) Arrays.stream(results).filter(Objects::nonNull).distinct().count();
        return new RaceOutcome(threads, cache.creations(), cache.factoryInvocations(), distinctInstances, elapsedNanos);
    }
}
