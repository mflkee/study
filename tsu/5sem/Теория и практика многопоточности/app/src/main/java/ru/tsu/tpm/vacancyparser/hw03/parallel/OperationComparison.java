package ru.tsu.tpm.vacancyparser.hw03.parallel;

import java.time.Duration;
import java.util.List;
import ru.tsu.tpm.vacancyparser.common.TimingResult;

/**
 * Результаты замера одной операции: время обоих вариантов и признак совпадения результатов.
 *
 * <p>Формат представления времени здесь отвязан от типа результата операции: сравнивать нужно
 * миллисекунды, а сами значения операций (число, список, сумма) в таблицу не попадают. Благодаря
 * этому таблицу и анализ можно проверять на искусственных временах, не запуская миллион элементов.
 *
 * @param name         название операции так, как оно печатается
 * @param sequential   статистика последовательного варианта
 * @param parallel     статистика параллельного варианта
 * @param resultsMatch совпали ли результаты вариантов; расхождение — ошибка, а не деталь
 */
public record OperationComparison(
        String name, TimingStats sequential, TimingStats parallel, boolean resultsMatch) {

    /**
     * Статистика одного варианта исполнения.
     *
     * @param cold    «холодный» первый прогон (в среднее не входит, печатается отдельно)
     * @param average среднее по повторам
     * @param min     минимум по повторам
     * @param max     максимум по повторам
     * @param repeats число учтённых повторов
     */
    public record TimingStats(Duration cold, Duration average, Duration min, Duration max, int repeats) {

        public TimingStats {
            if (repeats < 1) {
                throw new IllegalArgumentException("без повторов статистики нет");
            }
        }

        /** Снять статистику со замера. */
        public static TimingStats of(TimingResult<?> result) {
            return new TimingStats(
                    result.cold(), result.average(), result.min(), result.max(), result.repeatCount());
        }

        /** Разброс повторов: {@code max - min}. */
        public Duration spread() {
            return max.minus(min);
        }

        /**
         * Пересекаются ли интервалы {@code [min, max]} двух вариантов.
         *
         * <p>Если да — разница между средними укладывается в разброс, и объявлять победителя
         * нечестно.
         */
        public boolean overlaps(TimingStats other) {
            return min.compareTo(other.max) <= 0 && other.min.compareTo(max) <= 0;
        }
    }

    /** Отношение времён {@code sequential / parallel}: больше единицы — быстрее параллельный. */
    public double parallelToSequentialRatio() {
        double parallelNanos = parallel.average().toNanos();
        return parallelNanos == 0.0
                ? Double.POSITIVE_INFINITY
                : sequential.average().toNanos() / parallelNanos;
    }

    /** Разница между вариантами укладывается в разброс повторов. */
    public boolean differenceWithinNoise() {
        return sequential.overlaps(parallel);
    }

    /**
     * Длительности повторов обоих вариантов — для печати «среднее, минимум/максимум».
     *
     * <p>Возвращаются как доли миллисекунды, потому что именно в них считает таблица.
     */
    public static double millis(Duration duration) {
        return duration.toNanos() / 1_000_000.0;
    }

    /** Список времён повторов в миллисекундах — для отдельной строки о разбросе. */
    public static List<Double> millis(List<Duration> durations) {
        return durations.stream().map(OperationComparison::millis).toList();
    }
}
