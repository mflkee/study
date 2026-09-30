package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки вывода замеров на искусственных значениях: без запуска нагрузки проверяется, что нужные
 * числа и пометки попадают в вывод, а формат не зависит от конкретных цифр.
 */
class SynchronizationReportTest {

    private static SyncBenchmark.CounterRun counterRun(String protection, long nanos, long value) {
        return new SyncBenchmark.CounterRun(protection, nanos, value, 100_000L);
    }

    private static SyncBenchmark.CounterComparison comparison(boolean unprotectedLoses) {
        return new SyncBenchmark.CounterComparison(
                8,
                12_500,
                100_000L,
                List.of(
                        counterRun(Protection.ON.label(), 30_000_000L, 100_000L),
                        counterRun(Protection.ON.label(), 26_000_000L, 100_000L),
                        counterRun(
                                Protection.OFF.label(),
                                9_000_000L,
                                unprotectedLoses ? 87_431L : 100_000L),
                        counterRun(
                                Protection.OFF.label(),
                                8_000_000L,
                                unprotectedLoses ? 91_005L : 100_000L)));
    }

    @Test
    @DisplayName("Вывод сравнения содержит оба режима и все прогоны, а не только лучший")
    void counterBlockShowsEveryRun() {
        String text = String.join("\n", SynchronizationReport.counterComparisonBlock(comparison(true)));

        assertThat(text)
                .contains(Protection.ON.label())
                .contains(Protection.OFF.label())
                .contains("потоков: 8")
                .contains("операций на поток: 12500")
                .contains("ожидаемый итог: 100000")
                .contains("среднее по прогонам");
        assertThat(text)
                .as("оба прогона каждого режима обязаны быть в выводе")
                .contains("30.000 мс")
                .contains("26.000 мс")
                .contains("9.000 мс")
                .contains("8.000 мс");
        assertThat(text).contains("потеряно 12569").contains("потеряно 8995");
    }

    @Test
    @DisplayName("Точный счётчик печатается без пометки о потерях")
    void exactCounterHasNoLossMark() {
        String text = String.join("\n", SynchronizationReport.counterComparisonBlock(comparison(false)));

        assertThat(text).doesNotContain("потеряно");
    }

    @Test
    @DisplayName("Оценка цены синхронизации привязана к измеренным числам и числу потоков")
    void costStatementUsesMeasuredNumbers() {
        String text = SynchronizationReport.synchronizationCost(comparison(false));

        assertThat(text)
                .contains("при 8 потоках")
                .contains("мс против")
                .contains("100000 операций")
                .doesNotContain("всегда");
    }

    @Test
    @DisplayName("Нагрузочный прогон печатает число потоков, операций и состояние")
    void mixedLoadBlockPrintsCountsAndState() {
        SyncBenchmark.MixedLoadOutcome ok = new SyncBenchmark.MixedLoadOutcome(
                8, 20_000, 5_000, 160_000, 160_000L, 160_000L, List.of(), 2_000_000L, 2_000_000L, 12_345_678L);
        String text = String.join("\n", SynchronizationReport.mixedLoadBlock(ok));

        assertThat(text)
                .contains("потоков: 8")
                .contains("элементов на поток: 20000")
                .contains("переводов на поток: 5000")
                .contains("собрано элементов: 160000")
                .contains("счётчик обработанных: 160000 (ожидалось 160000)")
                .contains("12.346 мс")
                .contains("инвариант счётчика: выполнен")
                .contains("сумма балансов: сохранена");

        SyncBenchmark.MixedLoadOutcome broken = new SyncBenchmark.MixedLoadOutcome(
                8, 20_000, 5_000, 159_998, 160_000L, 159_998L,
                List.of("размер списка 159998 не равен счётчику 160000"),
                2_000_000L, 1_999_500L, 12_345_678L);
        String brokenText = String.join("\n", SynchronizationReport.mixedLoadBlock(broken));

        assertThat(brokenText)
                .contains("инвариант счётчика: НАРУШЕН")
                .contains("НАРУШЕНА");
    }

    @Test
    @DisplayName("Вывод сравнения перевода печатает время и судьбу суммы балансов")
    void transferBlockShowsTimeAndBalanceState() {
        SyncBenchmark.TransferComparison comparison = new SyncBenchmark.TransferComparison(
                8,
                5_000,
                1L,
                List.of(
                        new SyncBenchmark.TransferRun(Protection.ON.label(), 40_000_000L, 2_000_000L, 2_000_000L),
                        new SyncBenchmark.TransferRun(
                                Protection.OFF.label(), 5_000_000L, 2_000_000L, 1_987_654L)));

        String text = String.join("\n", SynchronizationReport.transferComparisonBlock(comparison));

        assertThat(text)
                .contains("потоков: 8")
                .contains("переводов на поток: 5000")
                .contains("сумма перевода: 1")
                .contains("40.000 мс")
                .contains("(сохранена)")
                .contains("5.000 мс")
                .contains("(НАРУШЕНА)")
                .contains("2000000 -> 1987654");
    }
}
