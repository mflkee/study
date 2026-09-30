package ru.tsu.tpm.vacancyparser.hw03.parallel;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * Наблюдение за тем, сколько потоков общего пула <b>фактически</b> работало при параллельном
 * исполнении.
 *
 * <p>Параллелизм {@code ForkJoinPool.commonPool()} — это намерение реализации, а не факт. На
 * реальной машине потоков в работе может оказаться меньше: часть занята другими задачами процесса,
 * работа закончилась раньше, чем все рабочие получили свой кусок, пул ещё не создал всех потоков.
 * Написать в отчёте «использовано 11 потоков» на основании формулы, когда в выводе видно 8, — это
 * ошибка. Поэтому число потоков наблюдается: во время параллельного прохода снимается снимок
 * потоков, и считаются те, что названы {@code ForkJoinPool.commonPool-worker-*} и находятся в
 * состоянии {@link Thread.State#RUNNABLE}.
 *
 * <p><b>Наблюдение идёт отдельным проходом, а не внутри замера.</b> Сам снимок потоков стоит
 * дорого, и внутри измеряемого участка он испортил бы цифры. Проход для наблюдения выполняется
 * столько раз, сколько нужно, чтобы пул успел развернуть рабочих; времена этого прохода никуда не
 * сохраняются.
 */
public final class ParallelismProbe {

    /** Сколько раз повторить проход, чтобы у рабочих потоков был шанс получить свой кусок. */
    private static final int DEFAULT_PASSES = 10;

    /** Пауза между снимками: без неё снимок потоков (дорогой) вытеснит саму работу. */
    private static final long SAMPLE_PAUSE_NANOS = 200_000L;

    private static final String WORKER_PREFIX = "ForkJoinPool.commonPool-worker-";

    private ParallelismProbe() {
    }

    /**
     * Наблюдаемый итог.
     *
     * @param observedThreads   сколько потоков общего пула реально застали за работой
     * @param workerNames       их имена — для тестов и разбора; в условия замера попадает только число
     * @param poolParallelism   параллелизм общего пула, как его заявляет сама реализация
     * @param poolSize          сколько рабочих потоков пул создал к моменту окончания наблюдения
     */
    public record Observation(
            int observedThreads, Set<String> workerNames, int poolParallelism, int poolSize) {

        public Observation {
            workerNames = Set.copyOf(workerNames);
        }
    }

    /** Наблюдать параллельный проход, выполнив его {@link #DEFAULT_PASSES} раз. */
    public static Observation observe(Supplier<?> parallelPass) {
        return observe(parallelPass, () -> null, DEFAULT_PASSES);
    }

    /** Наблюдать параллельный проход, прогревая обе ветви, за {@link #DEFAULT_PASSES} проходов. */
    public static Observation observe(Supplier<?> parallelPass, Supplier<?> companionPass) {
        return observe(parallelPass, companionPass, DEFAULT_PASSES);
    }

    /**
     * Наблюдать параллельный проход, выполняя на каждом шаге также парную последовательную операцию.
     *
     * <p>Парная операция нужна для честности сравнения. JIT компилирует каждую ветвь отдельно по её
     * счётчику вызовов, поэтому проходы для наблюдения, выполненные только по параллельной ветви,
     * прогрели бы её сильнее, чем последовательную, и последовательная выглядела бы медленнее не
     * из-за отсутствия параллелизма, а из-за недогретого кода. Обе ветви обязаны находиться в одной
     * стадии компиляции к началу измерений.
     *
     * @param parallelPass   параллельная операция — её и наблюдает датчик
     * @param companionPass  последовательная операция того же смысла; прогревается наравне
     * @param passes         сколько раз выполнить каждую из них
     */
    public static Observation observe(Supplier<?> parallelPass, Supplier<?> companionPass, int passes) {
        if (passes < 1) {
            throw new IllegalArgumentException("нужен хотя бы один проход для наблюдения");
        }

        Set<String> seen = ConcurrentHashMap.newKeySet();
        Thread sampler = new Thread(() -> sample(seen), "parallelism-probe-sampler");
        sampler.setDaemon(true);
        sampler.start();

        try {
            for (int i = 0; i < passes; i++) {
                companionPass.get();
                parallelPass.get();
            }
        } finally {
            sampler.interrupt();
            try {
                sampler.join(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        ForkJoinPool pool = ForkJoinPool.commonPool();
        return new Observation(seen.size(), new TreeSet<>(seen), pool.getParallelism(), pool.getPoolSize());
    }

    private static void sample(Set<String> seen) {
        while (!Thread.currentThread().isInterrupted()) {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.getName().startsWith(WORKER_PREFIX)
                        && thread.getState() == Thread.State.RUNNABLE) {
                    seen.add(thread.getName());
                }
            }
            LockSupport.parkNanos(SAMPLE_PAUSE_NANOS);
        }
    }
}
