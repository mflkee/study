package ru.tsu.tpm.vacancyparser.finaltask.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Конфигурация Итогового ДЗ: собственный пул потоков и включение расписания.
 *
 * <p>Пул собирается вручную, а не через {@code Executors.newFixedThreadPool}: нужны осмысленные имена
 * потоков (иначе в логе видно {@code pool-1-thread-3} и нельзя доказать многопоточность), ограниченная
 * очередь и предсказуемый обработчик отказа. Потоки не демоны: пул обязан останавливаться, и
 * {@code destroyMethod="shutdown"} делает это при закрытии Spring-контекста.
 *
 * <p>{@code @EnableScheduling} включает обработку {@code @Scheduled}; сам периодический обход лежит в
 * {@code CrawlScheduler} и активен только в режиме Итогового ДЗ (по умолчанию).
 */
@Configuration
@EnableScheduling
public class FinalTaskConfig {

    /** Префикс имён потоков пула обхода. */
    public static final String CRAWL_THREAD_PREFIX = "final-crawl-";

    /** Префикс имён потоков локального сервера-заглушки. */
    public static final String STUB_THREAD_PREFIX = "final-stub-";

    /**
     * Пул потоков обхода страниц.
     *
     * <p>При переполнении ограниченной очереди задачу выполняет вызывающий поток
     * ({@link ThreadPoolExecutor.CallerRunsPolicy}): задача не теряется, но обход может замедлиться —
     * это видно в отчёте как рост суммарного времени, а не как пропавший адрес.
     */
    @Bean(name = "crawlExecutor", destroyMethod = "shutdown")
    public ExecutorService crawlExecutor(FinalTaskProperties properties) {
        FinalTaskProperties.Pool pool = properties.getPool();
        if (pool.getSize() < 1) {
            throw new IllegalStateException("app.final.pool.size должен быть не меньше 1");
        }
        if (pool.getQueueCapacity() < 1) {
            throw new IllegalStateException("app.final.pool.queue-capacity должен быть не меньше 1");
        }
        RejectedExecutionHandler rejectionHandler = new ThreadPoolExecutor.CallerRunsPolicy();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                pool.getSize(),
                pool.getSize(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(pool.getQueueCapacity()),
                namedThreadFactory(CRAWL_THREAD_PREFIX, false),
                rejectionHandler);
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }

    /** Фабрика потоков с доменными именами вида {@code prefix + N}. */
    public static ThreadFactory namedThreadFactory(String prefix, boolean daemon) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(daemon);
            return thread;
        };
    }
}
