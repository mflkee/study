package ru.tsu.tpm.vacancyparser.hw14.loom.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.ThreadProbe;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.BenchmarkResult;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.LoadRun;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.PageLoad;
import ru.tsu.tpm.vacancyparser.hw14.loom.pinning.PinningRun;

/**
 * Строки отчёта: таблицы, выводы и примеры. Числа задаются вручную, поэтому проверка не зависит от
 * реальных замеров и не бывает «плавающей».
 */
class LoomReportTest {

    private static final BenchmarkResult PLATFORM = new BenchmarkResult(
            "платформенный пул", 200, 200, 0, 0, 700_000_000L, 16, 16, 0, 200, 16, 1024);
    private static final BenchmarkResult VIRTUAL = new BenchmarkResult(
            "виртуальные потоки", 200, 200, 0, 0, 120_000_000L, 200, 200, 200, 0, 0, 2048);

    @Test
    @DisplayName("Таблица выравнивает столбцы, а сравнение печатает вывод и оба исполнителя")
    void tableAndComparisonLines() {
        List<String> table = LoomReport.table(
                List.of("a", "длинный столбец"),
                List.of(List.of("1", "2"), List.of("10", "20")));
        assertThat(table).hasSize(4);
        assertThat(table.get(0)).startsWith("a");
        assertThat(table.get(1)).contains("-");
        assertThat(table.get(2).indexOf("2")).isEqualTo(table.get(3).indexOf("20"));

        List<String> comparison = LoomReport.loadComparisonLines(
                new LoadRun(PLATFORM, pages(5)), new LoadRun(VIRTUAL, pages(5)));
        assertThat(comparison).anyMatch(line -> line.contains("платформенный пул"));
        assertThat(comparison).anyMatch(line -> line.contains("виртуальные потоки"));
        assertThat(comparison.get(comparison.size() - 1))
                .contains("вывод")
                .contains("быстрее")
                .contains("5.8");
    }

    @Test
    @DisplayName("Pinning, примеры заголовков и итоговая таблица собираются из результатов")
    void pinningTitlesAndSummary() {
        PinningRun pinned = new PinningRun(
                "synchronized (монитор JVM)", "synchronized", 96, 60L, 486_000_000L,
                12, 12, Set.of("ForkJoinPool-1-worker-1", "ForkJoinPool-1-worker-2"), 0, 96, 12);
        PinningRun free = new PinningRun(
                "ReentrantLock (очередь JDK)", "ReentrantLock", 96, 60L, 62_000_000L,
                96, 12, Set.of("ForkJoinPool-1-worker-1"), 88, 96, 12);

        List<String> pinning = LoomReport.pinningLines(pinned, free);
        assertThat(pinning).anyMatch(line -> line.contains("synchronized"));
        assertThat(pinning).anyMatch(line -> line.contains("ReentrantLock"));
        String conclusion = pinning.get(pinning.size() - 1);
        assertThat(conclusion).contains("удерживает поток на carrier").contains("96");

        LoadRun virtual = new LoadRun(VIRTUAL, pages(5));
        assertThat(LoomReport.titleLines(virtual, 2)).hasSize(2);
        assertThat(LoomReport.titleLines(virtual, 0)).isEmpty();
        assertThat(LoomReport.titleLines(virtual, 1).get(0)).contains("№0").contains("Java-разработчик");
        assertThat(LoomReport.probeLines(virtual, 1).get(0))
                .contains("виртуальный: да")
                .contains("ForkJoinPool-1-worker-1");

        List<String> summary = LoomReport.summaryLines(
                new LoadRun(PLATFORM, pages(200)), new LoadRun(VIRTUAL, pages(200)),
                pinned, free, PLATFORM, VIRTUAL);
        assertThat(summary).hasSize(8);
        assertThat(summary).anyMatch(line -> line.contains("масштабирование"));
        assertThat(LoomReport.conclusionLine(new LoadRun(PLATFORM, pages(1)), new LoadRun(VIRTUAL, pages(1))))
                .contains("быстрее в 5.8 раза");
    }

    private static List<PageLoad> pages(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> PageLoad.success(
                        "http://127.0.0.1:1/vacancy/" + index,
                        index,
                        200,
                        "Вакансия №" + index + " — Java-разработчик",
                        1_000_000L,
                        new ThreadProbe("hw14-vt-" + index, index + 1L, true, "ForkJoinPool-1-worker-1", "runnable")))
                .toList();
    }
}
