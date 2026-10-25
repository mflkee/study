package ru.tsu.tpm.vacancyparser.hw13.queue.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.PageResult;
import ru.tsu.tpm.vacancyparser.hw13.queue.report.CrawlReport;
import ru.tsu.tpm.vacancyparser.hw13.queue.web.PageFetcher;
import ru.tsu.tpm.vacancyparser.hw13.queue.web.PageSource;

/**
 * Конвейер целиком: отсутствие потерь и повторов, ограничение очереди, остановка потоков,
 * обработка сбоя страницы как результата.
 */
class CrawlServerTest {

    private static PageSource fakeSource(long delayMillis, int failingEvery) {
        AtomicInteger counter = new AtomicInteger();
        return task -> {
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
            if (failingEvery > 0 && counter.incrementAndGet() % failingEvery == 0) {
                return PageResult.failure(task, 500, "имитация сбоя", 0L, Thread.currentThread().getName());
            }
            return PageResult.success(
                    task, 200, "Заголовок " + task.pageNumber(), 1_000L, Thread.currentThread().getName());
        };
    }

    @Test
    @Timeout(60)
    @DisplayName("Ни одна задача не потеряна и не обработана дважды")
    void noLossNoDuplicates() throws InterruptedException {
        List<String> urls = urls(30);

        CrawlReport report = CrawlServer.run(
                "тест", urls, 3, 3, 2, fakeSource(2L, 0), null, 10_000L);

        assertThat(report.requested()).isEqualTo(30);
        assertThat(report.produced()).isEqualTo(30);
        assertThat(report.consumed()).isEqualTo(30);
        assertThat(report.lossless()).isTrue();
        assertThat(report.duplicatePages()).isFalse();
        assertThat(report.distinctPages()).isEqualTo(30);
        assertThat(report.succeeded()).isEqualTo(30);
        assertThat(report.failed()).isZero();
        assertThat(report.maxQueueDepth()).isLessThanOrEqualTo(report.queueCapacity());
        assertThat(report.stoppedWithinTimeout()).isTrue();
    }

    @Test
    @Timeout(60)
    @DisplayName("Сбой источника превращается в результат-сбой, конвейер продолжается")
    void failuresDoNotStopPipeline() throws InterruptedException {
        CrawlReport report = CrawlServer.run(
                "тест", urls(12), 2, 2, 3, fakeSource(0L, 4), null, 10_000L);

        assertThat(report.consumed()).isEqualTo(12);
        assertThat(report.failed()).isEqualTo(3);
        assertThat(report.succeeded()).isEqualTo(9);
        assertThat(report.results()).filteredOn(result -> !result.ok())
                .allSatisfy(result -> assertThat(result.error()).contains("имитация сбоя"));
    }

    @Test
    @Timeout(60)
    @DisplayName("Работу разбирают несколько потребителей, потоки именованные и завершаются")
    void workIsSpreadBetweenConsumers() throws InterruptedException {
        CrawlReport report = CrawlServer.run(
                "тест", urls(18), 3, 3, 2, fakeSource(5L, 0), null, 10_000L);

        long consumers = report.results().stream().map(PageResult::consumer).distinct().count();
        assertThat(consumers).isGreaterThanOrEqualTo(2);
        assertThat(report.results()).allSatisfy(result ->
                assertThat(result.consumer()).startsWith(CrawlServer.CONSUMER_PREFIX));
        assertThat(ThreadDump.liveThreadsWithPrefixes(
                CrawlServer.PRODUCER_PREFIX, CrawlServer.CONSUMER_PREFIX)).isEmpty();
    }

    @Test
    @Timeout(60)
    @DisplayName("Пустой список: производители сразу отправляют сигналы, потребители завершаются")
    void emptyListStopsImmediately() throws InterruptedException {
        CrawlReport report = CrawlServer.run(
                "тест", List.of(), 2, 2, 4, fakeSource(0L, 0), null, 5_000L);

        assertThat(report.produced()).isZero();
        assertThat(report.consumed()).isZero();
        assertThat(report.lossless()).isTrue();
        assertThat(report.stoppedWithinTimeout()).isTrue();
    }

    @Test
    @Timeout(60)
    @DisplayName("Малая очередь заполняется до предела, большая — копит задачи")
    void capacityChangesBehaviour() throws InterruptedException {
        List<String> urls = urls(20);

        CrawlReport small = CrawlServer.run(
                "малая", urls, 2, 2, 2, fakeSource(10L, 0), null, 10_000L);
        CrawlReport large = CrawlServer.run(
                "большая", urls, 2, 2, 20, fakeSource(10L, 0), null, 10_000L);

        assertThat(small.maxQueueDepth()).isLessThanOrEqualTo(2);
        assertThat(small.blockedPuts()).isPositive();
        assertThat(large.maxQueueDepth()).isGreaterThan(2);
        assertThat(large.blockedPuts()).isZero();
    }

    @Test
    @Timeout(60)
    @DisplayName("Некорректные параметры конвейера отвергаются")
    void invalidParameters() {
        assertThatThrownBy(() -> CrawlServer.run(
                "тест", null, 1, 1, 1, fakeSource(0L, 0), null, 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CrawlServer.run(
                "тест", urls(1), 0, 1, 1, fakeSource(0L, 0), null, 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CrawlServer.run(
                "тест", urls(1), 1, 0, 1, fakeSource(0L, 0), null, 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CrawlServer.run(
                "тест", urls(1), 1, 1, 0, fakeSource(0L, 0), null, 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CrawlServer.run(
                "тест", urls(1), 1, 1, 1, fakeSource(0L, 0), null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(60)
    @DisplayName("Сквозная проверка с локальным сайтом: заголовки извлечены, 404 — исход, а не потеря")
    void endToEndWithLocalSite() throws IOException, InterruptedException {
        try (VacancySiteServer site = VacancySiteServer.start(0L);
                PageFetcher fetcher = new PageFetcher(1_000L, 2_000L)) {
            List<String> urls = site.urls(7);

            CrawlReport report = CrawlServer.run(
                    "сайт", urls, 2, 2, 2, fetcher, null, 10_000L);

            assertThat(report.lossless()).isTrue();
            assertThat(report.consumed()).isEqualTo(7);
            assertThat(report.succeeded()).isEqualTo(6);
            assertThat(report.failed()).isEqualTo(1);
            PageResult notFound = report.results().stream()
                    .filter(result -> !result.ok())
                    .findFirst()
                    .orElseThrow();
            assertThat(notFound.status()).isEqualTo(404);
            assertThat(report.results()).filteredOn(PageResult::ok).allSatisfy(result ->
                    assertThat(result.title()).isEqualTo(VacancySiteServer.expectedTitle(result.pageNumber())));
        }
    }

    private static List<String> urls(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> "http://local/vacancy/" + index)
                .toList();
    }
}
