package ru.tsu.tpm.vacancyparser.hw03.parallel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки условий замера: они берутся у JVM и у самого пула, а не подставляются формулой.
 */
class MeasurementConditionsTest {

    private static final BenchmarkConfig CONFIG = BenchmarkConfig.ofSize(200_000);

    @Test
    @DisplayName("В условиях замера присутствуют все пять обязательных значений")
    void conditionsContainAllRequiredValues() {
        MeasurementConditions conditions = MeasurementConditions.collect(
                CONFIG, probeObservation());

        assertThat(conditions.jvmVersion()).isNotBlank();
        assertThat(conditions.availableProcessors()).isPositive();
        assertThat(conditions.commonPoolParallelism()).isPositive();
        assertThat(conditions.listSize()).isEqualTo(CONFIG.size());
        assertThat(conditions.range()).isEqualTo(CONFIG.rangeDescription());
        assertThat(conditions.repeats()).isEqualTo(CONFIG.repeats());
        assertThat(conditions.observedThreads()).isNotNegative();
    }

    @Test
    @DisplayName("Версия JVM и число процессоров совпадают с фактическими")
    void jvmFactsMatchRuntime() {
        MeasurementConditions conditions = MeasurementConditions.collect(CONFIG, probeObservation());

        assertThat(conditions.jvmVersion())
                .as("версия берётся у JVM, а не пишется в коде")
                .isEqualTo(System.getProperty("java.version"));
        assertThat(conditions.availableProcessors())
                .isEqualTo(Runtime.getRuntime().availableProcessors());
    }

    @Test
    @DisplayName("Параллелизм общего пула равен фактическому и равен «процессоров - 1»")
    void commonPoolParallelismMatchesProcessors() {
        MeasurementConditions conditions = MeasurementConditions.collect(CONFIG, probeObservation());

        assertThat(conditions.commonPoolParallelism())
                .as("заявленный параллелизм пула берётся у самого пула")
                .isEqualTo(ForkJoinPool.commonPool().getParallelism())
                .isEqualTo(MeasurementConditions.expectedCommonPoolParallelism(
                        Runtime.getRuntime().availableProcessors()));
        assertThat(conditions.commonPoolMatchesProcessors()).isTrue();
    }

    @Test
    @DisplayName("На одном процессоре параллелизм общего пула равен единице, а не нулю")
    void singleProcessorGivesParallelismOfOne() {
        assertThat(MeasurementConditions.expectedCommonPoolParallelism(1)).isEqualTo(1);
        assertThat(MeasurementConditions.expectedCommonPoolParallelism(2)).isEqualTo(1);
        assertThat(MeasurementConditions.expectedCommonPoolParallelism(12)).isEqualTo(11);
    }

    @Test
    @DisplayName("Наблюдаемое число потоков не превышает параллелизм пула и берётся из наблюдения")
    void observedThreadsComeFromObservation() {
        BenchmarkConfig config = BenchmarkConfig.ofSize(1_000_000);
        List<Integer> data = RandomListGenerator.generate(config.size(), new Random(5L));

        ParallelismProbe.Observation observation =
                ParallelismProbe.observe(
                        () -> StreamOperations.filterEvenParallel(data),
                        () -> StreamOperations.filterEvenSequential(data),
                        10);
        MeasurementConditions conditions = MeasurementConditions.collect(config, observation);

        assertThat(conditions.observedThreads())
                .as("в условия попадает именно наблюдённое значение, а не формула")
                .isEqualTo(observation.observedThreads());
        assertThat(conditions.observedThreadsWithinParallelism())
                .as("рабочих потоков не может быть больше параллелизма пула")
                .isTrue();
        assertThat(observation.workerNames())
                .as("наблюдение опирается на настоящие рабочие потоки общего пула")
                .allSatisfy(name -> assertThat(name).startsWith("ForkJoinPool.commonPool-worker-"));
        assertThat(observation.poolSize()).isNotNegative();
    }

    @Test
    @DisplayName("Параллельный проход действительно разворачивает рабочие потоки пула")
    void parallelPassActuallyUsesPoolWorkers() {
        BenchmarkConfig config = BenchmarkConfig.ofSize(1_000_000);
        List<Integer> data = RandomListGenerator.generate(config.size(), new Random(9L));

        ParallelismProbe.Observation observation = ParallelismProbe.observe(
                () -> StreamOperations.sumParallel(data),
                () -> StreamOperations.sumSequential(data),
                10);

        assertThat(observation.observedThreads())
                .as("на миллионе элементов параллельный поток обязан задействовать хотя бы один "
                        + "рабочий поток общего пула")
                .isPositive();
    }

    @Test
    @DisplayName("Снимающий снимки поток не переживает наблюдение и не попадает в замеры")
    void samplerDoesNotSurviveObservation() {
        List<Integer> data = RandomListGenerator.generate(200_000, new Random(13L));

        ParallelismProbe.observe(
                () -> StreamOperations.filterEvenParallel(data),
                () -> StreamOperations.filterEvenSequential(data),
                3);

        List<String> leftoverSamplers = Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter(name -> name.startsWith("parallelism-probe-sampler"))
                .toList();

        assertThat(leftoverSamplers)
                .as("наблюдение завершается вместе со своим потоком, иначе снимок потоков "
                        + "продолжал бы искажать замеры")
                .isEmpty();
    }

    /** Наблюдение без дорогого прохода: нужны только значения, а не факт работы пула. */
    private static ParallelismProbe.Observation probeObservation() {
        return ParallelismProbe.observe(() -> 0, () -> 0, 1);
    }
}
