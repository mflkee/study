package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки вывода на искусственных значениях: без запуска нагрузки проверяется, что нужные числа и
 * пометки попадают в вывод.
 */
class LocksReportTest {

    private static ProducerConsumerRunner.Result result(int produced, int taken, int remaining) {
        Map<String, Integer> perConsumer = new LinkedHashMap<>();
        perConsumer.put("consumer-0", taken / 2);
        perConsumer.put("consumer-1", taken - taken / 2);
        return new ProducerConsumerRunner.Result(
                2, 2, 1, 50, 100, produced,
                java.util.stream.IntStream.range(0, taken).mapToObj(i -> "item-" + i).toList(),
                remaining, perConsumer, 7_500_000L);
    }

    @Test
    @DisplayName("Блок баланса печатает число потоков, элементов и состояние инвариантов")
    void balanceBlockPrintsCountsAndState() {
        String text = String.join("\n", LocksReport.balanceBlock(result(100, 100, 0)));

        assertThat(text)
                .contains("producer'ов: 2")
                .contains("consumer'ов: 2")
                .contains("ёмкость буфера: 1")
                .contains("элементов на producer: 50")
                .contains("ожидалось всего: 100")
                .contains("добавлено: 100, извлечено: 100, осталось в буфере: 0")
                .contains("7.500 мс")
                .contains("ничего не потеряно: да")
                .contains("ничего не продублировано: да")
                .contains("буфер пуст: да");
    }

    @Test
    @DisplayName("Нарушенный инвариант помечается явно, а не тонет в числах")
    void brokenInvariantIsMarked() {
        String text = String.join("\n", LocksReport.balanceBlock(result(100, 60, 40)));

        assertThat(text)
                .contains("ничего не потеряно: НЕТ")
                .contains("буфер пуст: НЕТ");
    }

    @Test
    @DisplayName("Распределение по потокам выводится отдельной таблицей")
    void distributionBlockListsEveryConsumer() {
        String text = String.join("\n", LocksReport.distributionBlock(result(100, 100, 0)));

        assertThat(text)
                .contains("Распределение извлечённых элементов по потокам")
                .contains("consumer-0")
                .contains("consumer-1");
    }

    @Test
    @DisplayName("Блок впустую проснувшихся сопоставляет два механизма")
    void wastedWakeupsBlockComparesMechanisms() {
        String text = String.join("\n", LocksReport.wastedWakeupsBlock(3, 1_500));

        assertThat(text)
                .contains(BufferBenchmark.LOCK_MECHANISM)
                .contains(BufferBenchmark.MONITOR_MECHANISM)
                .contains("3")
                .contains("1500")
                .contains("Два условия против одного");
    }

    @Test
    @DisplayName("Сравнение печатает все прогоны обоих механизмов и разброс")
    void comparisonBlockPrintsEveryRunAndSpread() {
        BufferBenchmark.Comparison comparison = new BufferBenchmark.Comparison(
                2, 2, 1, 50,
                List.of(
                        new BufferBenchmark.Run(BufferBenchmark.LOCK_MECHANISM, 12_000_000L, result(100, 100, 0)),
                        new BufferBenchmark.Run(BufferBenchmark.MONITOR_MECHANISM, 20_000_000L, result(100, 100, 0))));

        String text = String.join("\n", LocksReport.comparisonBlock(comparison));

        assertThat(text)
                .contains("producer'ов: 2")
                .contains("ёмкость: 1")
                .contains("элементов: 100")
                .contains("12.000 мс")
                .contains("20.000 мс")
                .contains("среднее")
                .contains("минимум")
                .contains("максимум");
    }

    @Test
    @DisplayName("Вывод при перекрытии разбросов не объявляет победителя")
    void conclusionRespectsSpread() {
        // Интервалы [10, 20] и [12, 22] пересекаются → победитель не объявляется.
        BufferBenchmark.Comparison overlapping = new BufferBenchmark.Comparison(
                2, 2, 1, 50,
                List.of(
                        new BufferBenchmark.Run(BufferBenchmark.LOCK_MECHANISM, 10_000_000L, result(100, 100, 0)),
                        new BufferBenchmark.Run(BufferBenchmark.LOCK_MECHANISM, 20_000_000L, result(100, 100, 0)),
                        new BufferBenchmark.Run(BufferBenchmark.MONITOR_MECHANISM, 12_000_000L, result(100, 100, 0)),
                        new BufferBenchmark.Run(BufferBenchmark.MONITOR_MECHANISM, 22_000_000L, result(100, 100, 0))));

        assertThat(LocksReport.conclusion(overlapping))
                .contains("укладывается в разброс повторов")
                .contains("2 producer'ах");

        // Интервалы [10, 12] и [30, 32] не пересекаются → победитель назван.
        BufferBenchmark.Comparison disjoint = new BufferBenchmark.Comparison(
                2, 2, 1, 50,
                List.of(
                        new BufferBenchmark.Run(BufferBenchmark.LOCK_MECHANISM, 10_000_000L, result(100, 100, 0)),
                        new BufferBenchmark.Run(BufferBenchmark.LOCK_MECHANISM, 12_000_000L, result(100, 100, 0)),
                        new BufferBenchmark.Run(BufferBenchmark.MONITOR_MECHANISM, 30_000_000L, result(100, 100, 0)),
                        new BufferBenchmark.Run(BufferBenchmark.MONITOR_MECHANISM, 32_000_000L, result(100, 100, 0))));

        assertThat(LocksReport.conclusion(disjoint))
                .contains("быстрее")
                .contains(BufferBenchmark.LOCK_MECHANISM);
    }
}
