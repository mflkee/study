package ru.tsu.tpm.vacancyparser.hw03.parallel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки вывода: таблица времён, вердикт по операции и объяснение причины.
 *
 * <p>Проверки вердикта и объяснений идут на искусственных временах: на реальных замерах то, какой
 * вариант быстрее, зависит от машины, и «параллельный выиграл» нельзя гарантировать на любом
 * оборудовании.
 */
class ComparisonReportTest {

    private static final MeasurementConditions CONDITIONS = new MeasurementConditions(
            "26.0.2.1", 8, 7, 7, 6, 1_000_000, "0..1999999", 1, 5);

    /** Замер с заданным средним и разбросом: min и max разнесены на {@code spread} мс. */
    private static OperationComparison.TimingStats stats(double averageMillis, double spreadMillis) {
        return new OperationComparison.TimingStats(
                Duration.ofNanos((long) (averageMillis * 2_000_000)),
                Duration.ofNanos((long) (averageMillis * 1_000_000)),
                Duration.ofNanos((long) ((averageMillis - spreadMillis / 2) * 1_000_000)),
                Duration.ofNanos((long) ((averageMillis + spreadMillis / 2) * 1_000_000)),
                5);
    }

    private static OperationComparison comparison(String name, double sequential, double parallel) {
        return new OperationComparison(name, stats(sequential, 0.1), stats(parallel, 0.1), true);
    }

    private static OperationComparison.TimingStats stats(long cold, long average, long min, long max) {
        return new OperationComparison.TimingStats(
                Duration.ofMillis(cold), Duration.ofMillis(average),
                Duration.ofMillis(min), Duration.ofMillis(max), 5);
    }

