package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Чтение счётчика одним потоком во время параллельных увеличений другими.
 *
 * <p>Проверяется, что чтение не требует синхронизации и возвращает одно из фактически достигнутых
 * значений: последовательность прочитанных значений не убывает и не выходит за пределы {@code [0, N×M]}.
 * Точное значение в момент чтения не фиксируется — оно меняется, пока читающий поток работает.
 */
class ReadWhileWritingTest {

    private static final int WRITERS = 4;
    private static final int OPERATIONS_PER_WRITER = 50_000;
    private static final long EXPECTED = (long) WRITERS * OPERATIONS_PER_WRITER;

    @Test
    @Timeout(120)
    @DisplayName("Чтение во время записи даёт неубывающую последовательность в пределах итога")
    void readsAreConsistentWhileWriting() throws InterruptedException {
        AtomicCounter counter = new AtomicCounter();
        AtomicBoolean writing = new AtomicBoolean(true);
        List<Integer> reads = new CopyOnWriteArrayList<>();

        Thread reader = new Thread(() -> {
            while (writing.get()) {
                reads.add(counter.get());
            }
        }, "hw06-counter-reader");
        reader.setDaemon(true);
        reader.start();

        AtomicsBenchmark.runCounterLoad(counter, WRITERS, OPERATIONS_PER_WRITER);
        writing.set(false);
        reader.join(5_000L);

        assertThat(reads)
                .as("пока идут записи, читатель обязан успевать читать значение")
                .isNotEmpty();

        int previous = -1;
        for (int value : reads) {
            assertThat(value)
                    .as("прочитанное значение лежит в достижимом диапазоне [0, %d]", EXPECTED)
                    .isBetween(0, (int) EXPECTED);
            assertThat(value)
                    .as("счётчик только растёт, значит прочитанные значения не убывают: %d после %d", value, previous)
                    .isGreaterThanOrEqualTo(previous);
            previous = value;
        }

        assertThat(counter.get())
                .as("после всех увеличений итог обязан быть точным")
                .isEqualTo((int) EXPECTED);
    }
}
