package ru.tsu.tpm.vacancyparser.finaltask.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Очередь адресов: отсечение дубликатов, повторная постановка после обработки, конкурентные постановщики.
 */
class CrawlQueueTest {

    @Test
    @DisplayName("Дубликаты и не-HTTP адреса не попадают в очередь")
    void rejectsDuplicatesAndUnsupportedSchemes() {
        CrawlQueue queue = new CrawlQueue();

        int accepted = queue.offerAll(java.util.Arrays.asList(
                "http://example.local/1",
                "http://example.local/1",
                " https://example.local/2 ",
                "file:///etc/passwd",
                "javascript:alert(1)",
                " ",
                null));

        assertThat(accepted).isEqualTo(2);
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.pollBatch(10)).containsExactly("http://example.local/1", "https://example.local/2");
    }

    @Test
    @DisplayName("После обработки адрес можно поставить снова — иначе не было бы обновления данных")
    void allowsRequeueAfterProcessing() {
        CrawlQueue queue = new CrawlQueue();

        assertThat(queue.offer("http://example.local/1")).isTrue();
        assertThat(queue.offer("http://example.local/1")).isFalse();
        assertThat(queue.pollBatch(1)).containsExactly("http://example.local/1");
        assertThat(queue.offer("http://example.local/1")).isTrue();
    }

    @Test
    @DisplayName("Восемь потоков ставят 1600 адресов — ни один не теряется и не дублируется")
    void isSafeForConcurrentProducers() throws Exception {
        CrawlQueue queue = new CrawlQueue();
        int threads = 8;
        int perThread = 200;
        ExecutorService producers = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                int producer = thread;
                futures.add(producers.submit(() -> {
                    start.await();
                    for (int index = 0; index < perThread; index++) {
                        queue.offer("http://example.local/" + producer + "/" + index);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            producers.shutdownNow();
        }

        List<String> all = queue.pollBatch(threads * perThread + 1);
        assertThat(all).hasSize(threads * perThread);
        assertThat(all).doesNotHaveDuplicates();
    }
}