    private static int indexOfLineContaining(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("Таблица содержит все три операции и обе колонки времён")
    void tableContainsAllOperationsAndBothColumns() {
        List<String> table = ComparisonReport.resultsTable(List.of(
                comparison("фильтрация", 6.0, 3.0),
                comparison("преобразование", 12.0, 4.0),
                comparison("агрегация", 5.0, 5.5)));

        String text = String.join("\n", table);

        assertThat(text)
                .as("заголовок обязан называть обе колонки времён")
                .contains("stream(), мс")
                .contains("parallelStream(), мс")
                .contains("parallel/stream");
        assertThat(text).contains("фильтрация").contains("преобразование").contains("агрегация");
        assertThat(text)
                .as("средние времена печатаются числом")
                .contains("6.000")
                .contains("3.000")
                .contains("12.000")
                .contains("4.000");
    }

    @Test
    @DisplayName("Таблица печатает минимум и максимум по повторам для обоих вариантов")
    void tableContainsMinAndMax() {
        String text = String.join("\n", ComparisonReport.resultsTable(
                List.of(comparison("фильтрация", 6.0, 3.0))));

        assertThat(text)
                .as("минимум и максимум нужны для оценки разброса")
                .contains("..")
                .contains("5.950")
                .contains("6.050")
                .contains("2.950")
                .contains("3.050");
    }

    @Test
    @DisplayName("Последовательный выиграл: фраза называет его и во сколько раз")
    void sequentialWinsIsPhrasedExplicitly() {
        ComparisonReport.Verdict verdict = ComparisonReport.verdict(comparison("фильтрация", 3.0, 6.0));

        assertThat(verdict.winner()).isEqualTo(ComparisonReport.Winner.SEQUENTIAL);
        assertThat(verdict.phrase()).contains("последовательный быстрее").contains("2.00");
    }

    @Test
    @DisplayName("Параллельный выиграл: фраза называет его и во сколько раз")
    void parallelWinsIsPhrasedExplicitly() {
        ComparisonReport.Verdict verdict = ComparisonReport.verdict(comparison("фильтрация", 6.0, 3.0));

        assertThat(verdict.winner()).isEqualTo(ComparisonReport.Winner.PARALLEL);
        assertThat(verdict.phrase()).contains("параллельный быстрее").contains("2.00");
    }

    @Test
    @DisplayName("Равные значения: выводится «значимой разницы не показано», а не произвольный победитель")
    void equalValuesReportNoSignificantDifference() {
        OperationComparison equal = new OperationComparison(
                "агрегация", stats(9, 5, 4, 6), stats(9, 5, 4, 6), true);

        ComparisonReport.Verdict verdict = ComparisonReport.verdict(equal);

        assertThat(verdict.winner()).isEqualTo(ComparisonReport.Winner.WITHIN_NOISE);
        assertThat(verdict.phrase())
                .contains("значимой разницы не показано")
                .doesNotContain("быстрее в");
    }

    @Test
    @DisplayName("Пересечение интервалов важнее отношения средних: 10 % не объявляется победой")
    void overlappingIntervalsBeatRatio() {
        // Интервалы [9, 11] и [8, 10] пересекаются, хотя средние различаются на 10 %.
        OperationComparison overlapping = new OperationComparison(
                "фильтрация", stats(20, 10, 9, 11), stats(18, 9, 8, 10), true);

        ComparisonReport.Verdict verdict = ComparisonReport.verdict(overlapping);

        assertThat(verdict.winner()).isEqualTo(ComparisonReport.Winner.WITHIN_NOISE);
    }

    @Test
    @DisplayName("При перекрытии интервалов причина неустойчивого замера называется прямо в выводе")
    void withinNoiseExplainsUnstableMeasurement() {
        OperationComparison noisy = new OperationComparison(
                "преобразование", stats(23, 23, 12, 35), stats(16, 7, 3, 16), true);

        Optional<String> note = ComparisonReport.spreadNote(noisy);

        assertThat(note)
                .as("для неустойчивого замера замечание обязано печататься")
                .isPresent()
                .hasValueSatisfying(text -> assertThat(text)
                        .contains("разброс повторов")
                        .contains("различие не подтверждено")
                        .contains("возможные причины"));
        assertThat(String.join("\n", ComparisonReport.analysisBlock(List.of(noisy), CONDITIONS)))
                .contains("различие не подтверждено");
    }

    @Test
    @DisplayName("При значимой разнице замечание о разбросе не печатается")
    void noSpreadNoteWhenDifferenceIsSignificant() {
        assertThat(ComparisonReport.spreadNote(comparison("фильтрация", 6.0, 3.0)))
                .as("разница значима — оправдываться разбросом нечем")
                .isEmpty();
        assertThat(String.join("\n", ComparisonReport.analysisBlock(
                        List.of(comparison("фильтрация", 6.0, 3.0)), CONDITIONS)))
                .doesNotContain("различие не подтверждено");
    }

    @Test
    @DisplayName("Непересекающиеся интервалы: победитель объявляется по среднему")
    void disjointIntervalsGiveWinner() {
        OperationComparison disjoint = new OperationComparison(
                "фильтрация", stats(30, 30, 29, 31), stats(10, 10, 9, 11), true);

        ComparisonReport.Verdict verdict = ComparisonReport.verdict(disjoint);

        assertThat(verdict.winner()).isEqualTo(ComparisonReport.Winner.PARALLEL);
        assertThat(verdict.ratio()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("При проигрыше параллельного варианта названы накладные расходы и число процессоров")
    void explanationNamesOverheadOnLoss() {
        String explanation = ComparisonReport.explanation(comparison("фильтрация", 3.0, 6.0), CONDITIONS);

        assertThat(explanation)
                .contains("накладных расходов")
                .contains("разбиение")
                .contains("синхронизацию")
                .contains("обратную сборку")
                .contains("8")
                .contains("7");
    }

    @Test
    @DisplayName("При выигрыше параллельного варианта сказано, что работа разделена между потоками пула")
    void explanationNamesSplittingOnWin() {
        String explanation = ComparisonReport.explanation(comparison("фильтрация", 6.0, 3.0), CONDITIONS);

        assertThat(explanation)
                .contains("разделена между потоками общего пула")
                .contains("8")
                .contains("7");
    }

    @Test
    @DisplayName("Объяснения при выигрыше и проигрыше различаются")
    void explanationsDifferByOutcome() {
        String onWin = ComparisonReport.explanation(comparison("фильтрация", 6.0, 3.0), CONDITIONS);
        String onLoss = ComparisonReport.explanation(comparison("фильтрация", 3.0, 6.0), CONDITIONS);

        assertThat(onWin).isNotEqualTo(onLoss);
        assertThat(onWin).doesNotContain("накладных расходов");
        assertThat(onLoss).doesNotContain("разделена между потоками общего пула");
    }

    @Test
    @DisplayName("Анализ выводится по каждой из трёх операций, а не одним общим итогом")
    void analysisCoversEveryOperation() {
        List<String> analysis = ComparisonReport.analysisBlock(
                List.of(
                        comparison("фильтрация", 6.0, 3.0),
                        comparison("преобразование", 3.0, 9.0),
                        comparison("агрегация", 5.0, 5.5)),
                CONDITIONS);

        String text = String.join("\n", analysis);

        assertThat(text).contains("фильтрация").contains("преобразование").contains("агрегация");
        assertThat(analysis.stream().filter(line -> line.startsWith("    фильтрация"))).hasSize(1);
        assertThat(analysis.stream().filter(line -> line.startsWith("    преобразование"))).hasSize(1);
        assertThat(analysis.stream().filter(line -> line.startsWith("    агрегация"))).hasSize(1);
    }

    @Test
    @DisplayName("Условия замера содержат все пять обязательных значений")
    void conditionsBlockContainsRequiredValues() {
        String text = String.join("\n", ComparisonReport.conditionsBlock(CONDITIONS));

        assertThat(text)
                .contains("26.0.2.1")
                .contains("доступно процессоров")
                .contains("размер списка")
                .contains("0..1999999")
                .contains("повторов на вариант")
                .contains("наблюдаемое число потоков пула");
    }

    @Test
    @DisplayName("В условия замера попадает только число потоков, без имён — таблица времён читаема")
    void conditionsBlockHasNoThreadNames() {
        String text = String.join("\n", ComparisonReport.conditionsBlock(CONDITIONS));

        assertThat(text)
                .as("имена потоков не должны попадать в условия замера")
                .doesNotContain("commonPool-worker")
                .doesNotContain("http-nio")
                .doesNotContain("Thread-")
                .doesNotContain("main");
        assertThat(text).as("но число потоков присутствует").contains("наблюдаемое число потоков пула");
    }

    @Test
    @DisplayName("«Холодный» прогон печатается отдельным блоком и не смешивается со средними")
    void coldRunIsPrintedSeparately() {
        String text = String.join("\n", ComparisonReport.coldRunBlock(
                List.of(comparison("фильтрация", 6.0, 3.0))));

        assertThat(text)
                .contains("Холодный")
                .contains("в статистику не входит")
                .contains("12.000")
                .contains("6.000");
    }

    @Test
    @DisplayName("Разброс повторов печатается отдельной строкой по каждой операции")
    void spreadIsPrintedOnItsOwnLine() {
        List<String> spread = ComparisonReport.spreadBlock(List.of(
                comparison("фильтрация", 6.0, 3.0),
                comparison("преобразование", 12.0, 4.0)));

        String text = String.join("\n", spread);

        assertThat(text).contains("Разброс повторов");
        assertThat(spread).as("пустая строка, заголовок и строка на каждую операцию").hasSize(4);
        assertThat(text).contains("фильтрация").contains("преобразование");
    }

    @Test
    @DisplayName("Анализ идёт после таблицы времён, а условия — до неё")
    void analysisComesAfterMeasurements() {
        BenchmarkConfig config = new BenchmarkConfig(100_000, 200_000, 1, 1);
        List<Integer> data = RandomListGenerator.generate(config.size());
        BenchmarkOutcome outcome = new BenchmarkService(config).compare(data);
        MeasurementConditions conditions = MeasurementConditions.collect(
                config, ParallelismProbe.observe(
                        () -> StreamOperations.filterEvenParallel(data),
                        () -> StreamOperations.filterEvenSequential(data),
                        2));

        List<String> full = ComparisonReport.fullReport(outcome, conditions);

        int conditionsAt = indexOfLineContaining(full, "Условия замера");
        int tableAt = indexOfLineContaining(full, "Результаты замера");
        int analysisAt = indexOfLineContaining(full, "Анализ причин различия");

        assertThat(conditionsAt).isNotNegative();
        assertThat(tableAt).isGreaterThan(conditionsAt);
        assertThat(analysisAt)
                .as("анализ обязан идти после таблицы времён, а не вместо неё")
                .isGreaterThan(tableAt);
    }

    @Test
    @DisplayName("Полный отчёт содержит сверку результатов обоих вариантов")
    void fullReportContainsResultsCheck() {
        BenchmarkConfig config = new BenchmarkConfig(100_000, 200_000, 1, 1);
        List<Integer> data = RandomListGenerator.generate(config.size());
        BenchmarkOutcome outcome = new BenchmarkService(config).compare(data);

        String text = String.join("\n", ComparisonReport.fullReport(
                outcome,
                MeasurementConditions.collect(config, ParallelismProbe.observe(() -> 0, () -> 0, 1))));

        assertThat(text).contains("Сверка результатов обоих вариантов");
        assertThat(text).doesNotContain("ВНИМАНИЕ");
    }

    @Test
    @DisplayName("Расхождение результатов вариантов помечается предупреждением")
    void mismatchedResultsAreWarnedAbout() {
        BenchmarkOutcome outcome = new BenchmarkOutcome(
                new OperationResults(500, List.of(2, 4), 6),
                new OperationResults(501, List.of(2, 4), 6),
                List.of(new OperationComparison("фильтрация", stats(6, 6, 5, 7), stats(3, 3, 2, 4), false)),
                1,
                5,
                BenchmarkConfig.ofSize(1_000));

        String text = String.join("\n", ComparisonReport.resultsCheckBlock(outcome));

        assertThat(text)
                .contains("ВНИМАНИЕ")
                .contains("500")
                .contains("501");
        assertThat(outcome.resultsMatch()).isFalse();
    }
}
