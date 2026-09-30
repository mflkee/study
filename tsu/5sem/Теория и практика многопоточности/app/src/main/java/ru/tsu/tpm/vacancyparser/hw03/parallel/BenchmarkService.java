package ru.tsu.tpm.vacancyparser.hw03.parallel;

import java.util.List;
import org.springframework.stereotype.Service;
import ru.tsu.tpm.vacancyparser.common.Measurer;
import ru.tsu.tpm.vacancyparser.common.TimingResult;

/**
 * Единая точка входа для замера: принимает **один и тот же** список и прогоняет по нему все три
 * операции в порядке из текста задания — фильтрация → преобразование → агрегация — в обоих
 * вариантах исполнения.
 *
 * <p>Список приходит снаружи, а не создаётся внутри. Это и есть способ не пустить генерацию в
 * измеряемый интервал: генератор здесь просто не вызывается, его нет в зависимостях класса.
 *
 * <p>Класс ничего не печатает. Печать внутри измеряемого участка исказила бы замер (ввод-вывод
 * дороже самой операции), а вывод анализа обязан идти после измерений, поэтому весь вывод собран в
 * отдельном классе отчёта.
 */
@Service
public class BenchmarkService {

    private final BenchmarkConfig config;

    /** Сервис с параметрами из текста задания. */
    public BenchmarkService() {
        this(BenchmarkConfig.defaults());
    }

    public BenchmarkService(BenchmarkConfig config) {
        this.config = config;
    }

    /** Параметры, с которыми работает сервис. */
    public BenchmarkConfig config() {
        return config;
    }

    /**
     * Замерить все три операции обоих вариантов на переданном списке.
     *
     * @param data тот же список для всех шести замеров; он не копируется и не пересоздаётся
     */
    public BenchmarkOutcome compare(List<Integer> data) {
        int warmup = config.warmupRuns();
        int repeats = config.repeats();

        // Порядок операций — как в тексте задания: фильтрация, преобразование, агрегация.
        TimingResult<Long> evenSequential =
                Measurer.measure(warmup, repeats, () -> StreamOperations.filterEvenSequential(data));
        TimingResult<Long> evenParallel =
                Measurer.measure(warmup, repeats, () -> StreamOperations.filterEvenParallel(data));

        TimingResult<List<Integer>> doubledSequential =
                Measurer.measure(warmup, repeats, () -> StreamOperations.doubleSequential(data));
        TimingResult<List<Integer>> doubledParallel =
                Measurer.measure(warmup, repeats, () -> StreamOperations.doubleParallel(data));

        TimingResult<Long> sumSequential =
                Measurer.measure(warmup, repeats, () -> StreamOperations.sumSequential(data));
        TimingResult<Long> sumParallel =
                Measurer.measure(warmup, repeats, () -> StreamOperations.sumParallel(data));

        OperationResults sequentialResults = new OperationResults(
                evenSequential.result(), doubledSequential.result(), sumSequential.result());
        OperationResults parallelResults = new OperationResults(
                evenParallel.result(), doubledParallel.result(), sumParallel.result());

        List<OperationComparison> comparisons = List.of(
                new OperationComparison(
                        "фильтрация",
                        OperationComparison.TimingStats.of(evenSequential),
                        OperationComparison.TimingStats.of(evenParallel),
                        sequentialResults.evenCount() == parallelResults.evenCount()),
                new OperationComparison(
                        "преобразование",
                        OperationComparison.TimingStats.of(doubledSequential),
                        OperationComparison.TimingStats.of(doubledParallel),
                        sequentialResults.doubled().equals(parallelResults.doubled())),
                new OperationComparison(
                        "агрегация",
                        OperationComparison.TimingStats.of(sumSequential),
                        OperationComparison.TimingStats.of(sumParallel),
                        sequentialResults.sum() == parallelResults.sum()));

        return new BenchmarkOutcome(
                sequentialResults, parallelResults, comparisons, warmup, repeats, config);
    }
}
