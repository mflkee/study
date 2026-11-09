package ru.tsu.tpm.vacancyparser.hw14.loom.swarm;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.ConcurrencyMeter;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.MemoryProbe;

/**
 * Сценарий из текста задания: «10 000 задач, каждая печатает имя своего потока и имитирует
 * неблокирующую I/O операцию», — отдельно на виртуальных и отдельно на платформенных потоках.
 *
 * <p>Задача намеренно тривиальная: записать {@code Thread.currentThread().getName()},
 * проспать {@code sleepMillis} миллисекунд и сообщить о завершении. Никакого общего состояния, ни
 * блокировок, ни пулов — только «поток на задачу». Поэтому разница между двумя прогонами объясняется
 * исключительно ценой самого потока, а не алгоритмом.
 *
 * <p><b>Почему в этом опыте нет {@code ExecutorService}.</b> В тексте задания потоки создаются
 * напрямую: {@code Thread.ofVirtual().start(...)} и {@code new Thread(...).start()}. Пулы уже
 * проверены на этапах 1–4, а здесь важно показать именно цену «сырого» потока ОС.
 *
 * <p><b>Десять тысяч платформенных потоков.</b> Это законно, но дорого: у каждого потока свой стек
 * (по умолчанию 1 МиБ виртуальной памяти), запись в планировщике и структуры ядра. На машине
 * разработки 10 000 потоков запускаются за ~0.9 с и требуют ~10 ГиБ адресного пространства — то есть
 * работают, но впустую. Если на машине мало памяти или маленький лимит потоков
 * ({@code ulimit -u}), запуск можно разбить на пачки параметром
 * {@code app.hw14.swarm-max-platform-threads}; тогда прогон идёт порциями и это честно помечается в
 * отчёте. Если ядро всё равно отказывает в потоке, прогон автоматически повторяется более мелкими
 * пачками — демонстрация не должна падать из-за настроек системы.
 */
public final class ThreadSwarm {

    /** Префикс имён виртуальных потоков. */
    public static final String VIRTUAL_PREFIX = "hw14-swarm-vt-";

    /** Префикс имён платформенных потоков. */
    public static final String PLATFORM_PREFIX = "hw14-swarm-pt-";

    private static final ThreadMXBean THREAD_BEAN = ManagementFactory.getThreadMXBean();

    /** Процессорное время процесса: по нему видно, сколько CPU съедает сам запуск потоков. */
    private static final java.lang.management.OperatingSystemMXBean OS_BEAN =
            ManagementFactory.getOperatingSystemMXBean();

    private ThreadSwarm() {
    }

    /**
     * Запустить задачи через {@link Thread#ofVirtual()} и дождаться всех.
     *
     * @param tasks       сколько задач создать
     * @param sleepMillis сколько «ждать» внутри задачи (0 — вместо сна {@link Thread#yield()})
     */
    public static SwarmRun runVirtual(int tasks, long sleepMillis) {
        return runVirtual(tasks, sleepMillis, null);
    }

    /**
     * То же, но каждое имя потока дополнительно отдаётся в {@code echo} — это и есть «каждая задача
     * печатает имя своего потока»; {@code null} означает «только собрать имена».
     */
    public static SwarmRun runVirtual(int tasks, long sleepMillis, Consumer<String> echo) {
        validate(tasks, sleepMillis);

        String[] names = new String[tasks];
        Arrays.fill(names, "");
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger aborted = new AtomicInteger();
        AtomicInteger virtualExecutions = new AtomicInteger();
        AtomicInteger platformExecutions = new AtomicInteger();
        ConcurrencyMeter meter = new ConcurrencyMeter();

        Thread.Builder.OfVirtual builder = Thread.ofVirtual().name(VIRTUAL_PREFIX, 0);
        long heapBefore = MemoryProbe.usedHeapBytes();
        int platformPeakBefore = THREAD_BEAN.getPeakThreadCount();
        long cpuBefore = processCpuNanos();

        List<Thread> threads = new ArrayList<>(tasks);
        long startOfStart = System.nanoTime();
        for (int index = 0; index < tasks; index++) {
            int taskNumber = index;
            threads.add(builder.start(() -> task(taskNumber, names, sleepMillis, completed, aborted,
                    virtualExecutions, platformExecutions, meter, echo)));
        }
        long startNanos = System.nanoTime() - startOfStart;
        waitForAll(threads);
        long totalNanos = System.nanoTime() - startOfStart;

        return new SwarmRun(
                "Thread.ofVirtual().start",
                tasks,
                completed.get(),
                aborted.get(),
                tasks == 0 ? 0 : 1,
                Math.max(0, THREAD_BEAN.getPeakThreadCount() - platformPeakBefore),
                meter.peak(),
                startNanos,
                totalNanos,
                Arrays.asList(names),
                virtualExecutions.get(),
                platformExecutions.get(),
                MemoryProbe.deltaBytes(heapBefore),
                Math.max(0L, processCpuNanos() - cpuBefore));
    }

    /** Запустить задачи через {@code new Thread(...).start()}, все сразу. */
    public static SwarmRun runPlatform(int tasks, long sleepMillis) {
        return runPlatform(tasks, sleepMillis, 0, null);
    }

