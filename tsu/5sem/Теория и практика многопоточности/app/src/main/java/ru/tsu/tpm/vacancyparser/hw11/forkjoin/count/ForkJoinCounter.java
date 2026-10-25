package ru.tsu.tpm.vacancyparser.hw11.forkjoin.count;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.AggregatedVacancy;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountCriteria;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountResult;

/**
 * Параллельный подсчёт на {@link ForkJoinPool} собственным рекурсивным разбиением.
 *
 * <p>Пул создаётся явно, с заданным параллелизмом и собственной фабрикой потоков
 * {@code hw11-forkjoin-N}. Явный пул нужен по двум причинам: во-первых, размер параллелизма — параметр
 * эксперимента, а не «сколько дала JVM»; во-вторых, общий {@code commonPool} используется всем
 * приложением, и его числа зависят от чужой нагрузки.
 *
 * <p>Число краж ({@code getStealCount()}) снимается как разница до и после прогона: у пула это
 * накопительный счётчик, и «всего за жизнь» к отчёту об одном замере отношения не имеет.
 *
 * <p>Потоки пула — демоны, но пул всё равно закрывается методом {@link #close()}: тесты и повторные
 * прогоны не должны оставлять за собой парк потоков. {@code invoke} блокирует вызывающий поток до
 * готовности результата, поэтому после него пул уже простаивает.
 */
public final class ForkJoinCounter implements AutoCloseable {

    /** Префикс имён потоков пула. */
    public static final String THREAD_PREFIX = "hw11-forkjoin-";

    private static final long SHUTDOWN_TIMEOUT_MILLIS = 2_000L;

    private final ForkJoinPool pool;
    private final int parallelism;

    /** Создать пул с заданным параллелизмом. */
    public ForkJoinCounter(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("параллелизм должен быть положительным, получено " + parallelism);
        }
        this.parallelism = parallelism;
        AtomicInteger counter = new AtomicInteger();
        this.pool = new ForkJoinPool(
                parallelism,
                forkJoinPool -> {
                    ForkJoinWorkerThread thread =
                            ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(forkJoinPool);
                    thread.setName(THREAD_PREFIX + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                null,
                false);
    }

    /** Параллелизм пула. */
    public int parallelism() {
        return parallelism;
    }

    /**
     * Посчитать набор на пуле.
     *
     * @param data      агрегированные записи
     * @param criteria  критерий подсчёта
     * @param batchSize максимальный размер партии
     */
    public CountSummary count(List<AggregatedVacancy> data, CountCriteria criteria, int batchSize) {
        if (data == null || criteria == null) {
            throw new IllegalArgumentException("набор и критерий обязательны");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("размер партии должен быть положительным, получено " + batchSize);
        }
        CounterStats stats = new CounterStats();
        long stealsBefore = pool.getStealCount();
        BatchCountTask task = new BatchCountTask(data, 0, data.size(), criteria, batchSize, stats);

        long start = System.nanoTime();
        CountResult result = pool.invoke(task);
        long durationNanos = System.nanoTime() - start;

        return new CountSummary(
                "Fork/Join (партия " + batchSize + ")",
                result,
                durationNanos,
                stats.batches(),
                stats.tasks(),
                pool.getStealCount() - stealsBefore,
                parallelism,
                stats.threads().isEmpty() ? Set.of(Thread.currentThread().getName()) : stats.threads(),
                batchSize,
                BatchCountTask.expectedBatches(data.size(), batchSize));
    }

    /** Завершён ли пул. */
    public boolean isTerminated() {
        return pool.isTerminated();
    }

    /** Остановить пул: инициация, ожидание, при необходимости принудительное завершение. */
    @Override
    public void close() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }
}
