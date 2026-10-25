package ru.tsu.tpm.vacancyparser.hw11.forkjoin.count;

import java.util.List;
import java.util.concurrent.RecursiveTask;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.AggregatedVacancy;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountCriteria;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountResult;

/**
 * Рекурсивная задача подсчёта: диапазон разбивается на партии, каждая партия — отдельная задача.
 *
 * <p><b>Разбиение по партиям, а не «пополам по индексу».</b> Граница делится так, чтобы обе половины
 * содержали целое число партий: {@code mid = from + (партий слева) * batchSize}. При делении пополам
 * «как получится» листья выходят произвольного размера (62, 63, 125…), и утверждение «каждая пачка —
 * новая задача» перестаёт быть проверяемым. При выравнивании по партиям листьев ровно
 * {@code ceil(size / batchSize)}, и это число проверяется тестом.
 *
 * <p>Каждый лист ({@code size <= batchSize}) считает свою партию последовательно и возвращает
 * {@link CountResult}; внутренние узлы складывают результаты детей. Сложение ассоциативно, поэтому
 * порядок завершения подзадач на ответ не влияет — обязательное условие корректности Fork/Join.
 *
 * <p>Класс не хранит изменяемого состояния, кроме атомарной статистики: одна и та же задача может
 * выполняться разными потоками, и «украденная» подзадача считается тем потоком, который её взял.
 */
public final class BatchCountTask extends RecursiveTask<CountResult> {

    private final List<AggregatedVacancy> data;
    private final int from;
    private final int to;
    private final CountCriteria criteria;
    private final int batchSize;
    private final CounterStats stats;

    /**
     * @param data      весь набор (общий для всех подзадач, только чтение)
     * @param from      начало диапазона включительно
     * @param to        конец диапазона исключительно
     * @param criteria  критерий подсчёта
     * @param batchSize максимальный размер партии
     * @param stats     атомарная статистика выполнения
     */
    public BatchCountTask(
            List<AggregatedVacancy> data,
            int from,
            int to,
            CountCriteria criteria,
            int batchSize,
            CounterStats stats) {
        if (data == null || criteria == null || stats == null) {
            throw new IllegalArgumentException("набор, критерий и статистика обязательны");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("размер партии должен быть положительным, получено " + batchSize);
        }
        if (from < 0 || to > data.size() || from > to) {
            throw new IllegalArgumentException(
                    "некорректный диапазон [" + from + ", " + to + ") для набора из " + data.size());
        }
        this.data = data;
        this.from = from;
        this.to = to;
        this.criteria = criteria;
        this.batchSize = batchSize;
        this.stats = stats;
        stats.taskCreated();
    }

    @Override
    protected CountResult compute() {
        stats.threadUsed();
        int size = to - from;
        if (size == 0) {
            return CountResult.empty();
        }
        if (size <= batchSize) {
            stats.batchStarted();
            return countBatch();
        }

        int batches = (size + batchSize - 1) / batchSize;
        int leftBatches = batches / 2;
        int mid = from + leftBatches * batchSize;

        BatchCountTask left = new BatchCountTask(data, from, mid, criteria, batchSize, stats);
        BatchCountTask right = new BatchCountTask(data, mid, to, criteria, batchSize, stats);

        left.fork();
        CountResult rightResult = right.compute();
        CountResult leftResult = left.join();
        return leftResult.plus(rightResult);
    }

    /** Последовательный подсчёт одной партии. */
    private CountResult countBatch() {
        long matched = 0;
        long remote = 0;
        long salarySum = 0;
        int maxSalary = 0;
        for (int index = from; index < to; index++) {
            AggregatedVacancy vacancy = data.get(index);
            if (!criteria.matches(vacancy)) {
                continue;
            }
            matched++;
            if (vacancy.remote()) {
                remote++;
            }
            salarySum += vacancy.salary();
            maxSalary = Math.max(maxSalary, vacancy.salary());
        }
        return new CountResult(to - from, matched, remote, salarySum, maxSalary);
    }

    /** Ожидаемое число партий для диапазона — используется в проверках и отчёте. */
    public static int expectedBatches(int size, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("размер партии должен быть положительным");
        }
        return (size + batchSize - 1) / batchSize;
    }
}
