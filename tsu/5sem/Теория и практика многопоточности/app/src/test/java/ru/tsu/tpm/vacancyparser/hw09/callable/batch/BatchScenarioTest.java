package ru.tsu.tpm.vacancyparser.hw09.callable.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw09.callable.WorkerThreads;

/**
 * Набор задач и invokeAll: результаты в порядке списка, запуск одновременный.
 */
class BatchScenarioTest {

    private static final int TASKS = 5;
    private static final long MIN_MILLIS = 150L;
    private static final long MAX_MILLIS = 400L;

    @Test
    @Timeout(60)
    @DisplayName("invokeAll возвращает результат каждой задачи в порядке исходного списка")
    void resultsFollowInputOrder() throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(TASKS, WorkerThreads.factory("hw09-test-batch-"));
        try {
            BatchScenario.BatchOutcome outcome =
                    BatchScenario.run(pool, TASKS, MIN_MILLIS, MAX_MILLIS, 42L);

            assertThat(outcome.results()).containsExactly(1, 2, 3, 4, 5);
            assertThat(outcome.delaysMillis()).hasSize(TASKS);
            assertThat(outcome.delaysMillis()).allSatisfy(
                    delay -> assertThat(delay).isBetween(MIN_MILLIS, MAX_MILLIS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Задачи выполняются одновременно: время набора меньше суммы длительностей")
    void tasksRunConcurrently() throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(TASKS, WorkerThreads.factory("hw09-test-batch-"));
        try {
            BatchScenario.BatchOutcome outcome =
                    BatchScenario.run(pool, TASKS, MIN_MILLIS, MAX_MILLIS, 7L);

            assertThat(outcome.totalMillis())
                    .as("время набора (%.1f мс) должно быть меньше суммы длительностей (%d мс)",
                            outcome.totalMillis(), outcome.sumDelayMillis())
                    .isLessThan(outcome.sumDelayMillis());
            long maxStartOffsetMillis = outcome.startOffsetsNanos().stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L) / 1_000_000L;
            assertThat(maxStartOffsetMillis)
                    .as("задачи стартовали почти одновременно (разброс %d мс)", maxStartOffsetMillis)
                    .isLessThan(MIN_MILLIS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Задача случайной длительности: длительность в диапазоне, результат — идентификатор")
    void randomDelayTaskIsBounded() {
        RandomDelayTask task = new RandomDelayTask(3, 100L, 200L, 5L);

        assertThat(task.delayMillis()).isBetween(100L, 200L);
        assertThat(task.id()).isEqualTo(3);
        assertThatThrownBy(() -> new RandomDelayTask(1, 200L, 100L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
