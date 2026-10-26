package ru.tsu.tpm.vacancyparser.hw14.loom.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.BenchmarkResult;

/**
 * Один и тот же объём работы на виртуальных потоках и на платформенном пуле.
 *
 * <p>Тесты проверяют инварианты, а не абсолютное время: миллисекунды зависят от машины и загрузки,
 * а вот «все задачи вернули результат», «работали именно виртуальные потоки» и «отменённые задачи
 * учтены» должны выполняться всегда.
 */
class LoomBenchmarksTest {

    @Test
    @DisplayName("Виртуальные потоки выполняют все задачи и действительно виртуальны")
    void virtualRunUsesVirtualThreads() {
        int tasks = 50;
        LoomBenchmarks.Run<Integer> run = LoomBenchmarks.runNamedVirtual(
                "виртуальные", tasks, 10_000L, new SleepyTask(10L));

        BenchmarkResult metrics = run.metrics();
        assertThat(metrics.completed()).isTrue();
        assertThat(metrics.succeeded()).isEqualTo(tasks);
        assertThat(metrics.failed()).isZero();
        assertThat(metrics.cancelled()).isZero();
        assertThat(metrics.virtualOnly()).isTrue();
        assertThat(metrics.platformExecutions()).isZero();
        assertThat(metrics.workerThreads()).isEqualTo(tasks);
        assertThat(metrics.peakConcurrentTasks()).isGreaterThan(1);
        assertThat(metrics.peakConcurrentTasks()).isLessThanOrEqualTo(tasks);
        assertThat(run.results()).containsExactlyElementsOf(sequence(tasks));
    }

    @Test
    @DisplayName("Платформенный пул ограничен своими потоками, но обрабатывает всё")
    void platformRunUsesPoolThreads() {
        int tasks = 60;
        int poolSize = 4;
        LoomBenchmarks.Run<Integer> run = LoomBenchmarks.runPlatform(
                "пул", tasks, poolSize, 10_000L, new SleepyTask(10L));

        BenchmarkResult metrics = run.metrics();
        assertThat(metrics.completed()).isTrue();
        assertThat(metrics.platformOnly()).isTrue();
        assertThat(metrics.virtualExecutions()).isZero();
        assertThat(metrics.workerThreads()).isEqualTo(poolSize);
        assertThat(metrics.peakConcurrentTasks()).isLessThanOrEqualTo(poolSize);
        // platformThreadsPeakDelta здесь не проверяется: ThreadMXBean хранит пик на весь процесс,
        // и в общем прогоне тестов он уже мог быть достигнут раньше (тогда прирост равен 0).
        assertThat(metrics.platformThreadsPeakDelta()).isGreaterThanOrEqualTo(0);
        assertThat(run.results()).hasSize(tasks).containsAll(sequence(tasks));
    }

    @Test
    @DisplayName("Таймаут отменяет задачи, исключение задачи считается сбоем, пустой прогон пуст")
    void timeoutFailuresAndEmptyRun() {
        LoomBenchmarks.Run<Integer> timedOut = LoomBenchmarks.runVirtual(
                "медленные", 4, 100L, new SleepyTask(5_000L));
        assertThat(timedOut.metrics().cancelled()).isEqualTo(4);
        assertThat(timedOut.metrics().completed()).isFalse();
        assertThat(timedOut.metrics().wallMillis()).isLessThan(4_000.0);
        assertThat(timedOut.results()).isEmpty();

        LoomBenchmarks.Run<Integer> withFailure = LoomBenchmarks.runVirtual("сбои", 3, 10_000L, index -> {
            if (index == 1) {
                throw new IllegalStateException("источник недоступен");
            }
            return index;
        });
        assertThat(withFailure.metrics().succeeded()).isEqualTo(2);
        assertThat(withFailure.metrics().failed()).isEqualTo(1);
        assertThat(withFailure.metrics().cancelled()).isZero();
        assertThat(withFailure.metrics().completed()).isTrue();
        assertThat(withFailure.results()).containsExactly(0, 2);

        LoomBenchmarks.Run<Integer> empty = LoomBenchmarks.runVirtual("пусто", 0, 1_000L, index -> index);
        assertThat(empty.metrics().tasks()).isZero();
        assertThat(empty.metrics().completed()).isTrue();
        assertThat(empty.results()).isEmpty();

        assertThatThrownBy(() -> LoomBenchmarks.runVirtual("минус", -1, 1_000L, index -> index))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LoomBenchmarks.runPlatform("пул", 1, 0, 1_000L, index -> index))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LoomBenchmarks.runVirtual("таймаут", 1, 0L, index -> index))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Integer> sequence(int size) {
        return java.util.stream.IntStream.range(0, size).boxed().toList();
    }
}
