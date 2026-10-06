package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Сравнительный замер: три механизма на одинаковой нагрузке, все прогоны в выводе.
 */
class AtomicsBenchmarkTest {

    private static final int THREADS = 8;
    private static final int OPERATIONS_PER_THREAD = 100_000;
    private static final int RUNS = 2;

    @Test
    @Timeout(180)
    @DisplayName("В сравнении все три механизма: корректные точны, дефектный теряет обновления")
    void comparisonCoversAllVariants() throws InterruptedException {
        AtomicsBenchmark.CounterComparison comparison =
                AtomicsBenchmark.compareCounters(THREADS, OPERATIONS_PER_THREAD, RUNS);

        assertThat(comparison.expected()).isEqualTo((long) THREADS * OPERATIONS_PER_THREAD);
        assertThat(comparison.runs()).hasSize(RUNS * CounterVariant.values().length);
        assertThat(comparison.nanosFor(CounterVariant.MONITOR.label())).hasSize(RUNS);
        assertThat(comparison.nanosFor(CounterVariant.ATOMIC.label())).hasSize(RUNS);
        assertThat(comparison.nanosFor(CounterVariant.VOLATILE_BROKEN.label())).hasSize(RUNS);

        assertThat(comparison.correctVariantsExact())
                .as("монитор и AtomicInteger обязаны быть точными во всех прогонах")
                .isTrue();
        assertThat(comparison.brokenVariantLost())
                .as("учебный volatile-счётчик обязан терять обновления — иначе сравнение бессмысленно")
                .isTrue();
    }

    @Test
    @Timeout(180)
    @DisplayName("В вывод попадают все прогоны каждого механизма, а не только лучший")
    void everyRunIsPrinted() throws InterruptedException {
        AtomicsBenchmark.CounterComparison comparison =
                AtomicsBenchmark.compareCounters(4, 20_000, 3);

        String text = String.join("\n", AtomicsReport.counterComparisonBlock(comparison));

        for (CounterVariant variant : CounterVariant.values()) {
            assertThat(text).contains(variant.label());
            for (long nanos : comparison.nanosFor(variant.label())) {
                assertThat(text)
                        .as("прогон %.3f мс обязан быть в выводе", nanos / 1_000_000.0)
                        .contains(String.format(java.util.Locale.ROOT, "%9.3f мс", nanos / 1_000_000.0));
            }
        }
        assertThat(text).contains("среднее").contains("минимум").contains("максимум");
    }

    @Test
    @Timeout(180)
    @DisplayName("Формат вывода не зависит от чисел: повтор даёт ту же структуру")
    void outputFormatIsStableAcrossRuns() throws InterruptedException {
        AtomicsBenchmark.CounterComparison first =
                AtomicsBenchmark.compareCounters(4, 20_000, 1);
        AtomicsBenchmark.CounterComparison second =
                AtomicsBenchmark.compareCounters(4, 20_000, 1);

        assertThat(skeleton(AtomicsReport.counterComparisonBlock(second)))
                .as("структура вывода обязана совпадать, расходятся только числа")
                .isEqualTo(skeleton(AtomicsReport.counterComparisonBlock(first)));
    }

    @Test
    @Timeout(120)
    @DisplayName("Вывод о разнице привязан к измерению и не объявляет победителя без оснований")
    void conclusionIsTiedToMeasurement() throws InterruptedException {
        AtomicsBenchmark.CounterComparison comparison =
                AtomicsBenchmark.compareCounters(THREADS, OPERATIONS_PER_THREAD, 2);

        assertThat(AtomicsReport.conclusion(comparison))
                .containsAnyOf("быстрее", "укладывается в разброс");
        assertThat(AtomicsReport.volatileVerdict(comparison))
                .contains("потерял обновления");
    }

    @Test
    @Timeout(180)
    @DisplayName("Нагрузочный прогон охватывает три механизма и не оставляет живых потоков")
    void mechanismsLoadCoversThreeMechanisms() throws InterruptedException {
        AtomicsBenchmark.MechanismsOutcome outcome =
                AtomicsBenchmark.runMechanismsLoad(4, 20_000);

        assertThat(outcome.stopStoppedInTime())
                .as("остановка по volatile-флагу обязана укладываться в отведённое время")
                .isTrue();
        assertThat(outcome.counterExact())
                .as("счётчик точен: %d из %d", outcome.atomicValue(), outcome.atomicExpected())
                .isTrue();
        assertThat(outcome.cacheCreatedOnce())
                .as("кэш создан ровно один раз")
                .isTrue();
        assertThat(outcome.liveThreads())
                .as("после прогона не должно оставаться живых потоков демонстрации")
                .isEmpty();
    }

    /** Сравнение структуры: числа заменяются, пробелы выравнивания сжимаются. */
    private static List<String> skeleton(List<String> lines) {
        return lines.stream()
                .map(line -> line.replaceAll("[0-9]+", "N").replaceAll(" +", " "))
                .toList();
    }
}
