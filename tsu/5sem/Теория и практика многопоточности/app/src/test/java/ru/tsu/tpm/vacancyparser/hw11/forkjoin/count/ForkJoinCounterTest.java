package ru.tsu.tpm.vacancyparser.hw11.forkjoin.count;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.data.VacancyDataset;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.AggregatedVacancy;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountCriteria;

/**
 * Параллельный подсчёт: совпадение с последовательным, число партий, имена потоков, остановка пула.
 */
class ForkJoinCounterTest {

    private static final CountCriteria TOMSK_JAVA = new CountCriteria("Томск", 100_000, "java");

    @Test
    @Timeout(60)
    @DisplayName("Для всех размеров и партий результат совпадает с последовательным")
    void matchesSequentialForAllShapes() {
        for (int size : List.of(0, 1, 2, 7, 100, 1_001)) {
            List<AggregatedVacancy> data = VacancyDataset.generate(size, 11L);
            CountSummary sequential = SequentialCounter.count(data, TOMSK_JAVA);
            for (int batchSize : List.of(1, 3, 10, 100, 5_000)) {
                try (ForkJoinCounter counter = new ForkJoinCounter(3)) {
                    CountSummary parallel = counter.count(data, TOMSK_JAVA, batchSize);
                    assertThat(parallel.result())
                            .as("размер %d, партия %d", size, batchSize)
                            .isEqualTo(sequential.result());
                    assertThat(parallel.batches())
                            .as("партий при размере %d и партии %d", size, batchSize)
                            .isEqualTo(BatchCountTask.expectedBatches(size, batchSize));
                }
            }
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Задач в дереве вдвое больше партий минус один, лишних партий нет")
    void taskTreeMatchesBatches() {
        List<AggregatedVacancy> data = VacancyDataset.generate(10_000, 5L);
        try (ForkJoinCounter counter = new ForkJoinCounter(4)) {
            CountSummary summary = counter.count(data, TOMSK_JAVA, 250);

            assertThat(summary.batches()).isEqualTo(40);
            assertThat(summary.tasks()).isEqualTo(2 * 40 - 1);
            assertThat(summary.batchesAsExpected()).isTrue();
            assertThat(summary.parallelism()).isEqualTo(4);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Подсчёт идёт на именованных демон-потоках пула, не больше параллелизма")
    void usesNamedPoolThreads() {
        List<AggregatedVacancy> data = VacancyDataset.generate(20_000, 8L);
        try (ForkJoinCounter counter = new ForkJoinCounter(2)) {
            CountSummary summary = counter.count(data, TOMSK_JAVA, 100);

            assertThat(summary.threads()).isNotEmpty();
            assertThat(summary.threads()).allSatisfy(name ->
                    assertThat(name).startsWith(ForkJoinCounter.THREAD_PREFIX));
            assertThat(summary.threadCount()).isLessThanOrEqualTo(2);
            assertThat(summary.steals()).isGreaterThanOrEqualTo(0L);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Пустой набор даёт пустой агрегат и нулевые партии")
    void emptyDataset() {
        try (ForkJoinCounter counter = new ForkJoinCounter(2)) {
            CountSummary summary = counter.count(List.of(), TOMSK_JAVA, 100);

            assertThat(summary.result()).isEqualTo(ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountResult.empty());
            assertThat(summary.batches()).isZero();
            assertThat(summary.expectedBatches()).isZero();
            assertThat(summary.threads()).hasSize(1).allSatisfy(name ->
                    assertThat(name).startsWith(ForkJoinCounter.THREAD_PREFIX));
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("close() останавливает пул, повторный вызов безопасен")
    void closeStopsPool() {
        ForkJoinCounter counter = new ForkJoinCounter(3);
        counter.count(VacancyDataset.generate(1_000, 1L), TOMSK_JAVA, 100);

        counter.close();
        counter.close();

        assertThat(counter.isTerminated()).isTrue();
        assertThat(ThreadDump.liveThreadsWithPrefixes(ForkJoinCounter.THREAD_PREFIX)).isEmpty();
    }

    @Test
    @Timeout(60)
    @DisplayName("Некорректные параметры пула и подсчёта отвергаются")
    void invalidParametersRejected() {
        assertThatThrownBy(() -> new ForkJoinCounter(0)).isInstanceOf(IllegalArgumentException.class);
        List<AggregatedVacancy> data = VacancyDataset.generate(10, 1L);
        try (ForkJoinCounter counter = new ForkJoinCounter(2)) {
            assertThatThrownBy(() -> counter.count(null, TOMSK_JAVA, 10))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> counter.count(data, null, 10))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> counter.count(data, TOMSK_JAVA, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Последовательный вариант помечает себя одним потоком и одной партией")
    void sequentialSummaryDescribesItself() {
        List<AggregatedVacancy> data = VacancyDataset.generate(100, 2L);

        CountSummary summary = SequentialCounter.count(data, TOMSK_JAVA);

        assertThat(summary.parallelism()).isEqualTo(1);
        assertThat(summary.batches()).isEqualTo(1);
        assertThat(summary.tasks()).isEqualTo(1);
        assertThat(summary.steals()).isZero();
        assertThat(summary.threads()).containsExactly(Thread.currentThread().getName());
        assertThat(summary.durationNanos()).isPositive();
        assertThat(summary.reportLine()).contains("последовательно").contains("партий 1");
    }

    @Test
    @Timeout(60)
    @DisplayName("Критерий действительно фильтрует: без фильтра подходит больше записей")
    void criteriaNarrowsResult() {
        List<AggregatedVacancy> data = VacancyDataset.generate(5_000, 4L);
        CountCriteria anything = new CountCriteria(null, 0, null);

        CountSummary all = SequentialCounter.count(data, anything);
        CountSummary filtered = SequentialCounter.count(data, TOMSK_JAVA);

        assertThat(all.result().matched()).isEqualTo(data.size());
        assertThat(filtered.result().matched()).isLessThan(all.result().matched()).isPositive();
        assertThat(filtered.result().salarySum()).isLessThan(all.result().salarySum());
        assertThat(filtered.result().averageSalary()).isPositive();
    }

    /** Проверка, что выборка не пустая на большом наборе — иначе сравнение не показательно. */
    @Test
    @Timeout(60)
    @DisplayName("На большом наборе критерий находит заметное число записей")
    void largeDatasetHasMatches() {
        List<AggregatedVacancy> data = VacancyDataset.generate(200_000, 2026L);
        CountSummary summary = SequentialCounter.count(data, TOMSK_JAVA);

        assertThat(summary.result().matched())
                .isGreaterThan(1_000L)
                .isLessThan((long) data.size());
    }
}
