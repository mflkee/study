package ru.tsu.tpm.vacancyparser.hw08.pool.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.tsu.tpm.vacancyparser.hw08.pool.config.RunConfig;
import ru.tsu.tpm.vacancyparser.hw08.pool.config.WorkMode;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.OutcomeCategory;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.RequestResult;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.RunResult;

/**
 * Сводка: порядок строк, агрегаты, выигрыш от параллельности и сохранение в файл.
 */
class RunSummaryTest {

    private static final long MS = 1_000_000L;

    private static RunResult sample(long runNanos) {
        return new RunResult(
                List.of(
                        RequestResult.response("http://a/1", 200, 300 * MS),
                        RequestResult.response("http://a/2", 404, 100 * MS),
                        RequestResult.timeout("http://a/3", 200 * MS, "таймаут запроса"),
                        RequestResult.networkError("http://a/4", 50 * MS, "соединение не установлено"),
                        RequestResult.rejected("http://a/5", "некорректный адрес")),
                runNanos,
                2L);
    }

    private static RunConfig config() {
        return new RunConfig(WorkMode.LOCAL, List.of("http://a/1"), 2, 4, 2, 1_000L, 500L, 200L, 5_000L);
    }

    @Test
    @DisplayName("Строки сводки идут в порядке входного списка и содержат категории и коды")
    void linesFollowInputOrder() {
        List<String> lines = RunSummary.build(config(), sample(400 * MS), "порт заглушки: 12345", "пул остановлен");

        String text = String.join("\n", lines);
        assertThat(text)
                .contains("режим работы: local")
                .contains("записей в сводке: 5")
                .contains("порт заглушки: 12345")
                .contains("http://a/1")
                .contains("http://a/5");
        assertThat(text.indexOf("http://a/1")).isLessThan(text.indexOf("http://a/2"));
        assertThat(text.indexOf("http://a/2")).isLessThan(text.indexOf("http://a/5"));
    }

    @Test
    @DisplayName("Агрегаты согласованы: сумма категорий равна числу записей, коды — числу ответов")
    void aggregatesAreConsistent() {
        RunResult result = sample(400 * MS);

        long categorySum = RunSummary.categoryCountMap(result).values().stream().mapToLong(Long::longValue).sum();
        assertThat(categorySum).isEqualTo(result.size());

        long responseCount = result.results().stream().filter(RequestResult::hasStatus).count();
        long statusSum = RunSummary.statusDistributionMap(result).values().stream().mapToLong(Long::longValue).sum();
        assertThat(statusSum).isEqualTo(responseCount);

        assertThat(RunSummary.categoryCounts(result))
                .contains("получен ответ=2")
                .contains("таймаут=1")
                .contains("сетевая ошибка=1")
                .contains("отказ в выполнении=1");
        assertThat(RunSummary.statusDistribution(result)).contains("200=1").contains("404=1");
    }

    @Test
    @DisplayName("Статистика времени: минимум, среднее, максимум; при run < sum виден выигрыш от параллельности")
    void durationAndParallelism() {
        RunResult result = sample(400 * MS);
        RunSummary.DurationStats stats = RunSummary.durationStats(result);

        assertThat(stats.minMillis()).isEqualTo(50.0);
        assertThat(stats.maxMillis()).isEqualTo(300.0);
        assertThat(RunSummary.parallelismNote(result)).contains("меньше суммы времён ответов");

        RunResult slow = sample(2_000 * MS);
        assertThat(RunSummary.parallelismNote(slow))
                .contains("НЕ меньше")
                .contains("обработчик отказа");
    }

    @Test
    @DisplayName("Сводка сохраняется в файл каталога сборки с теми же строками")
    void summaryIsWrittenToFile(@TempDir Path tempDir) throws IOException {
        List<String> lines = RunSummary.build(config(), sample(400 * MS), "заметка", "пул остановлен");

        Path file = RunSummary.write(tempDir, lines);

        assertThat(file).exists();
        assertThat(Files.readString(file)).isEqualTo(String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }
}
