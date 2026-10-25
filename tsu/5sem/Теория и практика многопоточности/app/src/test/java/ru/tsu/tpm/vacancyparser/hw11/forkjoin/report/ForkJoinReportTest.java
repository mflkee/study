package ru.tsu.tpm.vacancyparser.hw11.forkjoin.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.count.CountSummary;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountResult;

/**
 * Строки отчёта ДЗ 11: таблица, ускорение, проверка разбиения.
 */
class ForkJoinReportTest {

    private static CountSummary summary(
            String label, long nanos, int batches, int tasks, long steals, int parallelism, CountResult result) {
        return new CountSummary(
                label, result, nanos, batches, tasks, steals, parallelism,
                Set.of("hw11-forkjoin-1"), 100, batches);
    }

    @Test
    @DisplayName("Ускорение считается по времени базы и варианта")
    void speedupComputed() {
        CountSummary baseline = summary("последовательно", 40_000_000L, 1, 1, 0, 1, CountResult.empty());
        CountSummary parallel = summary("Fork/Join (партия 100)", 10_000_000L, 10, 19, 7, 8, CountResult.empty());

        assertThat(ForkJoinReport.speedup(baseline, parallel)).isEqualTo(4.0);
        assertThat(ForkJoinReport.comparisonLine(baseline, parallel))
                .contains("последовательно 40.000 мс")
                .contains("Fork/Join 10.000 мс")
                .contains("ускорение 4.00×")
                .contains("параллелизме 8");
        assertThat(ForkJoinReport.speedup(null, parallel)).isZero();
        assertThat(ForkJoinReport.comparisonLine(null, parallel)).contains("сравнение недоступно");
    }

    @Test
    @DisplayName("Строка таблицы содержит время, ускорение, разбиение и кражи")
    void tableRowContainsAllColumns() {
        CountSummary baseline = summary("последовательно", 20_000_000L, 1, 1, 0, 1, CountResult.empty());
        CountSummary parallel = summary(
                "Fork/Join (партия 100)", 5_000_000L, 10, 19, 3, 8,
                new CountResult(1_000, 100, 20, 9_000_000, 250_000));

        String header = ForkJoinReport.tableHeader();
        String row = ForkJoinReport.tableRow(parallel, baseline);

        assertThat(header).contains("вариант").contains("время, мс").contains("партий").contains("краж");
        assertThat(row)
                .contains("Fork/Join (партия 100)")
                .contains("5.000")
                .contains("4.00×")
                .contains("10")
                .contains("19")
                .contains("3");
    }

    @Test
    @DisplayName("Сводка сообщает о соответствии числа партий ожидаемому")
    void summaryLinesCheckBatches() {
        CountSummary summary = summary(
                "Fork/Join (партия 100)", 1_000_000L, 10, 19, 0, 4,
                new CountResult(1_000, 200, 10, 20_000_000, 200_000));

        assertThat(ForkJoinReport.summaryLines(summary))
                .anySatisfy(line -> assertThat(line).contains("партий 10 из 10"))
                .anySatisfy(line -> assertThat(line).contains("партии соответствуют ожидаемым: да"))
                .anySatisfy(line -> assertThat(line).contains("подошло 200"));
    }

    @Test
    @DisplayName("Сравнение агрегатов — по значению")
    void sameResultByValue() {
        CountResult result = new CountResult(10, 5, 2, 500_000, 120_000);
        CountSummary first = summary("a", 1_000L, 1, 1, 0, 1, result);
        CountSummary second = summary("b", 2_000L, 1, 1, 0, 1, new CountResult(10, 5, 2, 500_000, 120_000));
        CountSummary third = summary("c", 2_000L, 1, 1, 0, 1, new CountResult(10, 4, 2, 500_000, 120_000));

        assertThat(ForkJoinReport.sameResult(first, second)).isTrue();
        assertThat(ForkJoinReport.sameResult(first, third)).isFalse();
    }
}
