package ru.tsu.tpm.vacancyparser.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки измерителя.
 *
 * <p>Счётчик времени подставляется детерминированный: длительности получаются точными и не
 * зависят от загрузки машины, поэтому проверки не «мигают».
 */
class MeasurerTest {

    /**
     * Счётчик, который на {@code i}-м обращении возвращает {@code i * i} миллисекунд.
     *
     * <p>Квадрат нужен, чтобы длительности прогонов различались: прогон {@code i} занимает
     * {@code (2i + 1)} мс. Тогда «взято ли среднее только по повторам» проверяется точно.
     */
    private static LongSupplier quadraticClock(AtomicLong calls) {
        return () -> {
            long i = calls.getAndIncrement();
            return i * i * 1_000_000L;
        };
    }

    /**
     * Длительность прогона номер {@code k} при квадратичном счётчике: {@code (4k + 1)} мс.
     *
     * <p>Прогон занимает два обращения к счётчику — до и после операции, — а {@code i}-е обращение
     * возвращает {@code i * i} мс. Для прогона {@code k} это обращения {@code 2k} и {@code 2k + 1},
     * то есть {@code (2k + 1)² - (2k)² = 4k + 1} мс. Длительности прогонов растут, поэтому по
     * статистике видно, какие именно прогоны в неё попали.
     */
    private static Duration expectedRun(int k) {
        return Duration.ofMillis(4L * k + 1L);
    }

    @Test
    @DisplayName("Замер возвращает неотрицательную длительность, и реальная операция длится дольше пустой")
    void measuresNonNegativeAndDistinguishesRealWork() {
        Duration empty = Timings.measure(() -> { });
        Duration real = Timings.measure(() -> {
            long acc = 1L;
            for (int i = 1; i <= 200_000; i++) {
                acc = acc * 31L + i;
            }
            if (acc == 0L) {
                throw new AssertionError("недостижимо");
            }
        });

        assertThat(empty).isGreaterThanOrEqualTo(Duration.ZERO);
        assertThat(real).isGreaterThanOrEqualTo(Duration.ZERO);
        assertThat(real)
                .as("замер реальной операции обязан быть заметно больше замера пустого участка")
                .isGreaterThan(empty);
    }

    @Test
    @DisplayName("Прогрев не входит в статистику: прогонов N+1, среднее считается по N")
    void warmupIsExcludedFromStatistics() {
        AtomicLong calls = new AtomicLong();
        AtomicInteger runs = new AtomicInteger();

        TimingResult<Integer> result = Measurer.measure(
                Measurer.DEFAULT_WARMUP_RUNS, 5, runs::incrementAndGet, quadraticClock(calls));

        assertThat(runs.get())
                .as("один прогрев плюс пять повторов")
                .isEqualTo(6);
        assertThat(result.warmupRuns()).isEqualTo(1);
        assertThat(result.repeatCount()).isEqualTo(5);
        assertThat(result.cold())
                .as("холодный прогон — это самый первый прогон")
                .isEqualTo(expectedRun(0));
        assertThat(result.repeats())
                .containsExactly(expectedRun(1), expectedRun(2), expectedRun(3), expectedRun(4), expectedRun(5));
        assertThat(result.average())
                .as("среднее по повторам 5, 9, 13, 17, 21 мс = 13 мс; "
                        + "если бы прогрев (1 мс) попал в расчёт, вышло бы 11.17 мс")
                .isEqualTo(Duration.ofMillis(13));
        assertThat(result.min()).isEqualTo(expectedRun(1));
        assertThat(result.max()).isEqualTo(expectedRun(5));
    }

    @Test
    @DisplayName("Холодный прогон возвращается отдельно и отличается от среднего по повторам")
    void coldRunIsSeparateFromAverage() {
        AtomicLong calls = new AtomicLong();

        TimingResult<Integer> result = Measurer.measure(
                Measurer.DEFAULT_WARMUP_RUNS, 3, () -> 1, quadraticClock(calls));

        assertThat(result.cold()).isEqualTo(expectedRun(0));
        assertThat(result.average())
                .as("среднее по повторам 5, 9, 13 мс = 9 мс, а холодный прогон — 1 мс")
                .isEqualTo(Duration.ofMillis(9));
        assertThat(result.repeats()).containsExactly(
                expectedRun(1), expectedRun(2), expectedRun(3));
        assertThat(result.repeats())
                .as("холодный прогон не должен появиться среди повторов")
                .doesNotContain(result.cold());
    }

