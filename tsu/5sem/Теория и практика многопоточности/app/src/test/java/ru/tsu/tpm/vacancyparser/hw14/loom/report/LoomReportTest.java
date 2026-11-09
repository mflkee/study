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
import ru.tsu.tpm.vacancyparser.hw14.loom.structured.ScopeRun;
import ru.tsu.tpm.vacancyparser.hw14.loom.swarm.SwarmRun;

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

    @Test
    @DisplayName("10 000 задач «поток на задачу» и опыты со скоупом попадают в таблицы")
    void swarmAndStructuredLines() {
        SwarmRun virtualSwarm = new SwarmRun(
                "Thread.ofVirtual().start", 10_000, 10_000, 0, 1, 0, 10_000,
                90_000_000L, 260_000_000L,
                java.util.stream.IntStream.range(0, 10_000)
                        .mapToObj(index -> "hw14-swarm-vt-" + index).toList(),
                10_000, 0, 2048L, 150_000_000L);
        SwarmRun platformSwarm = new SwarmRun(
                "new Thread(...).start", 10_000, 10_000, 0, 1, 9_990, 45,
                950_000_000L, 1_400_000_000L,
                java.util.stream.IntStream.range(0, 10_000)
                        .mapToObj(index -> "hw14-swarm-pt-" + (index + 1)).toList(),
                0, 10_000, 4096L, 700_000_000L);

        List<String> swarm = LoomReport.swarmLines(virtualSwarm, platformSwarm);
        assertThat(swarm).anyMatch(line -> line.contains("ofVirtual"));
        assertThat(swarm).anyMatch(line -> line.contains("new Thread"));
        String swarmConclusion = swarm.get(swarm.size() - 1);
        assertThat(swarmConclusion).contains("вывод").contains("5.4");
        assertThat(swarmConclusion).contains("45").contains("10000");

        ScopeRun all = new ScopeRun("все подзадачи успешны", "ShutdownOnFailure", 10_000,
                10_000, 0, 0, 120_000_000L, 10_000, 10_000, 0,
                "успех", null, null, List.of("задача №0"));
        ScopeRun failure = new ScopeRun("одна подзадача падает", "ShutdownOnFailure", 10_000,
                0, 1, 9_999, 104_000_000L, 10_000, 10_000, 0,
                "исключение", "IllegalStateException", "источник недоступен: подзадача №137",
                List.of());

        List<String> structured = LoomReport.structuredLines(List.of(all, failure));
        assertThat(structured).hasSize(4);
        assertThat(structured).anyMatch(line -> line.contains("ShutdownOnFailure"));
        assertThat(structured.get(2)).contains("10000");

        List<String> explanation = LoomReport.structuredExplanationLines(List.of(all, failure));
        assertThat(explanation).hasSize(2);
        assertThat(explanation.get(0)).contains("исключений нет");
        assertThat(explanation.get(1)).contains("IllegalStateException").contains("9999");

        List<String> summary = LoomReport.summaryLines(
                new LoadRun(PLATFORM, pages(200)), new LoadRun(VIRTUAL, pages(200)),
                pinned(), free(), PLATFORM, VIRTUAL,
                virtualSwarm, platformSwarm, List.of(all, failure));
        assertThat(summary).hasSize(8 + 2 + 2);
        assertThat(summary).anyMatch(line -> line.contains("поток на задачу"));
        assertThat(summary).anyMatch(line -> line.contains("StructuredTaskScope"));
    }

    private static PinningRun pinned() {
        return new PinningRun(
                "synchronized (монитор JVM)", "synchronized", 96, 60L, 486_000_000L,
                12, 12, Set.of("ForkJoinPool-1-worker-1"), 0, 96, 12);
    }

    private static PinningRun free() {
        return new PinningRun(
                "ReentrantLock (очередь JDK)", "ReentrantLock", 96, 60L, 62_000_000L,
                96, 12, Set.of("ForkJoinPool-1-worker-1"), 88, 96, 12);
    }

    private static List<PageLoad> pages(int count) {        return java.util.stream.IntStream.range(0, count)
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
