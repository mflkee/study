package ru.tsu.tpm.vacancyparser.hw10.completable.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.CompanyInfo;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.FetchFailure;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.FinalResult;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.VacancyDetails;
import ru.tsu.tpm.vacancyparser.hw10.completable.service.FetchOutcome;

/**
 * Строки отчёта ДЗ 10: чистые функции, поэтому проверяются без запуска конвейера.
 */
class AsyncFetchReportTest {

    private static FinalResult result(String id, boolean fallback) {
        return FinalResult.combine(
                        new VacancyDetails(id, "Java-разработчик", "Томск", 100_000, 150_000),
                        fallback
                                ? CompanyInfo.unknown(id)
                                : new CompanyInfo(id, "ООО «Тест»", 4.0, 3, false))
                .withAttractiveness("java разработчик", 110.0);
    }

    private static FetchOutcome outcome(
            String mode, List<FinalResult> results, List<FetchFailure> failures, long millis) {
        return new FetchOutcome(
                mode, results.size() + failures.size(), results, failures,
                millis * 1_000_000L, Set.of("hw10-async-1"), 2);
    }

    @Test
    @DisplayName("Сравнение времени показывает ускорение и оба измерения")
    void comparisonLineShowsSpeedup() {
        FetchOutcome async = outcome("async", List.of(result("v-01", false)), List.of(), 100L);
        FetchOutcome sync = outcome("sync", List.of(result("v-01", false)), List.of(), 400L);

        assertThat(AsyncFetchReport.speedup(async, sync)).isEqualTo(4.0);
        assertThat(AsyncFetchReport.comparisonLine(async, sync))
                .contains("синхронно 400.000 мс")
                .contains("асинхронно 100.000 мс")
                .contains("ускорение 4.00×");
        assertThat(AsyncFetchReport.sameResults(async, sync)).isTrue();
        assertThat(AsyncFetchReport.comparisonLine(async, outcome("sync", List.of(), List.of(), 0L)))
                .contains("сравнение недоступно");
    }

    @Test
    @DisplayName("Пометки изоляции сбоев различают handle и exceptionally")
    void isolationLinesExplainBothHandlers() {
        FetchOutcome partial = outcome(
                "async",
                List.of(result("v-02", true)),
                List.of(new FetchFailure("v-01", "данные вакансии", "IOException: нет данных")),
                120L);

        List<String> lines = AsyncFetchReport.isolationLines(partial, List.of("v-01", "v-02"), "v-01", "v-02");

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("v-01").contains("handle");
        assertThat(lines.get(1)).contains("v-02").contains("exceptionally");
    }

    @Test
    @DisplayName("Строки результатов содержат сбой и имена потоков, журнал обрезается")
    void resultLinesAndTraceHead() {
        FetchOutcome outcome = outcome(
                "async",
                List.of(result("v-01", false)),
                List.of(new FetchFailure("v-02", "данные вакансии", "IOException: нет данных")),
                90L);

        assertThat(AsyncFetchReport.resultLines(outcome))
                .anySatisfy(line -> assertThat(line).contains("получено 1, сбоев 1"))
                .anySatisfy(line -> assertThat(line).contains("v-01"))
                .anySatisfy(line -> assertThat(line).contains("сбой v-02"));

        List<String> trace = List.of("a", "b", "c", "d");
        assertThat(AsyncFetchReport.traceHead(trace, 2)).containsExactly("a", "b");
        assertThat(AsyncFetchReport.traceHead(trace, 0)).isEmpty();
        assertThat(AsyncFetchReport.traceHead(List.of(), 5)).isEmpty();
    }

    @Test
    @DisplayName("Итог прогона: идентификаторы в порядке поступления и число потоков")
    void outcomeReportsItself() {
        FetchOutcome outcome = new FetchOutcome(
                "async", 3, List.of(result("v-01", false), result("v-02", true)),
                List.of(new FetchFailure("v-03", "данные вакансии", "сбой")),
                250_000_000L, Set.of("hw10-async-2", "hw10-async-1"), 6);

        assertThat(outcome.durationMillis()).isEqualTo(250.0);
        assertThat(outcome.threadCount()).isEqualTo(2);
        assertThat(outcome.threadNames()).isEqualTo("hw10-async-1, hw10-async-2");
        assertThat(AsyncFetchReport.ids(outcome)).containsExactly("v-01", "v-02");
        assertThat(outcome.summaryLine())
                .contains("запрошено 3")
                .contains("получено 2")
                .contains("сбоев 1")
                .contains("потоков 2");
    }
}
