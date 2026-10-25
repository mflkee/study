package ru.tsu.tpm.vacancyparser.finaltask.config;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Корректная остановка пула обхода при закрытии приложения.
 *
 * <p>{@code destroyMethod = "shutdown"} у бина пула только инициирует остановку и не ждёт завершения
 * задач. Этот бин зависит от пула, поэтому Spring уничтожает его раньше: здесь пул получает
 * {@code shutdown()}, ожидание в пределах {@code app.final.pool.shutdown-timeout-ms} и, если задачи не
 * уложились, {@code shutdownNow()}. Иначе незавершённый HTTP-запрос мог бы задержать выход процесса.
 */
@Component
public class CrawlExecutorShutdown {

    private static final Logger log = LoggerFactory.getLogger(CrawlExecutorShutdown.class);

    private final ExecutorService executor;
    private final FinalTaskProperties properties;

    public CrawlExecutorShutdown(
            @Qualifier("crawlExecutor") ExecutorService executor, FinalTaskProperties properties) {
        this.executor = executor;
        this.properties = properties;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        long timeout = properties.getPool().getShutdownTimeoutMs();
        try {
            if (!executor.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                log.warn("пул обхода не завершился за {} мс — принудительная остановка", timeout);
                executor.shutdownNow();
            } else {
                log.info("пул обхода остановлен");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
