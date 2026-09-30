package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Сравнительный замер: оба механизма на одинаковой нагрузке, все прогоны в выводе.
 */
class BufferBenchmarkTest {

    private static final int THREADS = 3;
    private static final int PER_PRODUCER = 300;
    private static final int SMALL_PER_PRODUCER = 50;
    private static final int RUNS = 3;

    @Test
    @DisplayName("В выводе оба механизма: время, число потоков, ёмкость и число элементов")
    @Timeout(180)
    void comparisonShowsBothMechanismsAndLoad() throws InterruptedException {
        BufferBenchmark.Comparison comparison =
                BufferBenchmark.compare(THREADS, THREADS, 2, PER_PRODUCER, RUNS);

        String text = String.join("\n", LocksReport.comparisonBlock(comparison));

        assertThat(text)
                .contains(BufferBenchmark.LOCK_MECHANISM)
                .contains(BufferBenchmark.MONITOR_MECHANISM)
                .contains("producer'ов: 3")
                .contains("consumer'ов: 3")
                .contains("ёмкость: 2")
                .contains("элементов: 900")
                .contains("среднее")
                .contains("минимум")
                .contains("максимум");
        assertThat(comparison.nanosFor(BufferBenchmark.LOCK_MECHANISM)).hasSize(RUNS);
        assertThat(comparison.nanosFor(BufferBenchmark.MONITOR_MECHANISM)).hasSize(RUNS);
    }

    @Test
    @DisplayName("В вывод попадают все прогоны, а не только лучший")
    @Timeout(180)
    void everyRunIsPrinted() throws InterruptedException {
        BufferBenchmark.Comparison comparison =
                BufferBenchmark.compare(THREADS, THREADS, 1, PER_PRODUCER, RUNS);

        String text = String.join("\n", LocksReport.comparisonBlock(comparison));

        for (long nanos : comparison.nanosFor(BufferBenchmark.LOCK_MECHANISM)) {
            assertThat(text)
                    .as("прогон %.3f мс обязан быть в выводе", nanos / 1_000_000.0)
                    .contains(String.format(Locale.ROOT, "%9.3f мс", nanos / 1_000_000.0));
        }
        assertThat(comparison.runs()).hasSize(RUNS * 2);
    }

    @Test
    @DisplayName("Формат вывода не зависит от чисел: повтор даёт ту же структуру")
    @Timeout(180)
    void outputFormatIsStableAcrossRuns() throws InterruptedException {
        BufferBenchmark.Comparison first =
                BufferBenchmark.compare(THREADS, THREADS, 2, SMALL_PER_PRODUCER, 1);
        BufferBenchmark.Comparison second =
                BufferBenchmark.compare(THREADS, THREADS, 2, SMALL_PER_PRODUCER, 1);

        assertThat(skeleton(LocksReport.comparisonBlock(second)))
                .as("структура вывода обязана совпадать, расходятся только числа")
                .isEqualTo(skeleton(LocksReport.comparisonBlock(first)));
    }

    @Test
    @DisplayName("Инварианты выполнены во всех прогонах обоих механизмов")
    @Timeout(180)
    void allRunsAreConsistent() throws InterruptedException {
        BufferBenchmark.Comparison comparison =
                BufferBenchmark.compare(THREADS, THREADS, 1, PER_PRODUCER, RUNS);

        assertThat(comparison.allConsistent())
                .as("сравнивать быстрое решение с неправильным нельзя")
                .isTrue();
        assertThat(LocksReport.conclusion(comparison))
                .as("вывод привязан к числу потоков и либо называет разницу, либо говорит о её отсутствии")
                .containsAnyOf("быстрее", "укладывается в разброс повторов");
    }

    /** Сравнение структуры: числа заменяются, чтобы формат можно было сверить между прогонами. */
    private static List<String> skeleton(List<String> lines) {
        return lines.stream().map(line -> line.replaceAll("[0-9]+", "N")).toList();
    }
}
