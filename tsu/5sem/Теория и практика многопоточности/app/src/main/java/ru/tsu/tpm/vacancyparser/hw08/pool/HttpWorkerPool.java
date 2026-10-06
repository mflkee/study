package ru.tsu.tpm.vacancyparser.hw08.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import ru.tsu.tpm.vacancyparser.hw08.pool.config.RunConfig;

/**
 * Вручную настроенный пул потоков ДЗ 8 и его корректная остановка.
 *
 * <p>Пул собирается из явных параметров, без фабрик {@code Executors.*}: минимальный и максимальный
 * размер, <b>ограниченная</b> ёмкость очереди, время жизни простаивающего сверхядерного потока,
 * собственная фабрика потоков и обработчик отказа. Ограничение очереди принципиально: при
 * неограниченной очереди {@code ThreadPoolExecutor} набирает только потоки ядра, кладёт остальное в
 * очередь и до максимального размера не доходит — «пул из восьми потоков» молча превращается в
 * «ядро + очередь».
 *
 * <p>Потоки пула не демоны, поэтому без остановки процесс не завершится. {@link #shutdown()} сначала
 * инициирует остановку, затем ждёт завершения с таймаутом, а при превышении — принудительно завершает
 * и сообщает, сколько задач не успело.
 */
public final class HttpWorkerPool implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final CountingCallerRunsPolicy rejectionHandler;
    private final long shutdownTimeoutMillis;

    private HttpWorkerPool(
            ThreadPoolExecutor executor, CountingCallerRunsPolicy rejectionHandler, long shutdownTimeoutMillis) {
        this.executor = executor;
        this.rejectionHandler = rejectionHandler;
        this.shutdownTimeoutMillis = shutdownTimeoutMillis;
    }

    /** Создать пул с ограниченной очередью на параметрах конфигурации. */
    public static HttpWorkerPool create(RunConfig config) {
        return create(config, new ArrayBlockingQueue<>(config.queueCapacity()));
    }

    /**
     * Контрольный пул с <b>неограниченной</b> очередью.
     *
     * <p>Нужен только для сравнения: показывает, что при неограниченной очереди пул не растёт выше
     * минимума и максимальный размер недостижим.
     */
    public static HttpWorkerPool createUnbounded(RunConfig config) {
        return create(config, new LinkedBlockingQueue<>());
    }

    private static HttpWorkerPool create(RunConfig config, BlockingQueue<Runnable> queue) {
        CountingCallerRunsPolicy policy = new CountingCallerRunsPolicy();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                config.coreSize(),
                config.maxSize(),
                config.keepAliveMillis(),
                TimeUnit.MILLISECONDS,
                queue,
                new NamedThreadFactory(),
                policy);
        executor.prestartAllCoreThreads();
        return new HttpWorkerPool(executor, policy, config.shutdownTimeoutMillis());
    }

    /** Нижележащий исполнитель — для подачи задач и наблюдения состояния. */
    public ThreadPoolExecutor executor() {
        return executor;
    }

    /** Сколько раз сработал обработчик отказа. */
    public long rejectionCount() {
        return rejectionHandler.rejections();
    }

    /** Имя обработчика отказа. */
    public String rejectionHandlerName() {
        return rejectionHandler.name();
    }

    /** Фактический размер пула (число созданных потоков). */
    public int poolSize() {
        return executor.getPoolSize();
    }

    /** Строки с фактическими настройками пула — для вывода и отчёта. */
    public List<String> settingsLines(RunConfig config) {
        List<String> lines = new ArrayList<>();
        lines.add("минимальный размер пула (ядро): " + config.coreSize());
        lines.add("максимальный размер пула: " + config.maxSize());
        lines.add("ёмкость очереди задач: " + executor.getQueue().remainingCapacity()
                + " из " + config.queueCapacity());
        lines.add("время жизни простаивающего потока: " + executor.getKeepAliveTime(TimeUnit.MILLISECONDS) + " мс");
        lines.add("фабрика потоков: " + NamedThreadFactory.PREFIX + "<N>");
        lines.add("обработчик отказа: " + rejectionHandler.name());
        lines.add("потоков после создания пула: " + executor.getPoolSize());
        return lines;
    }

    /**
     * Остановить пул и вернуть итог остановки.
     *
     * @param terminated  завершились ли все задачи в пределах таймаута
     * @param pendingTasks сколько задач оставалось невыполненными
     * @param message     пояснение
     */
    public record ShutdownOutcome(boolean terminated, long pendingTasks, String message) {
    }

    /** Инициировать остановку, подождать с таймаутом и при необходимости завершить принудительно. */
    public ShutdownOutcome shutdown() {
        executor.shutdown();
        boolean terminated;
        try {
            terminated = executor.awaitTermination(shutdownTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminated = false;
        }
        long pending = Math.max(0L, executor.getTaskCount() - executor.getCompletedTaskCount());
        if (!terminated) {
            executor.shutdownNow();
            return new ShutdownOutcome(
                    false, pending, "задачи не завершились за " + shutdownTimeoutMillis
                    + " мс — принудительное завершение, не завершено задач: " + pending);
        }
        return new ShutdownOutcome(true, 0L, "пул остановлен штатно");
    }

    /** Аварийное закрытие без ожидания (для тестов и уборки). */
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
