package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

/**
 * Сквозной инвариант счётчика: N потоков × M увеличений дают ровно N × M.
 *
 * <p>Формулировка заимствована из ДЗ 4 без изменений — тот же инвариант, тот же размер нагрузки
 * (8 потоков × 10 000) и те же десять повторов. Один и тот же критерий, применённый к новому
 * механизму, и есть содержание этого ДЗ: замена монитора на атомарный тип не меняет инвариант.
 */
class AtomicCounterInvariantTest {

    private static final int THREADS = 8;
    private static final int OPERATIONS_PER_THREAD = 10_000;
    private static final long EXPECTED = (long) THREADS * OPERATIONS_PER_THREAD;

    @RepeatedTest(10)
    @DisplayName("8 потоков × 10 000 увеличений: итог равен 80 000 в каждом прогоне")
    @Timeout(120)
    void invariantHoldsOnEveryRun() throws InterruptedException {
        AtomicCounter counter = new AtomicCounter();
        long value = AtomicsBenchmark.runCounterInvariant(counter, THREADS, OPERATIONS_PER_THREAD);

        assertThat(value)
                .as("атомарный счётчик не имеет права терять обновления")
                .isEqualTo(EXPECTED);
    }
}
