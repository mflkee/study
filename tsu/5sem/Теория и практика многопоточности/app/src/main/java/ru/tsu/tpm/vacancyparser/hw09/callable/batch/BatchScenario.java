package ru.tsu.tpm.vacancyparser.hw09.callable.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Набор задач, запускаемых одновременно через {@code invokeAll}.
 *
 * <p>{@link ExecutorService#invokeAll} отправляет все задачи, блокируется до завершения всех и
 * возвращает результаты **в порядке входного списка**. Одновременность здесь — не декларация, а
 * измеримый факт: суммарное время набора близко к самой долгой задаче, а не к сумме всех. Моменты
 * старта задач тоже фиксируются — по ним видно, что они начались почти одновременно.
 */
public final class BatchScenario {

    /**
     * Итог набора.
     *
     * @param results        результаты в порядке задач
     * @param delaysMillis   заданные длительности задач
     * @param startOffsetsNanos смещения старта задач относительно первой (наносекунды)
     * @param totalNanos     суммарное время набора
     */
    public record BatchOutcome(
            List<Integer> results, List<Long> delaysMillis, List<Long> startOffsetsNanos, long totalNanos) {

        public BatchOutcome {
            results = List.copyOf(results);
            delaysMillis = List.copyOf(delaysMillis);
            startOffsetsNanos = List.copyOf(startOffsetsNanos);
        }

        /** Суммарное время набора в миллисекундах. */
        public double totalMillis() {
            return totalNanos / 1_000_000.0;
        }

        /** Самая долгая задача. */
        public long maxDelayMillis() {
            return delaysMillis.stream().mapToLong(Long::longValue).max().orElse(0L);
        }

        /** Сумма длительностей всех задач. */
        public long sumDelayMillis() {
            return delaysMillis.stream().mapToLong(Long::longValue).sum();
        }
    }

    private BatchScenario() {
    }

    /**
     * Запустить {@code count} задач случайной длительности через {@code invokeAll}.
     *
     * @param pool      исполнитель (число потоков должно позволять параллельный запуск)
     * @param count     число задач (по заданию 3–5)
     * @param minMillis нижняя граница длительности
     * @param maxMillis верхняя граница длительности
     * @param seed      зерно для воспроизводимости длительностей
     */
    public static BatchOutcome run(
            ExecutorService pool, int count, long minMillis, long maxMillis, long seed)
            throws InterruptedException, ExecutionException {
        List<RandomDelayTask> tasks = new ArrayList<>(count);
        List<Callable<Integer>> callables = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RandomDelayTask task = new RandomDelayTask(i + 1, minMillis, maxMillis, seed + i);
            tasks.add(task);
            callables.add(task);
        }

        long start = System.nanoTime();
        List<Future<Integer>> futures = pool.invokeAll(callables);
        long totalNanos = System.nanoTime() - start;

        List<Integer> results = new ArrayList<>(count);
        for (Future<Integer> future : futures) {
            results.add(future.get());
        }

        long firstStart = tasks.stream().mapToLong(RandomDelayTask::startedAtNanos).min().orElse(0L);
        List<Long> offsets = new ArrayList<>(count);
        List<Long> delays = new ArrayList<>(count);
        for (RandomDelayTask task : tasks) {
            offsets.add(task.startedAtNanos() - firstStart);
            delays.add(task.delayMillis());
        }

        return new BatchOutcome(results, delays, offsets, totalNanos);
    }
}
