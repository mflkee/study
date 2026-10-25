package ru.tsu.tpm.vacancyparser.hw12.collections.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.tsu.tpm.vacancyparser.hw12.collections.benchmark.ConcurrentBenchmarks;
import ru.tsu.tpm.vacancyparser.hw12.collections.cache.CacheStats;
import ru.tsu.tpm.vacancyparser.hw12.collections.events.EventType;
import ru.tsu.tpm.vacancyparser.hw12.collections.ranking.SalaryRanking;

/**
 * Строки отчёта ДЗ 12 — чистые функции от результатов.
 *
 * <p>Сравнения, счётчики событий и топ рейтинга форматируются отдельно от демонстрации: их можно
 * проверить без запуска потоков, а в отчёт попадают ровно те строки, что напечатаны.
 */
public final class ConcurrentReport {

    private ConcurrentReport() {
    }

    /** Строка состояния кэша. */
    public static String cacheLine(CacheStats stats) {
        return stats.reportLine();
    }

    /** Строка событий: опубликовано, доставлено, отклонено. */
    public static String eventSummary(long published, long delivered, long rejected) {
        return String.format(
                Locale.ROOT,
                "событий опубликовано %d, доставлено %d, отклонено %d, потеряно %d",
                published, delivered, rejected, Math.max(0L, published - delivered));
    }

    /** Счётчики по типам событий (только ненулевые). */
    public static List<String> eventCountLines(Map<EventType, Long> counts) {
        List<String> lines = new ArrayList<>();
        counts.forEach((type, count) -> {
            if (count != null && count > 0) {
                lines.add(type.label() + ": " + count);
            }
        });
        return lines;
    }

    /** Во сколько раз один вариант быстрее другого по операциям в секунду. */
    public static double mapSpeedup(ConcurrentBenchmarks.MapRun faster, ConcurrentBenchmarks.MapRun slower) {
        if (faster == null || slower == null || slower.opsPerSecond() <= 0) {
            return 0.0;
        }
        return faster.opsPerSecond() / slower.opsPerSecond();
    }

    /** Строка сравнения двух замеров карт. */
    public static String mapComparison(ConcurrentBenchmarks.MapRun concurrent, ConcurrentBenchmarks.MapRun synchronizedMap) {
        double speedup = mapSpeedup(concurrent, synchronizedMap);
        if (speedup <= 0) {
            return "сравнение карт недоступно: нет измерений";
        }
        return String.format(
                Locale.ROOT,
                "%s быстрее %s в %.2f× (%.0f против %.0f оп/с)",
                concurrent.label(), synchronizedMap.label(), speedup,
                concurrent.opsPerSecond(), synchronizedMap.opsPerSecond());
    }

    /** Строка сравнения двух счётчиков. */
    public static String counterComparison(
            ConcurrentBenchmarks.CounterRun adder, ConcurrentBenchmarks.CounterRun atomic) {
        if (adder == null || atomic == null || atomic.opsPerSecond() <= 0) {
            return "сравнение счётчиков недоступно: нет измерений";
        }
        double speedup = adder.opsPerSecond() / atomic.opsPerSecond();
        return String.format(
                Locale.ROOT,
                "%s быстрее %s в %.2f× (%.0f против %.0f оп/с), оба без потерь: %s",
                adder.label(), atomic.label(), speedup, adder.opsPerSecond(), atomic.opsPerSecond(),
                adder.correct() && atomic.correct() ? "да" : "НЕТ");
    }

    /** Строки топа рейтинга. */
    public static List<String> rankingLines(SalaryRanking ranking, int limit) {
        if (ranking == null || limit <= 0) {
            return List.of();
        }
        List<String> lines = ranking.topGroups(limit);
        lines.add("идентификаторы топа: " + ranking.topIds(limit));
        return lines;
    }
}
