package ru.tsu.tpm.vacancyparser.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Измеритель времени с прогревом и повторами.
 *
 * <p>Замер — это разница двух значений монотонного счётчика вокруг участка кода. Счётчик по
 * умолчанию — {@link System#nanoTime()}: он монотонен и имеет наносекундное разрешение, тогда как
 * системные часы могут быть переведены на длительности замера и дать отрицательное или завышенное
 * значение.
 *
 * <p><b>Зачем прогрев.</b> HotSpot компилирует метод по счётчику вызовов: первый прогон
 * исполняется интерпретатором, затем C1, затем C2. Разница между стадиями легко перекрывает
 * эффект, ради которого замер и делается. Поэтому результат основного прогона не учитывается, а
 * статистика строится по повторам, когда код уже прогрет.
 *
 * <p><b>Зачем повторы.</b> Один прогретый замер всё ещё ловит случайный шум планировщика. Среднее
 * по нескольким повторам его гасит, а сохранённые минимум и максимум показывают, насколько
 * доверять разнице.
 *
 * <p>Счётчик времени вынесен в параметр: тесты подставляют детерминированный и получают точные,
 * не зависящие от машины длительности.
 */
public final class Measurer {

    /** Прогрев по умолчанию: одного прогона достаточно, чтобы уйти со стадии интерпретатора. */
    public static final int DEFAULT_WARMUP_RUNS = 1;

    /** Повторы по умолчанию: меньше — заметен шум, больше — демонстрация заметно удлиняется. */
    public static final int DEFAULT_REPEAT_RUNS = 5;

    private Measurer() {
    }

    /** Замерить операцию с прогревом и повторами по умолчанию. */
    public static <T> TimingResult<T> measure(Supplier<T> operation) {
        return measure(DEFAULT_WARMUP_RUNS, DEFAULT_REPEAT_RUNS, operation);
    }

    /** Замерить операцию с заданным числом прогревочных прогонов и повторов. */
    public static <T> TimingResult<T> measure(int warmupRuns, int repeatRuns, Supplier<T> operation) {
        return measure(warmupRuns, repeatRuns, operation, System::nanoTime);
    }

    /**
     * Замерить операцию, используя переданный счётчик времени.
     *
     * @param warmupRuns число прогревочных прогонов; их времена не попадают в статистику
     * @param repeatRuns число учитываемых повторов; по ним считаются среднее, минимум и максимум
     * @param operation  измеряемая операция
     * @param nanos      монотонный счётчик времени
     * @throws IllegalArgumentException если прогрев или повторы отрицательны либо повторов нет
     */
    public static <T> TimingResult<T> measure(
            int warmupRuns, int repeatRuns, Supplier<T> operation, LongSupplier nanos) {
        if (warmupRuns < 0) {
            throw new IllegalArgumentException("число прогревочных прогонов не может быть отрицательным");
        }
        if (repeatRuns < 1) {
            throw new IllegalArgumentException("нужен хотя бы один повтор, иначе статистики нет");
        }

        // Холодный прогон — всегда самый первый; он же и прогревочный, и в статистику не входит.
        Duration cold = runOnce(operation, nanos);

        int skippedWarmups = Math.max(0, warmupRuns - 1);
        for (int i = 0; i < skippedWarmups; i++) {
            runOnce(operation, nanos);
        }

        List<Duration> repeats = new ArrayList<>(repeatRuns);
        T lastResult = null;
        Duration min = null;
        Duration max = null;
        long totalNanos = 0L;

        for (int i = 0; i < repeatRuns; i++) {
            long start = nanos.getAsLong();
            lastResult = operation.get();
            Duration duration = Duration.ofNanos(nanos.getAsLong() - start);

            repeats.add(duration);
            totalNanos += duration.toNanos();
            min = (min == null || duration.compareTo(min) < 0) ? duration : min;
            max = (max == null || duration.compareTo(max) > 0) ? duration : max;
        }

        return new TimingResult<>(
                lastResult,
                cold,
                Duration.ofNanos(totalNanos / repeatRuns),
                min,
                max,
                warmupRuns,
                repeats);
    }

    private static <T> Duration runOnce(Supplier<T> operation, LongSupplier nanos) {
        long start = nanos.getAsLong();
        operation.get();
        return Duration.ofNanos(nanos.getAsLong() - start);
    }
}
