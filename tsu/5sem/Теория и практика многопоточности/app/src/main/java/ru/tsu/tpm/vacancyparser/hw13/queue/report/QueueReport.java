package ru.tsu.tpm.vacancyparser.hw13.queue.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.PageResult;

/**
 * Строки отчёта ДЗ 13 — чистые функции от результата прогона.
 *
 * <p>Здесь собрано всё, что демонстрация печатает и что попадает в отчёт: сводка, проверка отсутствия
 * потерь и повторов, распределение работы по потребителям, примеры извлечённых заголовков и сравнение
 * двух ёмкостей очереди. Проверки форматирования не требуют потоков и сети.
 */
public final class QueueReport {

    private QueueReport() {
    }

    /** Сводка прогона и проверки корректности. */
    public static List<String> summaryLines(CrawlReport report) {
        List<String> lines = new ArrayList<>();
        lines.add(report.reportLine());
        lines.add(integrityLine(report));
        lines.add("распределение по потребителям: " + consumerLoads(report));
        return lines;
    }

    /** Проверка «ничего не потеряно и не обработано дважды». */
    public static String integrityLine(CrawlReport report) {
        return "без потерь: " + marker(report.lossless())
                + ", повторов страниц: " + marker(report.duplicatePages())
                + ", разных страниц " + report.distinctPages() + " из " + report.consumed()
                + ", все потоки завершились: " + marker(report.stoppedWithinTimeout());
    }

    /** Сколько страниц обработал каждый потребитель. */
    public static String consumerLoads(CrawlReport report) {
        Map<String, Integer> loads = new TreeMap<>();
        for (PageResult result : report.results()) {
            loads.merge(result.consumer(), 1, Integer::sum);
        }
        if (loads.isEmpty()) {
            return "(нет результатов)";
        }
        StringBuilder text = new StringBuilder();
        loads.forEach((consumer, count) -> {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(consumer).append("=").append(count);
        });
        return text.toString();
    }

    /** Сколько разных потребителей участвовало в обработке. */
    public static int consumerCount(CrawlReport report) {
        return (int) report.results().stream().map(PageResult::consumer).distinct().count();
    }

    /** Первые {@code limit} успешно извлечённых заголовков в порядке номеров страниц. */
    public static List<String> titleLines(CrawlReport report, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<PageResult> successful = report.results().stream()
                .filter(PageResult::ok)
                .sorted((left, right) -> Integer.compare(left.pageNumber(), right.pageNumber()))
                .limit(limit)
                .toList();
        List<String> lines = new ArrayList<>(successful.size());
        for (PageResult result : successful) {
            lines.add(String.format(
                    Locale.ROOT, "№%d «%s» (поток %s)", result.pageNumber(), result.title(), result.consumer()));
        }
        return lines;
    }

    /** Сводка сбоев по кодам ответа. */
    public static String failuresByStatus(CrawlReport report) {
        Map<Integer, Integer> counts = new TreeMap<>();
        report.results().stream()
                .filter(result -> !result.ok())
                .forEach(result -> counts.merge(result.status(), 1, Integer::sum));
        if (counts.isEmpty()) {
            return "сбоев нет";
        }
        StringBuilder text = new StringBuilder();
        counts.forEach((status, count) -> {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append("код ").append(status).append(": ").append(count);
        });
        return text.toString();
    }

    /**
     * Сравнение двух прогонов с разной ёмкостью очереди.
     *
     * <p>Ключевой вывод — не «быстрее/медленнее» (пропускную способность ограничивают потребители), а
     * <b>как ведёт себя очередь</b>: маленькая заполняется до предела и заставляет производителей
     * ждать, большая принимает всё сразу и просто копит задачи.
     */
    public static List<String> comparisonLines(CrawlReport small, CrawlReport large) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(
                Locale.ROOT,
                "малая очередь (ёмкость %d): глубина %d, блокировок put %d, ожидание %.1f мс, %.1f стр./с",
                small.queueCapacity(), small.maxQueueDepth(), small.blockedPuts(),
                small.blockedPutMillis(), small.throughputPerSecond()));
        lines.add(String.format(
                Locale.ROOT,
                "большая очередь (ёмкость %d): глубина %d, блокировок put %d, ожидание %.1f мс, %.1f стр./с",
                large.queueCapacity(), large.maxQueueDepth(), large.blockedPuts(),
                large.blockedPutMillis(), large.throughputPerSecond()));
        lines.add("вывод: ограниченная очередь включает обратное давление (put ждёт), "
                + "большая — копит задачи, но не сдерживает производителя");
        return lines;
    }

    /** Сводка по страницам: сколько успешных и неуспешных, какие коды. */
    public static Map<String, String> keyNumbers(CrawlReport report) {
        Map<String, String> numbers = new LinkedHashMap<>();
        numbers.put("подано адресов", String.valueOf(report.requested()));
        numbers.put("обработано", String.valueOf(report.consumed()));
        numbers.put("успешно", String.valueOf(report.succeeded()));
        numbers.put("сбоев", String.valueOf(report.failed()));
        numbers.put("максимум в очереди", report.maxQueueDepth() + " из " + report.queueCapacity());
        return numbers;
    }

    private static String marker(boolean value) {
        return value ? "да" : "нет";
    }
}
