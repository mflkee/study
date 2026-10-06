package ru.tsu.tpm.vacancyparser.hw08.pool.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import ru.tsu.tpm.vacancyparser.hw08.pool.config.RunConfig;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.OutcomeCategory;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.RequestResult;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.RunResult;

/**
 * Формирование сводки ДЗ 8 и её сохранение в файл.
 *
 * <p>Строки сводки — чистые функции от результата прогона, поэтому их можно проверить без сети. Сводка
 * выводится в порядке входного списка, а не в порядке завершения запросов, и дополняется агрегатами:
 * счётчики по категориям, распределение по кодам, минимальное/среднее/максимальное время ответа,
 * суммарное время прогона и сумма времён ответов. Последняя пара показывает выигрыш от параллельности.
 *
 * <p>Сводка дополнительно пишется в файл каталога сборки: в отчёт переносится именно файл, а не то, что
 * осталось на экране терминала.
 */
public final class RunSummary {

    /** Имя файла сводки в каталоге сборки. */
    public static final String FILE_NAME = "hw08-summary.txt";

    /** Каталог по умолчанию для файла сводки. */
    public static final Path DEFAULT_DIR = Path.of("target");

    /** Статистика времени ответа. */
    public record DurationStats(double minMillis, double averageMillis, double maxMillis) {
    }

    private RunSummary() {
    }

    /** Собрать строки сводки. */
    public static List<String> build(RunConfig config, RunResult result, String modeNote, String shutdownNote) {
        List<String> lines = new ArrayList<>();
        lines.add("=== Сводка ДЗ 8 ===");
        lines.add("режим работы: " + config.mode().label());
        lines.add("адресов в списке: " + config.urls().size());
        lines.add("записей в сводке: " + result.size());
        if (modeNote != null && !modeNote.isBlank()) {
            lines.add(modeNote);
        }
        lines.add("");
        lines.add("Результаты по адресам (в порядке входного списка):");
        int index = 1;
        for (RequestResult entry : result.results()) {
            lines.add(String.format(Locale.ROOT, "%2d. %s", index++, entry.reportLine()));
        }

        lines.add("");
        lines.add("Агрегаты:");
        lines.add("    по категориям: " + categoryCounts(result));
        lines.add("    по статус-кодам: " + statusDistribution(result));
        DurationStats stats = durationStats(result);
        lines.add(String.format(
                Locale.ROOT,
                "    время ответа: минимум %.3f мс, среднее %.3f мс, максимум %.3f мс",
                stats.minMillis(), stats.averageMillis(), stats.maxMillis()));
        lines.add(String.format(Locale.ROOT, "    суммарное время прогона: %.3f мс", result.runMillis()));
        lines.add(String.format(Locale.ROOT, "    сумма времён ответов: %.3f мс", result.sumResponseMillis()));
        lines.add("    выигрыш от параллельности: " + parallelismNote(result));
        lines.add("    срабатываний обработчика отказа: " + result.rejectionCount());
        lines.add("    остановка пула: " + shutdownNote);
        return lines;
    }

    /** Счётчики по категориям исхода. */
    public static Map<OutcomeCategory, Long> categoryCountMap(RunResult result) {
        Map<OutcomeCategory, Long> counts = new LinkedHashMap<>();
        for (OutcomeCategory category : OutcomeCategory.values()) {
            counts.put(category, 0L);
        }
        for (RequestResult entry : result.results()) {
            counts.merge(entry.category(), 1L, Long::sum);
        }
        return counts;
    }

    /** Строковое представление счётчиков по категориям (нулевые категории опускаются). */
    public static String categoryCounts(RunResult result) {
        List<String> parts = new ArrayList<>();
        categoryCountMap(result).forEach((category, count) -> {
            if (count > 0) {
                parts.add(category.label() + "=" + count);
            }
        });
        return String.join(", ", parts);
    }

    /** Распределение по статус-кодам (только полученные ответы). */
    public static Map<Integer, Long> statusDistributionMap(RunResult result) {
        Map<Integer, Long> distribution = new TreeMap<>();
        for (RequestResult entry : result.results()) {
            if (entry.hasStatus()) {
                distribution.merge(entry.httpStatus(), 1L, Long::sum);
            }
        }
        return distribution;
    }

    /** Строковое представление распределения по кодам. */
    public static String statusDistribution(RunResult result) {
        List<String> parts = new ArrayList<>();
        statusDistributionMap(result).forEach((status, count) -> parts.add(status + "=" + count));
        return parts.isEmpty() ? "(ответов с кодом не было)" : String.join(", ", parts);
    }

    /** Минимальное, среднее и максимальное время ответа по записям с измеренным временем. */
    public static DurationStats durationStats(RunResult result) {
        long min = Long.MAX_VALUE;
        long max = 0L;
        long total = 0L;
        int measured = 0;
        for (RequestResult entry : result.results()) {
            if (entry.responseNanos() <= 0) {
                continue;
            }
            min = Math.min(min, entry.responseNanos());
            max = Math.max(max, entry.responseNanos());
            total += entry.responseNanos();
            measured++;
        }
        if (measured == 0) {
            return new DurationStats(0.0, 0.0, 0.0);
        }
        double average = total / (double) measured;
        return new DurationStats(min / 1_000_000.0, average / 1_000_000.0, max / 1_000_000.0);
    }

    /** Пояснение о выигрыше от параллельности, привязанное к измеренным числам. */
    public static String parallelismNote(RunResult result) {
        long run = result.runNanos();
        long sum = result.sumResponseNanos();
        if (run <= 0 || sum <= 0) {
            return "недостаточно данных";
        }
        if (run >= sum) {
            return String.format(
                    Locale.ROOT,
                    "суммарное время прогона (%.3f мс) НЕ меньше суммы времён ответов (%.3f мс) — "
                            + "параллелизм не проявился (мало потоков или сработал обработчик отказа)",
                    result.runMillis(), result.sumResponseMillis());
        }
        return String.format(
                Locale.ROOT,
                "суммарное время прогона %.3f мс меньше суммы времён ответов %.3f мс — запросы перекрывались по времени",
                result.runMillis(), result.sumResponseMillis());
    }

    /** Записать сводку в файл, создав каталог при необходимости. */
    public static Path write(Path directory, List<String> lines) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(FILE_NAME);
        Files.writeString(file, String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        return file;
    }
}
