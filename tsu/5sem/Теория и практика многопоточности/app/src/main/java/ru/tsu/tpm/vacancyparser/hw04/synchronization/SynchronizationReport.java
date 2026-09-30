package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Формирование вывода замеров ДЗ 4.
 *
 * <p>Собрано отдельно от {@link SyncBenchmark} по той же причине, что и в ДЗ 3: печать внутри
 * измеряемого участка испортила бы замер, а строки вывода — чистые функции от результатов, поэтому
 * их можно проверить без запуска нагрузки.
 */
public final class SynchronizationReport {

    private SynchronizationReport() {
    }

    /** Итог нагрузочного прогона: число потоков, число операций и состояние после прогона. */
    public static List<String> mixedLoadBlock(SyncBenchmark.MixedLoadOutcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Нагрузочный прогон (потоки одновременно пишут, читают и переводят):");
        lines.add(String.format(
                Locale.ROOT, "    потоков: %d, элементов на поток: %d, переводов на поток: %d",
                outcome.threads(), outcome.itemsPerThread(), outcome.transfersPerThread()));
        lines.add(String.format(
                Locale.ROOT, "    собрано элементов: %d, счётчик обработанных: %d (ожидалось %d)",
                outcome.collectedCount(), outcome.counterActual(), outcome.counterExpected()));
        lines.add(String.format(
                Locale.ROOT, "    время прогона: %.3f мс", millis(outcome.elapsedNanos())));

        if (outcome.collectorConsistent()) {
            lines.add("    инвариант счётчика: выполнен — размер списка, счётчик и ключи согласованы");
        } else {
            lines.add("    инвариант счётчика: НАРУШЕН — " + String.join("; ", outcome.invariantViolations()));
        }

        lines.add(String.format(
                Locale.ROOT, "    сумма балансов счетов: %d -> %d", outcome.accountsBefore(), outcome.accountsAfter()));
        lines.add("    сумма балансов: "
                + (outcome.accountsPreserved() ? "сохранена" : "НАРУШЕНА — средства потеряны или созданы"));
        return lines;
    }

    /** Сравнение защищённого и незащищённого счётчиков: все прогоны, а не только лучший. */
    public static List<String> counterComparisonBlock(SyncBenchmark.CounterComparison comparison) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Сравнение режимов защиты общего счётчика:");
        lines.add(String.format(
                Locale.ROOT, "    потоков: %d, операций на поток: %d, ожидаемый итог: %d",
                comparison.threads(), comparison.operationsPerThread(), comparison.expected()));

        for (Protection protection : Protection.values()) {
            lines.add("    " + protection.label() + ":");
            for (SyncBenchmark.CounterRun run : comparison.runs()) {
                if (!run.protection().equals(protection.label())) {
                    continue;
                }
                lines.add(String.format(
                        Locale.ROOT,
                        "        %8.3f мс, счётчик %d из %d%s",
                        millis(run.nanos()),
                        run.value(),
                        run.expected(),
                        run.exact() ? "" : " (потеряно " + run.lost() + ")"));
            }
            lines.add(String.format(
                    Locale.ROOT,
                    "        среднее по прогонам: %.3f мс",
                    averageMillis(comparison.nanosFor(protection.label()))));
        }
        return lines;
    }

    /** Сравнение перевода между счетами: время и сохранность суммы балансов. */
    public static List<String> transferComparisonBlock(SyncBenchmark.TransferComparison comparison) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Операция перевода между двумя счетами:");
        lines.add(String.format(
                Locale.ROOT, "    потоков: %d, переводов на поток: %d, сумма перевода: %d",
                comparison.threads(), comparison.transfersPerThread(), comparison.amount()));

        for (Protection protection : Protection.values()) {
            lines.add("    " + protection.label() + ":");
            for (SyncBenchmark.TransferRun run : comparison.runs()) {
                if (!run.protection().equals(protection.label())) {
                    continue;
                }
                lines.add(String.format(
                        Locale.ROOT,
                        "        %8.3f мс, сумма балансов %d -> %d%s",
                        millis(run.nanos()),
                        run.before(),
                        run.after(),
                        run.preserved() ? " (сохранена)" : " (НАРУШЕНА)"));
            }
            lines.add(String.format(
                    Locale.ROOT,
                    "        среднее по прогонам: %.3f мс",
                    averageMillis(comparison.nanosFor(protection.label()))));
        }
        return lines;
    }

    /**
     * Вывод о цене синхронизации на этой машине.
     *
     * <p>Формулировка привязана к измеренным числам и к режиму: если защищённый вариант медленнее,
     * это и говорится, с указанием числа потоков. Никаких «синхронизация всегда дорога» —
     * на одном потоке она почти бесплатна.
     */
    public static String synchronizationCost(SyncBenchmark.CounterComparison comparison) {
        double protectedNanos = average(comparison.nanosFor(Protection.ON.label()));
        double unprotectedNanos = average(comparison.nanosFor(Protection.OFF.label()));
        if (protectedNanos <= 0 || unprotectedNanos <= 0) {
            return "недостаточно данных для сравнения";
        }
        double ratio = protectedNanos / unprotectedNanos;
        return String.format(
                Locale.ROOT,
                "на этой машине при %d потоках защита счётчика стоит %.2f× от незащищённого варианта "
                        + "(%.3f мс против %.3f мс на %d операций)",
                comparison.threads(),
                ratio,
                millis((long) protectedNanos),
                millis((long) unprotectedNanos),
                comparison.expected());
    }

    private static double average(List<Long> nanos) {
        return nanos.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private static double averageMillis(List<Long> nanos) {
        return average(nanos) / 1_000_000.0;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
