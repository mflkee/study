package ru.tsu.tpm.vacancyparser.hw03.parallel;

import java.util.concurrent.ForkJoinPool;

/**
 * Условия, при которых получены цифры замера.
 *
 * <p>Вывод «parallelStream проиграл в 4 раза» без указания числа ядер выводом не является: на
 * 32-ядерной машине тот же код выиграет. Поэтому условия печатаются вместе с временами и попадают
 * в отчёт как часть результата, а не как служебная информация.
 *
 * @param jvmVersion          версия JVM, на которой шёл замер
 * @param availableProcessors сколько процессоров доступно JVM
 * @param commonPoolParallelism параллелизм {@code ForkJoinPool.commonPool()}, как его заявляет реализация
 * @param commonPoolSize      сколько рабочих потоков общий пул создал
 * @param observedThreads     сколько потоков общего пула реально застали за работой (наблюдение, не формула)
 * @param listSize            размер измеряемого списка
 * @param range               диапазон значений в виде {@code 0..верхняя-1}
 * @param warmupRuns          число прогревочных прогонов на каждый вариант
 * @param repeats             число учтённых повторов на каждый вариант
 */
public record MeasurementConditions(
        String jvmVersion,
        int availableProcessors,
        int commonPoolParallelism,
        int commonPoolSize,
        int observedThreads,
        int listSize,
        String range,
        int warmupRuns,
        int repeats) {

    /**
     * Параллелизм общего пула, который должен быть на машине с данным числом процессоров.
     *
     * <p>Один поток общего пула остаётся на «организацию», поэтому рабочих на единицу меньше числа
     * процессоров; на одном процессоре рабочих нет вовсе, и пул работает силами вызывающего потока
     * с {@code parallelism = 1}.
     */
    public static int expectedCommonPoolParallelism(int availableProcessors) {
        return Math.max(1, availableProcessors - 1);
    }

    /** Собрать условия: фактические значения берутся у JVM и у самого пула, а не задаются текстом. */
    public static MeasurementConditions collect(
            BenchmarkConfig config, ParallelismProbe.Observation observation) {
        return new MeasurementConditions(
                System.getProperty("java.version", "неизвестно"),
                Runtime.getRuntime().availableProcessors(),
                ForkJoinPool.commonPool().getParallelism(),
                observation.poolSize(),
                observation.observedThreads(),
                config.size(),
                config.rangeDescription(),
                config.warmupRuns(),
                config.repeats());
    }

    /** Совпадает ли заявленный параллелизм пула с ожидаемым по числу процессоров. */
    public boolean commonPoolMatchesProcessors() {
        return commonPoolParallelism == expectedCommonPoolParallelism(availableProcessors);
    }

    /** Не превышает ли наблюдённое число потоков параллелизм пула — больше рабочих быть не может. */
    public boolean observedThreadsWithinParallelism() {
        return observedThreads <= commonPoolParallelism;
    }
}
