package ru.tsu.tpm.vacancyparser.hw11.forkjoin.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.count.CountSummary;

/**
 * Строки отчёта ДЗ 11 — чистые функции от результатов подсчёта.
 *
 * <p>Отдельный класс позволяет проверить форматирование без запуска пула: таблица сравнения, ускорение
 * и пометки о числе партий — это вычисления над {@link CountSummary}, а не над потоками.
 */
public final class ForkJoinReport {

    private ForkJoinReport() {
    }

    /** Шапка таблицы сравнения. */
    public static String tableHeader() {
        return String.format(
                Locale.ROOT,
                "    %-32s %12s %10s %8s %8s %8s %7s",
                "вариант", "время, мс", "ускорение", "партий", "задач", "краж", "потоков");
    }

    /** Строка таблицы: вариант, время, ускорение относительно базы, разбиение и наблюдения. */
    public static String tableRow(CountSummary summary, CountSummary baseline) {
        double speedup = speedup(baseline, summary);
        return String.format(
                Locale.ROOT,
                "    %-32s %12.3f %9s %8d %8d %8d %7d",
                summary.label(),
                summary.durationMillis(),
                speedup <= 0 ? "—" : String.format(Locale.ROOT, "%.2f×", speedup),
                summary.batches(),
                summary.tasks(),
                summary.steals(),
                summary.threadCount());
    }

    /** Во сколько раз параллельный вариант быстрее базы (0, если база не измерена). */
    public static double speedup(CountSummary baseline, CountSummary summary) {
        if (baseline == null || summary == null || summary.durationNanos() <= 0) {
            return 0.0;
        }
        return baseline.durationNanos() / (double) summary.durationNanos();
    }

    /** Строка с агрегатом подсчёта и проверкой разбиения. */
    public static List<String> summaryLines(CountSummary summary) {
        List<String> lines = new ArrayList<>();
        lines.add(summary.reportLine());
        lines.add(summary.result().reportLine());
        lines.add("партии соответствуют ожидаемым: " + (summary.batchesAsExpected() ? "да" : "НЕТ")
                + " (" + summary.batches() + " из " + summary.expectedBatches() + ")");
        return lines;
    }

    /** Итоговая строка сравнения двух вариантов. */
    public static String comparisonLine(CountSummary baseline, CountSummary parallel) {
        double speedup = speedup(baseline, parallel);
        if (speedup <= 0) {
            return "сравнение недоступно: нет измерений";
        }
        return String.format(
                Locale.ROOT,
                "последовательно %.3f мс, Fork/Join %.3f мс — ускорение %.2f× при параллелизме %d",
                baseline.durationMillis(), parallel.durationMillis(), speedup, parallel.parallelism());
    }

    /** Совпадают ли агрегаты двух вариантов. */
    public static boolean sameResult(CountSummary first, CountSummary second) {
        return first.result().equals(second.result());
    }
}
