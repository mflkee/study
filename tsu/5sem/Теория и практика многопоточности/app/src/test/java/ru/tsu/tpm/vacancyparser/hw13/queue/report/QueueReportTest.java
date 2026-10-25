package ru.tsu.tpm.vacancyparser.hw13.queue.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.PageResult;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.UrlTask;

/**
 * Строки отчёта ДЗ 13: сводка, целостность, распределение работы, примеры заголовков.
 */
class QueueReportTest {

    private static PageResult success(int page, String consumer) {
        return PageResult.success(new UrlTask("http://local/vacancy/" + page, page),
                200, "Заголовок " + page, 5_000_000L, consumer);
    }

    private static PageResult notFound(int page, String consumer) {
        return PageResult.failure(new UrlTask("http://local/vacancy/" + page, page),
                404, "HTTP 404", 1_000_000L, consumer);
    }

    private static CrawlReport report(int capacity, int maxDepth, long blocked, double blockedMillis, List<PageResult> results) {
        long succeeded = results.stream().filter(PageResult::ok).count();
        return new CrawlReport(
                "очередь на " + capacity, results.size(), results.size(), results.size(),
                (int) succeeded, results.size() - (int) succeeded,
                maxDepth, capacity, 2, 2, blocked, blockedMillis, 1_000.0, true, results);
    }

    @Test
    @DisplayName("Сводка показывает потери, повторы и распределение по потребителям")
    void summaryLines() {
        CrawlReport report = report(2, 2, 5, 120.0, List.of(
                success(0, "hw13-consumer-1"),
                success(1, "hw13-consumer-2"),
                notFound(2, "hw13-consumer-1"),
                success(3, "hw13-consumer-2")));

        List<String> lines = QueueReport.summaryLines(report);

        assertThat(lines)
                .anySatisfy(line -> assertThat(line).contains("очередь на 2"))
                .anySatisfy(line -> assertThat(line).contains("без потерь: да"))
                .anySatisfy(line -> assertThat(line).contains("повторов страниц: нет"))
                .anySatisfy(line -> assertThat(line)
                        .contains("hw13-consumer-1=2")
                        .contains("hw13-consumer-2=2"));
        assertThat(QueueReport.consumerCount(report)).isEqualTo(2);
    }

    @Test
    @DisplayName("Сравнение ёмкостей описывает обратное давление и накопление задач")
    void comparisonLines() {
        CrawlReport small = report(4, 4, 17, 1_542.9, List.of(success(0, "c1")));
        CrawlReport large = report(32, 24, 0, 0.0, List.of(success(0, "c1")));

        assertThat(QueueReport.comparisonLines(small, large))
                .anySatisfy(line -> assertThat(line).contains("ёмкость 4").contains("блокировок put 17"))
                .anySatisfy(line -> assertThat(line).contains("ёмкость 32").contains("блокировок put 0"))
                .anySatisfy(line -> assertThat(line).contains("обратное давление"));
    }

    @Test
    @DisplayName("Примеры заголовков идут по номерам страниц и только для успешных")
    void titleLines() {
        CrawlReport report = report(4, 2, 0, 0.0, List.of(
                success(2, "c2"), notFound(1, "c1"), success(0, "c1")));

        assertThat(QueueReport.titleLines(report, 5)).containsExactly(
                "№0 «Заголовок 0» (поток c1)",
                "№2 «Заголовок 2» (поток c2)");
        assertThat(QueueReport.titleLines(report, 1)).hasSize(1);
        assertThat(QueueReport.titleLines(report, 0)).isEmpty();
    }

    @Test
    @DisplayName("Сбои группируются по кодам ответа")
    void failuresByStatus() {
        CrawlReport report = report(4, 2, 0, 0.0, List.of(
                notFound(1, "c1"), notFound(2, "c2"), success(0, "c1")));
        assertThat(QueueReport.failuresByStatus(report)).contains("код 404: 2");
        assertThat(QueueReport.failuresByStatus(report(4, 0, 0, 0.0, List.of(success(0, "c1")))))
                .contains("сбоев нет");
    }

    @Test
    @DisplayName("Ключевые числа и производные величины отчёта")
    void derivedNumbers() {
        CrawlReport report = report(4, 3, 0, 0.0, List.of(success(0, "c1"), notFound(1, "c2")));

        assertThat(report.lossless()).isTrue();
        assertThat(report.duplicatePages()).isFalse();
        assertThat(report.distinctPages()).isEqualTo(2);
        assertThat(report.throughputPerSecond()).isEqualTo(2.0);
        assertThat(QueueReport.keyNumbers(report))
                .containsEntry("подано адресов", "2")
                .containsEntry("максимум в очереди", "3 из 4");
        assertThat(report.reportLine()).contains("успешно 1").contains("сбоев 1");
    }
}
