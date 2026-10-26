package ru.tsu.tpm.vacancyparser.hw14.loom.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.BenchmarkResult;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.LoadRun;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.PageLoad;
import ru.tsu.tpm.vacancyparser.hw14.loom.pinning.PinningRun;

/**
 * Строки отчёта ДЗ 14 — чистые функции от результатов прогонов.
 *
 * <p>Здесь нет ни потоков, ни сети: на вход подаются уже собранные метрики, на выходе — строки
 * таблиц и выводов. Благодаря этому форматирование проверяется юнит-тестами на заранее заданных
 * числах, а не «на глаз» по выводу демонстрации.
 *
 * <p>Таблицы выравниваются по длине строки. Для кириллицы это приблизительно (в моноширинном
 * шрифте консоли ширина совпадает, в пропорциональном — нет), поэтому отчёт в README дополнительно
 * содержит обычные markdown-таблицы.
 */
public final class LoomReport {

    private LoomReport() {
    }

    /** Таблица с выравниванием по столбцам: заголовок, разделитель, строки. */
    public static List<String> table(List<String> headers, List<List<String>> rows) {
        int columns = headers.size();
        int[] widths = new int[columns];
        for (int column = 0; column < columns; column++) {
            widths[column] = headers.get(column).length();
        }
        for (List<String> row : rows) {
            for (int column = 0; column < columns && column < row.size(); column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        }

        List<String> lines = new ArrayList<>(rows.size() + 2);
        lines.add(row(headers, widths));
        StringBuilder separator = new StringBuilder();
        for (int column = 0; column < columns; column++) {
            if (column > 0) {
                separator.append("  ");
            }
            separator.append("-".repeat(widths[column]));
        }
        lines.add(separator.toString());
        for (List<String> row : rows) {
            List<String> padded = new ArrayList<>(columns);
            for (int column = 0; column < columns; column++) {
                padded.add(column < row.size() ? row.get(column) : "");
            }
            lines.add(row(padded, widths));
        }
        return lines;
    }

    private static String row(List<String> cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int column = 0; column < widths.length; column++) {
            if (column > 0) {
                line.append("  ");
            }
            String value = column < cells.size() ? cells.get(column) : "";
            line.append(value).append(" ".repeat(Math.max(0, widths[column] - value.length())));
        }
        return line.toString().stripTrailing();
    }

    /** Сравнение «один объём работы, два исполнителя» для загрузки страниц. */
    public static List<String> loadComparisonLines(LoadRun platform, LoadRun virtual) {
        List<String> headers = List.of("исполнитель", "время, мс", "стр./с", "пик задач",
                "потоков-исполнителей", "исполнений virtual/platform", "прирост потоков JVM");
        List<List<String>> rows = List.of(
                row("платформенный пул", platform),
                row("виртуальные потоки", virtual));
        List<String> lines = new ArrayList<>(table(headers, rows));
        lines.add(conclusionLine(platform, virtual));
        return lines;
    }

    private static List<String> row(String label, LoadRun run) {
        BenchmarkResult metrics = run.metrics();
        return List.of(
                label,
                format("%.1f", metrics.wallMillis()),
                format("%.1f", metrics.throughputPerSecond()),
                String.valueOf(metrics.peakConcurrentTasks()),
                String.valueOf(metrics.workerThreads()),
                metrics.virtualExecutions() + "/" + metrics.platformExecutions(),
                String.valueOf(metrics.platformThreadsPeakDelta()));
    }

    /** Вывод сравнения: во сколько раз виртуальные потоки быстрее и за счёт чего. */
    public static String conclusionLine(LoadRun platform, LoadRun virtual) {
        double platformMillis = platform.metrics().wallMillis();
        double virtualMillis = virtual.metrics().wallMillis();
        double speedup = virtualMillis <= 0 ? 0.0 : platformMillis / virtualMillis;
        return String.format(
                Locale.ROOT,
                "вывод: тот же объём работы, виртуальные потоки быстрее в %.1f раза "
                        + "(%.1f мс против %.1f мс) — выигрыш даёт ожидание IO, а не процессор",
                speedup, virtualMillis, platformMillis);
    }

    /** Примеры загруженных страниц: номер, заголовок и поток, выполнивший запрос. */
    public static List<String> titleLines(LoadRun run, int limit) {
        List<String> lines = new ArrayList<>();
        for (PageLoad page : run.sample(limit)) {
            lines.add(String.format(
                    Locale.ROOT, "№%d «%s» (%s)", page.pageNumber(), page.title(), page.worker().shortLabel()));
        }
        return lines;
    }

    /** Доказательство «поток действительно виртуальный»: имя, carrier и состояние. */
    public static List<String> probeLines(LoadRun run, int limit) {
        List<String> lines = new ArrayList<>();
        for (PageLoad page : run.sample(limit)) {
            lines.add(String.format(
                    Locale.ROOT,
                    "№%d поток %s, виртуальный: %s, carrier: %s",
                    page.pageNumber(),
                    page.worker().name().isBlank() ? "(без имени)" : page.worker().name(),
                    page.worker().virtual() ? "да" : "нет",
                    page.worker().carrier().isBlank() ? "(нет — платформенный поток)" : page.worker().carrier()));
        }
        return lines;
    }

