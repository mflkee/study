package ru.tsu.tpm.vacancyparser.hw11.forkjoin.count;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.data.VacancyDataset;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.AggregatedVacancy;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountCriteria;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountResult;

/**
 * Рекурсивная задача: подсчёт листа, разбиение по партиям, отказ некорректных параметров.
 */
class BatchCountTaskTest {

    private static final CountCriteria ANYTHING = new CountCriteria(null, 0, null);

    @Test
    @Timeout(30)
    @DisplayName("Лист считает ровно свой диапазон и игнорирует остальное")
    void leafCountsItsRange() {
        List<AggregatedVacancy> data = VacancyDataset.generate(10, 1L);
        CounterStats stats = new CounterStats();

        BatchCountTask task = new BatchCountTask(data, 3, 7, ANYTHING, 100, stats);

        CountResult result = task.compute();

        assertThat(result.processed()).isEqualTo(4);
        assertThat(result.matched()).isEqualTo(4);
        assertThat(result.salarySum()).isEqualTo(
                data.subList(3, 7).stream().mapToLong(AggregatedVacancy::salary).sum());
        assertThat(stats.batches()).isEqualTo(1);
        assertThat(stats.tasks()).isEqualTo(1);
    }

    @Test
    @Timeout(30)
    @DisplayName("Диапазон больше партии разбивается ровно на ceil(size/batch) листьев")
    void splitsIntoExpectedBatches() {
        List<AggregatedVacancy> data = VacancyDataset.generate(1_005, 7L);
        int batchSize = 100;

        List<Integer> expected = List.of(10, 11, 1);
        assertThat(BatchCountTask.expectedBatches(1_005, batchSize)).isEqualTo(11);
        assertThat(BatchCountTask.expectedBatches(1_000, batchSize)).isEqualTo(10);
        assertThat(BatchCountTask.expectedBatches(0, batchSize)).isZero();
        CounterStats stats = new CounterStats();
        CountResult result;
        try (ForkJoinPool pool = new ForkJoinPool(3)) {
            result = pool.invoke(new BatchCountTask(data, 0, data.size(), ANYTHING, batchSize, stats));
        }

        assertThat(stats.batches()).isEqualTo(11);
        assertThat(stats.tasks()).isEqualTo(21);
        assertThat(result.processed()).isEqualTo(1_005);
        assertThat(result.matched()).isEqualTo(1_005);
        assertThat(result.salarySum()).isEqualTo(data.stream().mapToLong(AggregatedVacancy::salary).sum());
    }

    @Test
    @Timeout(30)
    @DisplayName("Пустой диапазон даёт пустой агрегат и ни одной партии")
    void emptyRange() {
        CounterStats stats = new CounterStats();
        CountResult result = new BatchCountTask(List.of(), 0, 0, ANYTHING, 10, stats).compute();

        assertThat(result).isEqualTo(CountResult.empty());
        assertThat(stats.batches()).isZero();
    }

    @Test
    @Timeout(30)
    @DisplayName("Результаты подзадач складываются без потерь")
    void childrenResultsAreSummed() {
        List<AggregatedVacancy> data = VacancyDataset.generate(500, 3L);
        AtomicReference<CountResult> holder = new AtomicReference<>();
        CounterStats stats = new CounterStats();

        try (ForkJoinPool pool = new ForkJoinPool(2)) {
            holder.set(pool.invoke(new BatchCountTask(data, 0, data.size(), ANYTHING, 50, stats)));
        }

        assertThat(holder.get().processed()).isEqualTo(500);
        assertThat(holder.get().matched()).isEqualTo(500);
        assertThat(holder.get().salarySum()).isEqualTo(data.stream().mapToLong(AggregatedVacancy::salary).sum());
        assertThat(holder.get().maxSalary()).isEqualTo(
                data.stream().mapToInt(AggregatedVacancy::salary).max().orElse(0));
    }

    @Test
    @Timeout(30)
    @DisplayName("Некорректные параметры задачи отвергаются")
    void invalidParametersRejected() {
        List<AggregatedVacancy> data = VacancyDataset.generate(10, 1L);
        CounterStats stats = new CounterStats();

        assertThatThrownBy(() -> new BatchCountTask(null, 0, 0, ANYTHING, 10, stats))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCountTask(data, 0, 5, null, 10, stats))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCountTask(data, 0, 5, ANYTHING, 10, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCountTask(data, 0, 5, ANYTHING, 0, stats))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCountTask(data, -1, 5, ANYTHING, 10, stats))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCountTask(data, 0, 11, ANYTHING, 10, stats))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchCountTask(data, 7, 3, ANYTHING, 10, stats))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BatchCountTask.expectedBatches(10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
