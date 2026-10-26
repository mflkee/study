package ru.tsu.tpm.vacancyparser.hw14.loom.pinning;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.ConcurrencyMeter;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.LoomBenchmarks;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.SchedulerInfo;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.ThreadProbe;

/**
 * Опыт «pinning»: одинаковые блокирующие операции под {@code synchronized} и под
 * {@code ReentrantLock}.
 *
 * <p><b>Что такое pinning.</b> Виртуальный поток снимается с carrier-потока на время блокировки —
 * именно поэтому тысячи виртуальных потоков уживаются на десятке потоков ОС. Но сняться можно не
 * всегда: внутри блока {@code synchronized} поток удерживает монитор, и планировщик не может
 * «заморозить» его на полпути. Виртуальный поток <b>пришпиливается</b> (pinned) к carrier-потоку и
 * блокирует его на всё время ожидания. {@code ReentrantLock} устроен как очередь ожидания на
 * уровне JDK, а не как монитор JVM, поэтому с ним виртуальный поток снимается штатно.
 *
 * <p><b>Как поставлен опыт.</b> У каждой задачи <b>свой</b> монитор/замок, поэтому состязания за
 * общий ресурс нет — единственная разница между прогонами в том, пришпилен поток или нет. Внутри
 * секции задача «ждёт» {@code blockMillis} миллисекунд. Если пришпиливание работает, одновременно
 * ждать могут не более {@code parallelism} задач, и прогон растягивается на
 * {@code ceil(tasks / parallelism)} кругов. Если поток снимается — все задачи ждут параллельно, и
 * прогон длится около одной блокировки.
 *
 * <p><b>Что измеряется.</b> Пик одновременно выполнявшихся задач (счётчик внутри задач), число
 * задействованных carrier-потоков и число смен carrier'а после блокировки. Последнее — прямая улика:
 * пришпиленный поток не мог сменить carrier, свободный — мог. Время тоже печатается, но вывод
 * держится не на нём: тесты проверяют структурные признаки, а не абсолютные миллисекунды.
 */
public final class PinningExperiment {

    /** Сколько ждать сверх теоретического времени пришпиленного прогона. */
    private static final long TIMEOUT_MARGIN_MILLIS = 15_000L;

    private PinningExperiment() {
    }

    /** Прогон с {@code synchronized}-блоком вокруг блокирующей операции. */
    public static PinningRun runSynchronized(int tasks, long blockMillis) {
        return run("synchronized", tasks, blockMillis, true);
    }

    /** Прогон с {@code ReentrantLock} вокруг той же блокирующей операции. */
    public static PinningRun runReentrantLock(int tasks, long blockMillis) {
        return run("ReentrantLock", tasks, blockMillis, false);
    }

    private static PinningRun run(String lockKind, int tasks, long blockMillis, boolean useMonitor) {
        if (tasks < 0) {
            throw new IllegalArgumentException("число задач не может быть отрицательным");
        }
        if (blockMillis < 0) {
            throw new IllegalArgumentException("длительность блокировки не может быть отрицательной");
        }

        int parallelism = SchedulerInfo.parallelism();
        Object[] monitors = new Object[tasks];
        ReentrantLock[] locks = new ReentrantLock[tasks];
        for (int index = 0; index < tasks; index++) {
            monitors[index] = new Object();
            locks[index] = new ReentrantLock();
        }

        ConcurrencyMeter meter = new ConcurrencyMeter();
        Set<String> carriers = ConcurrentHashMap.newKeySet();
        AtomicInteger switches = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        ExecutorService executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(LoomBenchmarks.PINNING_PREFIX, 0).factory());
        long timeoutMillis = roundedRounds(tasks, parallelism) * blockMillis + TIMEOUT_MARGIN_MILLIS;

        List<Future<?>> futures = new ArrayList<>(tasks);
        long start = System.nanoTime();
        try {
            for (int index = 0; index < tasks; index++) {
                final int taskNumber = index;
                futures.add(executor.submit(() -> {
                    meter.enter(Thread.currentThread());
                    try {
                        if (useMonitor) {
                            synchronized (monitors[taskNumber]) {
                                recordBeforeSleep(carriers);
                                ThreadProbe before = ThreadProbe.of(Thread.currentThread());
                                sleep(blockMillis);
                                ThreadProbe after = ThreadProbe.of(Thread.currentThread());
                                if (!before.carrier().equals(after.carrier())) {
                                    switches.incrementAndGet();
                                }
                            }
                        } else {
                            ReentrantLock lock = locks[taskNumber];
                            lock.lock();
                            try {
                                recordBeforeSleep(carriers);
                                ThreadProbe before = ThreadProbe.of(Thread.currentThread());
                                sleep(blockMillis);
                                ThreadProbe after = ThreadProbe.of(Thread.currentThread());
                                if (!before.carrier().equals(after.carrier())) {
                                    switches.incrementAndGet();
                                }
                            } finally {
                                lock.unlock();
                            }
                        }
                        completed.incrementAndGet();
                    } finally {
                        meter.exit();
                    }
                }));
            }

            long deadline = start + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            for (Future<?> future : futures) {
                long remaining = deadline - System.nanoTime();
                try {
                    if (remaining <= 0L) {
                        future.cancel(true);
                    } else {
                        future.get(remaining, TimeUnit.NANOSECONDS);
                    }
                } catch (TimeoutException e) {
                    future.cancel(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.cancel(true);
                } catch (ExecutionException | java.util.concurrent.CancellationException e) {
                    // Сбой одной задачи не должен скрывать картину остальных.
                }
            }
            long wallTimeNanos = System.nanoTime() - start;

            return new PinningRun(
                    lockKind + (useMonitor ? " (монитор JVM)" : " (очередь JDK)"),
                    lockKind,
                    tasks,
                    blockMillis,
                    wallTimeNanos,
                    meter.peak(),
                    carriers.size(),
                    carriers,
                    switches.get(),
                    completed.get(),
                    parallelism);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Carrier определяется только у смонтированного потока; внутри блока он смонтирован. */
    private static void recordBeforeSleep(Set<String> carriers) {
        ThreadProbe probe = ThreadProbe.of(Thread.currentThread());
        if (!probe.carrier().isBlank()) {
            carriers.add(probe.carrier());
        }
    }

    private static int roundedRounds(int tasks, int parallelism) {
        if (parallelism <= 0 || tasks == 0) {
            return 0;
        }
        return (tasks + parallelism - 1) / parallelism;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CancellationException("задача прервана");
        }
    }
}
