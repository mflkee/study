package ru.tsu.tpm.vacancyparser.hw14.loom.benchmark;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntFunction;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.BenchmarkResult;

/**
 * Один и тот же объём работы на разных исполнителях.
 *
 * <p>Класс намеренно не знает, что именно делает задача: он принимает {@link IntFunction} от номера
 * задачи и умеет выполнить её тремя способами:
 *
 * <ul>
 *   <li>{@link #runVirtual} — {@link Executors#newVirtualThreadPerTaskExecutor()}: по виртуальному
 *       потоку на задачу, имена потоков автоматические;</li>
 *   <li>{@link #runNamedVirtual} — тот же режим, но фабрика {@code Thread.ofVirtual().name(prefix, 0)}
 *       даёт предсказуемые имена {@code hw14-vt-0, hw14-vt-1, …} для журнала;</li>
 *   <li>{@link #runPlatform} — {@link Executors#newFixedThreadPool(int)}: задачи встают в очередь и
 *       ждут свободный поток ОС.</li>
 * </ul>
 *
 * <p>Замеряется время от подачи первой задачи до завершения последней, пик одновременно выполнявшихся
 * задач, число разных потоков-исполнителей и прирост пика платформенных потоков JVM. Виртуальные
 * потоки в счётчик {@code ThreadMXBean} не попадают — это не недостаток замера, а суть разницы:
 * десятки тысяч виртуальных потоков не превращаются в десятки тысяч потоков ОС.
 *
 * <p>Таймаут общий на прогон: если задача не успела, она отменяется, а прогон помечается как
 * незавершённый. Так демонстрация не может зависнуть из-за одной медленной задачи, и это же
 * проверяется тестами.
 */
public final class LoomBenchmarks {

    /** Префикс имён виртуальных потоков в именованном режиме. */
    public static final String VIRTUAL_PREFIX = "hw14-vt-";

    /** Префикс имён платформенных потоков пула. */
    public static final String PLATFORM_PREFIX = "hw14-platform-";

    /** Префикс имён виртуальных потоков в опыте с pinning. */
    public static final String PINNING_PREFIX = "hw14-pin-";

    /** Сколько ждать остановки исполнителя после отмены задач. */
    static final long SHUTDOWN_TIMEOUT_MILLIS = 5_000L;

    private LoomBenchmarks() {
    }

    /**
     * Результат прогона: метрики и значения, возвращённые задачами (в порядке номеров, без пропусков,
     * если прогон завершился без отмен).
     *
     * @param metrics метрики прогона
     * @param results значения задач в порядке подачи
     * @param <T>     тип значения задачи
     */
    public record Run<T>(BenchmarkResult metrics, List<T> results) {

        public Run {
            Objects.requireNonNull(metrics, "metrics");
            results = List.copyOf(results);
        }
    }

    /** Виртуальный поток на задачу, автоматические имена. */
    public static <T> Run<T> runVirtual(
            String label, int tasks, long timeoutMillis, IntFunction<T> body) {
        return execute(label, tasks, timeoutMillis, body, Executors.newVirtualThreadPerTaskExecutor());
    }

    /** Виртуальный поток на задачу, имена {@code hw14-vt-0, hw14-vt-1, …}. */
    public static <T> Run<T> runNamedVirtual(
            String label, int tasks, long timeoutMillis, IntFunction<T> body) {
        ThreadFactory factory = Thread.ofVirtual().name(VIRTUAL_PREFIX, 0).factory();
        return execute(label, tasks, timeoutMillis, body, Executors.newThreadPerTaskExecutor(factory));
    }

    /** Платформенный пул фиксированного размера с именованными потоками. */
    public static <T> Run<T> runPlatform(
            String label, int tasks, int poolSize, long timeoutMillis, IntFunction<T> body) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("размер платформенного пула должен быть положительным");
        }
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, PLATFORM_PREFIX + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        return execute(label, tasks, timeoutMillis, body, Executors.newFixedThreadPool(poolSize, factory));
    }

    private static <T> Run<T> execute(
            String label,
            int tasks,
            long timeoutMillis,
            IntFunction<T> body,
            ExecutorService executor) {
        if (tasks < 0) {
            throw new IllegalArgumentException("число задач не может быть отрицательным");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("таймаут должен быть положительным");
        }
        Objects.requireNonNull(body, "body");

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        ConcurrencyMeter meter = new ConcurrencyMeter();
        AtomicReferenceArray<T> slots = new AtomicReferenceArray<>(tasks);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        long heapBefore = MemoryProbe.usedHeapBytes();
        int platformPeakBefore = threadBean.getPeakThreadCount();

        List<Future<?>> futures = new ArrayList<>(tasks);
        long start = System.nanoTime();
        try {
            for (int index = 0; index < tasks; index++) {
                final int taskNumber = index;
                futures.add(executor.submit(() -> {
                    meter.enter(Thread.currentThread());
                    try {
                        slots.set(taskNumber, body.apply(taskNumber));
                        completed.incrementAndGet();
                    } catch (RuntimeException | Error e) {
                        failures.incrementAndGet();
                        throw e;
                    } finally {
                        meter.exit();
                    }
                }));
            }

            long deadline = start + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            int cancelled = 0;
            for (Future<?> future : futures) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    future.cancel(true);
                    cancelled++;
                    continue;
                }
                try {
                    future.get(remaining, TimeUnit.NANOSECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    cancelled++;
                } catch (java.util.concurrent.CancellationException e) {
                    cancelled++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.cancel(true);
                    cancelled++;
                } catch (ExecutionException e) {
                    // Сбой задачи уже учтён в failures; прогон продолжается.
                }
            }
            long wallTimeNanos = System.nanoTime() - start;
            int platformPeakAfter = threadBean.getPeakThreadCount();
            long heapDelta = MemoryProbe.deltaBytes(heapBefore);

            List<T> results = new ArrayList<>(tasks - cancelled);
            for (int index = 0; index < tasks; index++) {
                T value = slots.get(index);
                if (value != null) {
                    results.add(value);
                }
            }

            BenchmarkResult metrics = new BenchmarkResult(
                    label,
                    tasks,
                    completed.get(),
                    failures.get(),
                    cancelled,
                    wallTimeNanos,
                    meter.peak(),
                    meter.workerCount(),
                    meter.virtualExecutions(),
                    meter.platformExecutions(),
                    Math.max(0, platformPeakAfter - platformPeakBefore),
                    heapDelta);
            return new Run<>(metrics, results);
        } finally {
            shutdown(executor);
        }
    }

    private static void shutdown(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