    /** Запустить задачи через {@code new Thread(...).start()} с явным отдаванием имён в {@code echo}. */
    public static SwarmRun runPlatform(int tasks, long sleepMillis, Consumer<String> echo) {
        return runPlatform(tasks, sleepMillis, 0, echo);
    }

    /**
     * Запустить задачи через {@code new Thread(...).start()}.
     *
     * @param tasks          сколько задач создать
     * @param sleepMillis    сколько «ждать» внутри задачи
     * @param maxConcurrent  сколько потоков держать одновременно; {@code 0} — все сразу; меньше
     *                       {@code tasks} — запуск пачками (каждая пачка стартует после завершения
     *                       предыдущей)
     * @param echo           куда печатать имена потоков; {@code null} — только собрать
     */
    public static SwarmRun runPlatform(
            int tasks, long sleepMillis, int maxConcurrent, Consumer<String> echo) {
        validate(tasks, sleepMillis);
        if (maxConcurrent < 0) {
            throw new IllegalArgumentException("предел одновременных потоков не может быть отрицательным");
        }

        int batch = maxConcurrent <= 0 ? Math.max(tasks, 1) : Math.min(maxConcurrent, Math.max(tasks, 1));
        try {
            return platformRun("new Thread(...).start", tasks, sleepMillis, batch, echo);
        } catch (OutOfMemoryError e) {
            // Ядро отказало в потоке (низкий ulimit -u или мало памяти): повторяем мелкими пачками,
            // чтобы демонстрация показала числа, а не упала.
            if (batch <= 64) {
                throw e;
            }
            int retryBatch = Math.max(64, batch / 8);
            return platformRun(
                    "new Thread(...).start (пачками по " + retryBatch + " — не хватило потоков ОС)",
                    tasks, sleepMillis, retryBatch, echo);
        }
    }

    private static SwarmRun platformRun(
            String label, int tasks, long sleepMillis, int batch, Consumer<String> echo) {
        String[] names = new String[tasks];
        Arrays.fill(names, "");
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger aborted = new AtomicInteger();
        AtomicInteger virtualExecutions = new AtomicInteger();
        AtomicInteger platformExecutions = new AtomicInteger();
        ConcurrencyMeter meter = new ConcurrencyMeter();

        long heapBefore = MemoryProbe.usedHeapBytes();
        int platformPeakBefore = THREAD_BEAN.getPeakThreadCount();
        long cpuBefore = processCpuNanos();

        long startOfAll = System.nanoTime();
        long startupNanos = 0L;
        int batches = 0;
        for (int from = 0; from < tasks; from += batch) {
            int to = Math.min(tasks, from + batch);
            List<Thread> threads = new ArrayList<>(to - from);
            long startOfBatch = System.nanoTime();
            for (int index = from; index < to; index++) {
                int taskNumber = index;
                Thread thread = new Thread(
                        () -> task(taskNumber, names, sleepMillis, completed, aborted,
                                virtualExecutions, platformExecutions, meter, echo),
                        PLATFORM_PREFIX + (index + 1));
                threads.add(thread);
                thread.start();
            }
            startupNanos += System.nanoTime() - startOfBatch;
            waitForAll(threads);
            batches++;
        }
        long totalNanos = System.nanoTime() - startOfAll;

        return new SwarmRun(
                label,
                tasks,
                completed.get(),
                aborted.get(),
                batches,
                Math.max(0, THREAD_BEAN.getPeakThreadCount() - platformPeakBefore),
                meter.peak(),
                startupNanos,
                totalNanos,
                Arrays.asList(names),
                virtualExecutions.get(),
                platformExecutions.get(),
                MemoryProbe.deltaBytes(heapBefore),
                Math.max(0L, processCpuNanos() - cpuBefore));
    }

    /** Процессорное время процесса в наносекундах; {@code -1}, если платформа его не отдаёт. */
    private static long processCpuNanos() {
        if (OS_BEAN instanceof com.sun.management.OperatingSystemMXBean extended) {
            return extended.getProcessCpuTime();
        }
        return -1L;
    }

    private static void task(
            int index,
            String[] names,
            long sleepMillis,
            AtomicInteger completed,
            AtomicInteger aborted,
            AtomicInteger virtualExecutions,
            AtomicInteger platformExecutions,
            ConcurrencyMeter meter,
            Consumer<String> echo) {
        Thread current = Thread.currentThread();
        meter.enter(current);
        names[index] = current.getName();
        if (current.isVirtual()) {
            virtualExecutions.incrementAndGet();
        } else {
            platformExecutions.incrementAndGet();
        }
        if (echo != null) {
            echo.accept(current.getName());
        }
        try {
            if (sleepMillis > 0L) {
                Thread.sleep(sleepMillis);
            } else {
                Thread.yield();
            }
            completed.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            aborted.incrementAndGet();
        } finally {
            meter.exit();
        }
    }

    private static void waitForAll(List<Thread> threads) {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void validate(int tasks, long sleepMillis) {
        if (tasks < 0) {
            throw new IllegalArgumentException("число задач не может быть отрицательным");
        }
        if (sleepMillis < 0L) {
            throw new IllegalArgumentException("задержка не может быть отрицательной");
        }
    }
}
