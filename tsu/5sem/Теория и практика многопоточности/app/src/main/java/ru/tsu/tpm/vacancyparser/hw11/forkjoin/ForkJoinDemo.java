package ru.tsu.tpm.vacancyparser.hw11.forkjoin;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.common.ProcessInfo;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.TimeBoxedExecution;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.config.Hw11Properties;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.count.CountSummary;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.count.ForkJoinCounter;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.count.SequentialCounter;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.data.VacancyDataset;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.AggregatedVacancy;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountCriteria;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.report.ForkJoinReport;

/**
 * Демонстрация ДЗ 11 «Fork/Join»: рекурсивный подсчёт агрегированных вакансий партиями.
 *
 * <p>Этапы: подготовка набора (имитация накопленных результатов парсера), последовательный подсчёт как
 * база сравнения, серия прогонов Fork/Join с разными размерами партии и разным параллелизмом, проверка
 * совпадения агрегатов и остановка пула.
 *
 * <p>Запускается только при {@code --app.hw=hw11}. Все числа — фактические замеры на этой машине;
 * размер партии и параллелизм задаются настройками {@code app.hw11.*}.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw11")
public class ForkJoinDemo implements ApplicationRunner {

    /** Предел времени на всю демонстрацию. */
    static final long TIME_LIMIT_MILLIS = 180_000L;

    /** Код выхода, если демонстрация не уложилась в лимит. */
    static final int TIME_LIMIT_EXIT_CODE = 3;

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;
    private final Hw11Properties properties;

    public ForkJoinDemo(ConsoleOutput console, ConfigurableApplicationContext context, Hw11Properties properties) {
        this.console = console;
        this.context = context;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        console.section("ДЗ 11. Fork/Join: рекурсивный подсчёт агрегированных вакансий партиями");
        console.raw("");
        console.raw(ProcessInfo.pidLine());
        console.raw("Настройки ДЗ 11:");
        console.item("размер набора: " + properties.getDatasetSize() + " записей, зерно " + properties.getSeed());
        console.item("критерий: " + properties.criteria().label());
        console.item("размер партии по умолчанию: " + properties.getBatchSize()
                + ", параллелизм: " + properties.getPoolSize());
        console.item("повторов на замер: " + properties.getRepeats() + " (в таблицу попадает лучшее время)");

        TimeBoxedExecution.Outcome outcome;
        try {
            outcome = TimeBoxedExecution.run(TIME_LIMIT_MILLIS, this::runStages);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            console.warn("демонстрация прервана: " + e.getMessage());
            context.close();
            System.exit(TIME_LIMIT_EXIT_CODE);
            return;
        }

        console.raw("");
        if (outcome.completed() && outcome.failure() == null) {
            console.info("Демонстрация завершена: параллельный подсчёт совпал с последовательным, пул остановлен");
        } else if (outcome.failure() != null) {
            console.warn("демонстрация завершилась ошибкой: " + outcome.failure());
        } else {
            console.warn("демонстрация не уложилась в " + TIME_LIMIT_MILLIS + " мс — снимаю дамп потоков");
            console.raw(ThreadDump.capture());
        }

        context.close();
        console.raw("");
        console.raw("живых потоков ДЗ 11 после завершения: "
                + ThreadDump.liveThreadsWithPrefixes(ForkJoinCounter.THREAD_PREFIX));
        if (!(outcome.completed() && outcome.failure() == null)) {
            System.exit(TIME_LIMIT_EXIT_CODE);
        }
    }

    private void runStages() {
        CountCriteria criteria = properties.criteria();

        console.raw("");
        console.raw("Этап 1. Подготовка набора агрегированных записей");
        long start = System.nanoTime();
        List<AggregatedVacancy> data = VacancyDataset.generate(properties.getDatasetSize(), properties.getSeed());
        double generationMillis = (System.nanoTime() - start) / 1_000_000.0;
        console.raw(String.format(
                Locale.ROOT,
                "    сгенерировано %d записей за %.3f мс, городов %d, заголовков %d",
                data.size(), generationMillis, VacancyDataset.CITIES.length, VacancyDataset.TITLES.length));

        console.raw("");
        console.raw("Этап 2. Последовательный подсчёт — база сравнения");
        CountSummary baseline = SequentialCounter.count(data, criteria);
        ForkJoinReport.summaryLines(baseline).forEach(line -> console.raw("    " + line));
        console.raw("    поток: " + baseline.threads().iterator().next());

        console.raw("");
        console.raw("Этап 3. Fork/Join: зависимость от размера партии (параллелизм "
                + properties.getPoolSize() + ")");
        CountSummary best = null;
        try (ForkJoinCounter counter = new ForkJoinCounter(properties.getPoolSize())) {
            console.raw(ForkJoinReport.tableHeader());
            for (int batchSize : properties.getBatchSizes()) {
                CountSummary summary = measure(counter, data, criteria, batchSize);
                console.raw(ForkJoinReport.tableRow(summary, baseline));
                if (best == null || summary.durationNanos() < best.durationNanos()) {
                    best = summary;
                }
            }
        }
        if (best != null) {
            console.raw("");
            console.raw("    " + ForkJoinReport.comparisonLine(baseline, best));
            console.raw("    агрегат совпадает с последовательным: "
                    + marker(ForkJoinReport.sameResult(baseline, best)));
            console.raw("    " + best.reportLine());
        }

        console.raw("");
        console.raw("Этап 4. Fork/Join: зависимость от параллелизма (партия " + properties.getBatchSize() + ")");
        console.raw(ForkJoinReport.tableHeader());
        CountSummary bestParallel = null;
        for (int poolSize : properties.getPoolSizes()) {
            try (ForkJoinCounter counter = new ForkJoinCounter(poolSize)) {
                CountSummary summary = measure(counter, data, criteria, properties.getBatchSize());
                console.raw(ForkJoinReport.tableRow(summary, baseline));
                console.raw("        потоки: " + summary.threads().stream().sorted().toList());
                if (bestParallel == null || summary.durationNanos() < bestParallel.durationNanos()) {
                    bestParallel = summary;
                }
            }
        }
        if (bestParallel != null) {
            console.raw("    " + ForkJoinReport.comparisonLine(baseline, bestParallel));
        }

        console.raw("");
        console.raw("Этап 5. Проверка корректности разбиения");
        CountSummary check = null;
        try (ForkJoinCounter counter = new ForkJoinCounter(properties.getPoolSize())) {
            check = counter.count(data, criteria, properties.getBatchSize());
        }
        ForkJoinReport.summaryLines(check).forEach(line -> console.raw("    " + line));
        console.raw("    партий ровно ceil(" + data.size() + " / " + check.batchSize() + ") = "
                + check.expectedBatches() + ": " + marker(check.batchesAsExpected()));
        console.raw("    агрегат равен последовательному: " + marker(ForkJoinReport.sameResult(baseline, check)));
        console.raw("    задач больше, чем партий, потому что внутренние узлы тоже задачи: "
                + check.tasks() + " против " + check.batches());
        console.raw("    украдено задач пулом за прогон: " + check.steals()
                + " (кража — признак реального параллелизма, а не работы одного потока)");
    }

    /** Несколько повторов, в результат идёт лучший: одиночный замер на JVM шумит. */
    private CountSummary measure(
            ForkJoinCounter counter, List<AggregatedVacancy> data, CountCriteria criteria, int batchSize) {
        CountSummary best = null;
        for (int attempt = 0; attempt < Math.max(1, properties.getRepeats()); attempt++) {
            CountSummary summary = counter.count(data, criteria, batchSize);
            if (best == null || summary.durationNanos() < best.durationNanos()) {
                best = summary;
            }
        }
        return best;
    }

    private static String marker(boolean value) {
        return value ? "да" : "нет";
    }
}
