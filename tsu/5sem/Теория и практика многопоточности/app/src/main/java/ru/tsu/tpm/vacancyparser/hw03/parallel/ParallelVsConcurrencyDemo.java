package ru.tsu.tpm.vacancyparser.hw03.parallel;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

/**
 * Демонстрация ДЗ 3 «Параллелизм vs параллельность».
 *
 * <p>Запускается только при {@code --app.hw=hw03}. Порядок работы: создать список один раз → снять
 * наблюдение о том, сколько потоков общего пула реально работает → замерить три операции в двух
 * вариантах → напечатать условия замера, таблицу времён, разброс, «холодные» прогоны, сверку
 * результатов и анализ причин → завершить приложение.
 *
 * <p>Список создаётся до всего остального и передаётся в замер готовым: так генерация не попадает
 * в измеряемый интервал.
 *
 * <p>Параметры задаются свойствами: {@code app.hw03.size} (по умолчанию 1 000 000),
 * {@code app.hw03.range} (по умолчанию {@code 2 * size}) и {@code app.hw03.repeats}
 * (по умолчанию 5). Фактически применённые значения печатаются в условиях замера.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw03")
public class ParallelVsConcurrencyDemo implements ApplicationRunner {

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;
    private final BenchmarkConfig config;

    public ParallelVsConcurrencyDemo(
            ConsoleOutput console,
            ConfigurableApplicationContext context,
            @Value("${app.hw03.size:" + BenchmarkConfig.DEFAULT_SIZE + "}") int size,
            @Value("${app.hw03.repeats:" + BenchmarkConfig.DEFAULT_REPEATS + "}") int repeats,
            @Value("${app.hw03.range:0}") int rangeUpperBound) {
        this.console = console;
        this.context = context;
        this.config = new BenchmarkConfig(
                size,
                rangeUpperBound > 0 ? rangeUpperBound : RandomListGenerator.upperBound(size),
                repeats,
                BenchmarkConfig.DEFAULT_WARMUP);
    }

    /** Фактически применённые параметры — печатаются в условиях замера и проверяются тестом. */
    public BenchmarkConfig config() {
        return config;
    }

    @Override
    public void run(ApplicationArguments args) {
        console.section("ДЗ 3. Параллелизм vs параллельность");

        console.raw("");
        console.raw("Этап 1. Создание списка случайных чисел (вне измеряемого интервала)");
        List<Integer> data = RandomListGenerator.generate(
                config.size(), config.upperBound(), ThreadLocalRandom.current());
        console.raw("    размер списка: " + data.size()
                + ", диапазон значений: " + config.rangeDescription()
                + ", чётных в списке: " + RandomListGenerator.evenCount(config.size()));

        console.raw("");
        console.raw("Этап 2. Наблюдение: сколько потоков общего пула реально работает");
        ParallelismProbe.Observation observation = ParallelismProbe.observe(
                () -> StreamOperations.filterEvenParallel(data),
                () -> StreamOperations.filterEvenSequential(data));
        console.raw("    за работой застано потоков: " + observation.observedThreads()
                + " (заявленный параллелизм пула: " + observation.poolParallelism() + ")");
        console.raw("    наравне прогреты обе ветви: проходы для наблюдения выполняют и параллельную,");
        console.raw("    и последовательную операцию, иначе JIT прогрел бы параллельную сильнее");

        console.raw("");
        console.raw("");
        console.raw("Этап 3. Замер: 3 операции × 2 варианта × ("
                + config.warmupRuns() + " прогрев + " + config.repeats() + " повторов)");
        BenchmarkOutcome outcome = new BenchmarkService(config).compare(data);

        MeasurementConditions conditions = MeasurementConditions.collect(config, observation);

        console.raw("");
        console.raw("Этап 4. Вывод результатов: условия замера, таблица времён, разброс, «холодные» прогоны");
        print(ComparisonReport.conditionsBlock(conditions));
        print(ComparisonReport.resultsTable(outcome.comparisons()));
        print(ComparisonReport.spreadBlock(outcome.comparisons()));
        print(ComparisonReport.coldRunBlock(outcome.comparisons()));

        console.raw("");
        console.raw("Этап 5. Сверка результатов обоих вариантов");
        print(ComparisonReport.resultsCheckBlock(outcome));
        if (!outcome.resultsMatch()) {
            console.warn("результаты вариантов разошлись — замер недействителен");
        }

        console.raw("");
        console.raw("Этап 6. Анализ причин различия (после измерений)");
        print(ComparisonReport.analysisBlock(outcome.comparisons(), conditions));

        console.raw("");
        console.info("Демонстрация завершена: три операции замерены в обоих вариантах, "
                + "условия замера, таблица времён и анализ причин напечатаны");

        context.close();
    }

    private void print(List<String> lines) {
        lines.forEach(console::raw);
    }
}
