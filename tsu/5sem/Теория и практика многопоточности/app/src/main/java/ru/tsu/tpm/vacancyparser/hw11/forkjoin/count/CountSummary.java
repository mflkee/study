package ru.tsu.tpm.vacancyparser.hw11.forkjoin.count;

import java.util.Locale;
import java.util.Set;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountResult;

/**
 * Итог одного подсчёта: агрегат, время, разбиение и наблюдения за пулом.
 *
 * @param label           название варианта (например, «Fork/Join, партия 5000»)
 * @param result          агрегат подсчёта
 * @param durationNanos   длительность
 * @param batches         сколько партий реально посчитано
 * @param tasks           сколько задач создано
 * @param steals          сколько задач украдено пулом за прогон
 * @param parallelism     параллелизм пула (1 для последовательного варианта)
 * @param threads         потоки, выполнявшие подсчёт
 * @param batchSize       размер партии (0 для последовательного варианта)
 * @param expectedBatches ожидаемое число партий
 */
public record CountSummary(
        String label,
        CountResult result,
        long durationNanos,
        int batches,
        int tasks,
        long steals,
        int parallelism,
        Set<String> threads,
        int batchSize,
        int expectedBatches) {

    public CountSummary {
        threads = Set.copyOf(threads);
    }

    /** Длительность в миллисекундах. */
    public double durationMillis() {
        return durationNanos / 1_000_000.0;
    }

    /** Совпадает ли число партий с ожидаемым. */
    public boolean batchesAsExpected() {
        return batches == expectedBatches;
    }

    /** Сколько потоков участвовало. */
    public int threadCount() {
        return threads.size();
    }

    /** Строка для консоли и отчёта. */
    public String reportLine() {
        return String.format(
                Locale.ROOT,
                "%s: %.3f мс, партий %d из %d, задач %d, краж %d, потоков %d",
                label, durationMillis(), batches, expectedBatches, tasks, steals, threadCount());
    }
}