    /** Сравнение pinning: synchronized против ReentrantLock. */
    public static List<String> pinningLines(PinningRun synchronizedRun, PinningRun lockRun) {
        List<String> headers = List.of("защита", "время, мс", "пик задач", "carrier'ов", "смен carrier'а",
                "ожидание при pinning, мс");
        List<List<String>> rows = List.of(
                List.of(synchronizedRun.lockKind(),
                        format("%.1f", synchronizedRun.wallMillis()),
                        String.valueOf(synchronizedRun.peakConcurrentTasks()),
                        synchronizedRun.carriersUsed() + " из " + synchronizedRun.parallelism(),
                        String.valueOf(synchronizedRun.carrierSwitches()),
                        format("%.1f", synchronizedRun.expectedPinnedMillis())),
                List.of(lockRun.lockKind(),
                        format("%.1f", lockRun.wallMillis()),
                        String.valueOf(lockRun.peakConcurrentTasks()),
                        lockRun.carriersUsed() + " из " + lockRun.parallelism(),
                        String.valueOf(lockRun.carrierSwitches()),
                        format("%.1f", lockRun.expectedPinnedMillis())));
        List<String> lines = new ArrayList<>(table(headers, rows));
        lines.add(String.format(
                Locale.ROOT,
                "вывод: synchronized удерживает поток на carrier'е — одновременно работают не больше %d задач, "
                        + "прогон идёт %.1f мс; ReentrantLock отпускает carrier — одновременно работают все %d задач "
                        + "и прогон идёт %.1f мс",
                synchronizedRun.parallelism(), synchronizedRun.wallMillis(),
                lockRun.peakConcurrentTasks(), lockRun.wallMillis()));
        return lines;
    }

    /** Сравнение масштабирования: тысячи спящих задач на пуле и на виртуальных потоках. */
    public static List<String> scalingLines(BenchmarkResult platform, BenchmarkResult virtual) {
        List<String> headers = List.of("исполнитель", "задач", "время, мс", "задач/с", "пик задач",
                "потоков-исполнителей", "прирост потоков JVM");
        List<List<String>> rows = List.of(
                List.of(platform.label(),
                        String.valueOf(platform.tasks()),
                        format("%.1f", platform.wallMillis()),
                        format("%.1f", platform.throughputPerSecond()),
                        String.valueOf(platform.peakConcurrentTasks()),
                        String.valueOf(platform.workerThreads()),
                        String.valueOf(platform.platformThreadsPeakDelta())),
                List.of(virtual.label(),
                        String.valueOf(virtual.tasks()),
                        format("%.1f", virtual.wallMillis()),
                        format("%.1f", virtual.throughputPerSecond()),
                        String.valueOf(virtual.peakConcurrentTasks()),
                        String.valueOf(virtual.workerThreads()),
                        String.valueOf(virtual.platformThreadsPeakDelta())));
        List<String> lines = new ArrayList<>(table(headers, rows));
        double speedup = virtual.wallMillis() <= 0 ? 0.0 : platform.wallMillis() / virtual.wallMillis();
        lines.add(String.format(
                Locale.ROOT,
                "вывод: %d задач с ожиданием — виртуальные потоки быстрее в %.1f раза; "
                        + "платформенный пул на %d потоков ограничен их числом, виртуальные — нет",
                virtual.tasks(), speedup, platform.workerThreads()));
        return lines;
    }

    /** Итоговая таблица «этап — что показал». */
    public static List<String> summaryLines(
            LoadRun platformLoad,
            LoadRun virtualLoad,
            PinningRun pinned,
            PinningRun lock,
            BenchmarkResult platformScaling,
            BenchmarkResult virtualScaling) {
        List<String> headers = List.of("этап", "исполнитель", "время, мс", "пик задач", "итог");
        List<List<String>> rows = List.of(
                List.of("страницы", "пул " + platformLoad.metrics().workerThreads(),
                        format("%.1f", platformLoad.metrics().wallMillis()),
                        String.valueOf(platformLoad.metrics().peakConcurrentTasks()),
                        "все " + platformLoad.metrics().succeeded() + " страниц загружены"),
                List.of("страницы", "виртуальные",
                        format("%.1f", virtualLoad.metrics().wallMillis()),
                        String.valueOf(virtualLoad.metrics().peakConcurrentTasks()),
                        "все " + virtualLoad.metrics().succeeded() + " страниц загружены"),
                List.of("pinning", "synchronized",
                        format("%.1f", pinned.wallMillis()),
                        String.valueOf(pinned.peakConcurrentTasks()),
                        "ограничен carrier'ами"),
                List.of("pinning", "ReentrantLock",
                        format("%.1f", lock.wallMillis()),
                        String.valueOf(lock.peakConcurrentTasks()),
                        "carrier освобождается"),
                List.of("масштабирование", "пул " + platformScaling.workerThreads(),
                        format("%.1f", platformScaling.wallMillis()),
                        String.valueOf(platformScaling.peakConcurrentTasks()),
                        "задачи ждут в очереди пула"),
                List.of("масштабирование", "виртуальные",
                        format("%.1f", virtualScaling.wallMillis()),
                        String.valueOf(virtualScaling.peakConcurrentTasks()),
                        "все задачи ждут одновременно"));
        return table(headers, rows);
    }

    /** Число с одним знаком после запятой. */
    public static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }
}
