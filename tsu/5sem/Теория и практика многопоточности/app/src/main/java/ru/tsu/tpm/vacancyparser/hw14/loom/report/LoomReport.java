package ru.tsu.tpm.vacancyparser.hw14.loom.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.BenchmarkResult;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.LoadRun;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.PageLoad;
import ru.tsu.tpm.vacancyparser.hw14.loom.pinning.PinningRun;
import ru.tsu.tpm.vacancyparser.hw14.loom.structured.ScopeRun;
import ru.tsu.tpm.vacancyparser.hw14.loom.swarm.SwarmRun;

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
        List<String> headers = List.of("исполнитель", "время, мс", "стр./с", "пик",
                "потоков", "virt/platf", "JVM-потоков");
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
                "ожидание, мс");
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
        List<String> headers = List.of("исполнитель", "задач", "время, мс", "задач/с", "пик",
                "рабочих", "JVM-потоков");
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

    /** Итоговая таблица без новых этапов — удобно там, где они не запускались. */
    public static List<String> summaryLines(
            LoadRun platformLoad,
            LoadRun virtualLoad,
            PinningRun pinned,
            PinningRun lock,
            BenchmarkResult platformScaling,
            BenchmarkResult virtualScaling) {
        return summaryLines(
                platformLoad, virtualLoad, pinned, lock, platformScaling, virtualScaling,
                null, null, List.of());
    }

    /**
     * Строки сравнения «10 000 задач через {@code Thread.ofVirtual().start()} и через
     * {@code new Thread().start()}».
     *
     * <p>Отдельная таблица нужна потому, что вопрос этого этапа — не «кто быстрее выполняет работу»
     * (работа одинаковая и почти пустая), а «сколько стоит сам поток»: запуск, память, число потоков
     * ОС. Поэтому в таблице есть колонка «старт, мс» — цена {@code start()} без ожидания задач.
     */
    public static List<String> swarmLines(SwarmRun virtual, SwarmRun platform) {
        List<String> headers = List.of("исполнитель", "задач", "старт, мс", "всего, мс", "CPU, мс",
                "задач/с", "пик", "имён");
        List<List<String>> rows = List.of(
                List.of(virtual.label(),
                        String.valueOf(virtual.tasks()),
                        format("%.1f", virtual.startMillis()),
                        format("%.1f", virtual.wallMillis()),
                        format("%.0f", virtual.cpuMillis()),
                        format("%.0f", virtual.throughputPerSecond()),
                        String.valueOf(virtual.peakConcurrentTasks()),
                        String.valueOf(virtual.distinctNames())),
                List.of(platform.label(),
                        String.valueOf(platform.tasks()),
                        format("%.1f", platform.startMillis()),
                        format("%.1f", platform.wallMillis()),
                        format("%.0f", platform.cpuMillis()),
                        format("%.0f", platform.throughputPerSecond()),
                        String.valueOf(platform.peakConcurrentTasks()),
                        String.valueOf(platform.distinctNames())));
        List<String> lines = new ArrayList<>(table(headers, rows));
        lines.add(String.format(
                Locale.ROOT,
                "вывод: те же %d задач — виртуальные потоки в %.1f раза быстрее (%.1f мс против %.1f мс), "
                        + "но главное не время, а цена потока: запуск %d потоков ОС занял %.1f мс, "
                        + "запуск %d виртуальных — %.1f мс; пик одновременных задач %d против %d",
                virtual.tasks(),
                SwarmRun.speedup(platform, virtual),
                virtual.wallMillis(), platform.wallMillis(),
                platform.tasks(), platform.startMillis(),
                virtual.tasks(), virtual.startMillis(),
                virtual.peakConcurrentTasks(), platform.peakConcurrentTasks()));
        return lines;
    }

    /** Таблица опытов со {@code StructuredTaskScope}: чем закончилась каждая группа. */
    public static List<String> structuredLines(List<ScopeRun> runs) {
        List<String> headers = List.of("сценарий", "политика", "подзадач", "время, мс",
                "успешно", "сбоев", "отменено", "итог");
        List<List<String>> rows = new ArrayList<>(runs.size());
        for (ScopeRun run : runs) {
            rows.add(List.of(
                    run.label(),
                    run.policy(),
                    String.valueOf(run.tasks()),
                    format("%.1f", run.wallMillis()),
                    String.valueOf(run.succeeded()),
                    String.valueOf(run.failed()),
                    String.valueOf(run.cancelled()),
                    run.outcome()));
        }
        return table(headers, rows);
    }

    /** Пояснения к опытам: тип исключения, сообщение и сколько подзадач успело уцелеть. */
    public static List<String> structuredExplanationLines(List<ScopeRun> runs) {
        List<String> lines = new ArrayList<>(runs.size());
        for (ScopeRun run : runs) {
            lines.add(run.exceptionLine() + "; отменено " + run.cancelled() + " из " + run.tasks()
                    + " (" + format("%.0f", run.cancelledPercent()) + " %), прогон "
                    + format("%.1f", run.wallMillis()) + " мс");
        }
        return lines;
    }

    /** Итоговая таблица «этап — что показал». */
    public static List<String> summaryLines(
            LoadRun platformLoad,
            LoadRun virtualLoad,
            PinningRun pinned,
            PinningRun lock,
            BenchmarkResult platformScaling,
            BenchmarkResult virtualScaling,
            SwarmRun swarmVirtual,
            SwarmRun swarmPlatform,
            List<ScopeRun> structured) {
        List<String> headers = List.of("этап", "исполнитель", "время, мс", "пик задач", "итог");
        List<List<String>> rows = new ArrayList<>(List.of(
                List.of("страницы", "пул " + platformLoad.metrics().workerThreads(),
                        format("%.1f", platformLoad.metrics().wallMillis()),
                        String.valueOf(platformLoad.metrics().peakConcurrentTasks()),
                        platformLoad.metrics().succeeded() + " стр. загружены"),
                List.of("страницы", "виртуальные",
                        format("%.1f", virtualLoad.metrics().wallMillis()),
                        String.valueOf(virtualLoad.metrics().peakConcurrentTasks()),
                        virtualLoad.metrics().succeeded() + " стр. загружены"),
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
                        "ждут в очереди пула"),
                List.of("масштабирование", "виртуальные",
                        format("%.1f", virtualScaling.wallMillis()),
                        String.valueOf(virtualScaling.peakConcurrentTasks()),
                        "все ждут одновременно")));
        if (swarmVirtual != null && swarmPlatform != null) {
            rows.add(List.of("поток на задачу", "new Thread().start",
                    format("%.1f", swarmPlatform.wallMillis()),
                    String.valueOf(swarmPlatform.peakConcurrentTasks()),
                    "потоков ОС: +" + swarmPlatform.peakPlatformThreads()));
            rows.add(List.of("поток на задачу", "Thread.ofVirtual().start",
                    format("%.1f", swarmVirtual.wallMillis()),
                    String.valueOf(swarmVirtual.peakConcurrentTasks()),
                    "потоков ОС: +" + swarmVirtual.peakPlatformThreads()));
        }
        for (ScopeRun run : structured) {
            rows.add(List.of("StructuredTaskScope", run.policy(),
                    format("%.1f", run.wallMillis()),
                    String.valueOf(run.peakConcurrentTasks()),
                    run.outcome()));
        }
        return table(headers, rows);
    }

    /** Число с одним знаком после запятой. */
    public static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }
}