    @Test
    @DisplayName("Больше прогревочных прогонов — больше прогонов всего, статистика та же по составу")
    void extraWarmupsAreCountedAsWarmupOnly() {
        AtomicLong calls = new AtomicLong();
        AtomicInteger runs = new AtomicInteger();

        TimingResult<Integer> result =
                Measurer.measure(3, 2, runs::incrementAndGet, quadraticClock(calls));

        assertThat(runs.get()).isEqualTo(5);
        assertThat(result.warmupRuns()).isEqualTo(3);
        assertThat(result.repeats())
                .as("повторы — последние два прогона, а не первые")
                .containsExactly(expectedRun(3), expectedRun(4));
    }

    @Test
    @DisplayName("Результат операции сохраняется: варианты можно сверить, JIT не выбросит вычисление")
    void resultIsKept() {
        TimingResult<String> result = Measurer.measure(1, 2, () -> "результат");

        assertThat(result.result()).isEqualTo("результат");
    }

    @Test
    @DisplayName("Среднее всегда лежит между минимумом и максимумом")
    void averageIsWithinSpread() {
        AtomicLong calls = new AtomicLong();

        TimingResult<Integer> result = Measurer.measure(1, 5, () -> 0, quadraticClock(calls));

        assertThat(result.average()).isBetween(result.min(), result.max());
        assertThat(result.min()).isLessThanOrEqualTo(result.max());
        assertThat(result.spread()).isEqualTo(result.max().minus(result.min()));
    }

    @Test
    @DisplayName("На «шумной» операции с реальным сном разброс есть, а среднее остаётся внутри интервала")
    void spreadIsReportedForNoisyOperation() {
        SnoringOperation operation = new SnoringOperation(2L);

        TimingResult<Integer> result = Measurer.measure(1, 4, operation);

        assertThat(result.average())
                .as("среднее лежит между минимумом и максимумом")
                .isBetween(result.min(), result.max());
        assertThat(result.spread())
                .as("разброс повторов измеряется и пригоден для печати отдельной строкой")
                .isGreaterThanOrEqualTo(Duration.ZERO);
        assertThat(result.average())
                .as("операция со сном 2 мс не может дать среднее меньше 2 мс")
                .isGreaterThanOrEqualTo(Duration.ofMillis(2));
    }

    @Test
    @DisplayName("Повторный замер той же операции даёт близкое среднее — замер воспроизводим")
    void repeatedMeasurementIsReproducible() {
        SnoringOperation operation = new SnoringOperation(2L);

        Duration first = Measurer.measure(1, 3, operation).average();
        Duration second = Measurer.measure(1, 3, operation).average();

        double relativeDifference = Math.abs(first.toNanos() - second.toNanos())
                / (double) Math.max(first.toNanos(), second.toNanos());

        assertThat(relativeDifference)
                .as("два независимых замера одной операции на одном списке должны лежать в десятках "
                        + "процентов друг от друга, иначе замер не воспроизводим")
                .isLessThan(0.5);
    }

    @Test
    @DisplayName("Пересечение интервалов определяется по min/max, а не по фактическому сну")
    void overlapDetectionWorks() {
        // Значения задаются явно: реальный сон даёт разброс от запуска к запуску, и проверка
        // «перекрываются или нет» стала бы случайной.
        TimingResult<Integer> fast = result(2, 3, 4);
        TimingResult<Integer> overlapping = result(3, 4, 5);
        TimingResult<Integer> disjoint = result(10, 11, 12);

        assertThat(fast.overlaps(overlapping))
                .as("интервалы [2,4] и [3,5] перекрываются — разница в пределах шума")
                .isTrue();
        assertThat(overlapping.overlaps(fast)).as("отношение симметрично").isTrue();
        assertThat(fast.overlaps(disjoint))
                .as("интервалы [2,4] и [10,12] не пересекаются — разница значима")
                .isFalse();
        assertThat(disjoint.overlaps(fast)).isFalse();
    }

    private static TimingResult<Integer> result(long minMillis, long averageMillis, long maxMillis) {
        return new TimingResult<>(
                0,
                Duration.ofMillis(minMillis),
                Duration.ofMillis(averageMillis),
                Duration.ofMillis(minMillis),
                Duration.ofMillis(maxMillis),
                1,
                List.of(Duration.ofMillis(minMillis), Duration.ofMillis(maxMillis)));
    }

    @Test
    @DisplayName("Неверные параметры замера отвергаются, а не дают молчаливую пустую статистику")
    void invalidParametersAreRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Measurer.measure(-1, 5, () -> 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Measurer.measure(1, 0, () -> 0));
    }

    /** Операция с реальным сном — источник настоящего разброса, без подстановки счётчика. */
    private static final class SnoringOperation implements Supplier<Integer> {

        private final long millis;

        private SnoringOperation(long millis) {
            this.millis = millis;
        }

        @Override
        public Integer get() {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("замер прерван", e);
            }
            return 0;
        }
    }
}
