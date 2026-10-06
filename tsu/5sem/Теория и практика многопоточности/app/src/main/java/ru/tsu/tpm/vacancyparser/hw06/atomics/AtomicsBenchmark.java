package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.ArrayList;
import java.util.List;
import ru.tsu.tpm.vacancyparser.common.Timings;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.Counter;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;

/**
 * Замеры ДЗ 6: инвариант счётчика, гонка за кэш и сравнение трёх механизмов на одинаковой нагрузке.
 *
 * <p>Всё измеряется одним способом — {@link Timings} из {@code common} на {@code System.nanoTime()}.
 * Сравнение трёх вариантов счётчика (монитор из ДЗ 4, учебный {@code volatile}-дефект, атомарный)
 * идёт при одинаковых числе потоков и числе операций: иначе разница объяснялась бы объёмом работы.
 * Ничего не печатает — печать собирается в {@link AtomicsReport}, чтобы вывод проверялся без нагрузки.
 */
public final class AtomicsBenchmark {

    /** Число потоков нагрузки по умолчанию. */
    public static final int DEFAULT_THREADS = 8;

    /** Операций на поток в замере счётчика. */
    public static final int DEFAULT_OPERATIONS = 100_000;

    /** Сколько раз повторять каждый замер, чтобы в отчёте был разброс, а не одно число. */
    public static final int DEFAULT_RUNS = 5;

    private AtomicsBenchmark() {
    }

    /**
     * Один замер одного варианта счётчика.
     *
     * @param variant  как вариант называется в выводе
     * @param nanos    время прогона
     * @param value    итоговое значение
     * @param expected ожидаемое значение
     */
    public record CounterRun(String variant, long nanos, long value, long expected) {

        /** Сколько обновлений потеряно. */
        public long lost() {
            return expected - value;
        }

        /** Совпало ли значение с ожидаемым. */
        public boolean exact() {
            return value == expected;
        }
    }

    /**
     * Сравнение трёх вариантов счётчика на одинаковой нагрузке.
     *
     * @param runs замеры по порядку выполнения: по N прогонов на каждый вариант
     */
    public record CounterComparison(int threads, int operationsPerThread, long expected, List<CounterRun> runs) {

        public CounterComparison {
            runs = List.copyOf(runs);
        }

        /** Времена всех прогонов указанного варианта. */
        public List<Long> nanosFor(String variant) {
            return runs.stream().filter(run -> run.variant().equals(variant)).map(CounterRun::nanos).toList();
        }

        /** Замеры указанного варианта. */
        public List<CounterRun> runsFor(String variant) {
            return runs.stream().filter(run -> run.variant().equals(variant)).toList();
        }

        /** Точен ли указанный вариант во всех прогонах. */
        public boolean allExact(String variant) {
            return runsFor(variant).stream().allMatch(CounterRun::exact);
        }

        /** Точны ли оба корректных варианта (монитор и атомарный). */
        public boolean correctVariantsExact() {
            return allExact(CounterVariant.MONITOR.label()) && allExact(CounterVariant.ATOMIC.label());
        }

        /** Наблюдалась ли потеря обновлений у учебного дефектного варианта. */
        public boolean brokenVariantLost() {
            return runsFor(CounterVariant.VOLATILE_BROKEN.label()).stream().anyMatch(run -> !run.exact());
        }
    }

    /**
     * Итог нагрузочного прогона сразу по трём механизмам: остановка по признаку, счётчик, кэш.
     *
     * @param liveThreads живые потоки демонстрации к моменту проверки (ожидается пустой список)
     */
    public record MechanismsOutcome(
            int threads,
            int operationsPerThread,
            boolean stopStoppedInTime,
            long stopWaitNanos,
            long stopIterations,
            long atomicValue,
            long atomicExpected,
            int cacheCreations,
            int cacheDistinctInstances,
            List<String> liveThreads,
            long elapsedNanos) {

        public MechanismsOutcome {
            liveThreads = List.copyOf(liveThreads);
        }

        /** Точен ли счётчик. */
        public boolean counterExact() {
            return atomicValue == atomicExpected;
        }

        /** Создан ли кэш ровно один раз. */
        public boolean cacheCreatedOnce() {
            return cacheCreations == 1 && cacheDistinctInstances == 1;
        }
    }

    /** Прогнать нагрузку на счётчике: {@code threads} потоков по {@code operationsPerThread} увеличений. */
    public static void runCounterLoad(Counter counter, int threads, int operationsPerThread) throws InterruptedException {
        CounterLoadRunner.runThreads(threads, index -> {
            for (int i = 0; i < operationsPerThread; i++) {
                counter.increment();
            }
        });
    }

    /** Проверить инвариант счётчика: N потоков × M увеличений дают ровно N × M. */
    public static long runCounterInvariant(Counter counter, int threads, int operationsPerThread)
            throws InterruptedException {
        runCounterLoad(counter, threads, operationsPerThread);
        return counter.value();
    }

    /** Сравнить три варианта счётчика на одинаковой нагрузке. */
    public static CounterComparison compareCounters(int threads, int operationsPerThread, int runs)
            throws InterruptedException {
        long expected = (long) threads * operationsPerThread;
        List<CounterRun> results = new ArrayList<>(runs * CounterVariant.values().length);

        for (CounterVariant variant : CounterVariant.values()) {
            for (int run = 0; run < runs; run++) {
                Counter counter = variant.newCounter();
                long nanos = Timings.measureNanos(() -> {
                    try {
                        runCounterLoad(counter, threads, operationsPerThread);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("замер счётчика прерван", e);
                    }
                });
                results.add(new CounterRun(variant.label(), nanos, counter.value(), expected));
            }
        }

        return new CounterComparison(threads, operationsPerThread, expected, results);
    }

    /** Нагрузочный прогон сразу по трём механизмам ДЗ 6. */
    public static MechanismsOutcome runMechanismsLoad(int threads, int operationsPerThread)
            throws InterruptedException {
        long start = System.nanoTime();

        FlagStopScenario.StopOutcome stop = FlagStopScenario.runStoppable(FlagStopScenario.DEFAULT_TIMEOUT_MILLIS);

        AtomicCounter counter = new AtomicCounter();
        runCounterLoad(counter, threads, operationsPerThread);

        SingletonCache<Object> cache = new SingletonCache<>();
        CacheRaceRunner.RaceOutcome race = CacheRaceRunner.run(cache, threads);

        long elapsedNanos = System.nanoTime() - start;
        List<String> live = ThreadDump.liveThreadsWithPrefixes("hw06-");

        return new MechanismsOutcome(
                threads,
                operationsPerThread,
                stop.stoppedInTime(),
                stop.waitNanos(),
                stop.iterations(),
                counter.value(),
                (long) threads * operationsPerThread,
                race.creations(),
                race.distinctInstances(),
                live,
                elapsedNanos);
    }
}
