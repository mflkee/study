package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Дефект {@code volatile}-счётчика: составная операция {@code count++} теряет обновления.
 *
 * <p>Это «красный» прогон: на учебном неверном варианте инвариант «N потоков → ровно N» нарушается.
 * Тест не падает только потому, что он <b>ожидает</b> расхождения — это и есть проверка
 * чувствительности (как в ДЗ 4 и ДЗ 5). Тот же прогон на атомарном счётчике обязан быть точным.
 */
class VolatileNotAtomicTest {

    private static final int THREADS = 8;
    private static final int OPERATIONS_PER_THREAD = 100_000;
    private static final long EXPECTED = (long) THREADS * OPERATIONS_PER_THREAD;
    private static final int RUNS = 5;

    @Test
    @Timeout(180)
    @DisplayName("volatile-счётчик теряет обновления во всех прогонах — дефект воспроизводится")
    void brokenVolatileCounterLosesUpdates() throws InterruptedException {
        for (int run = 1; run <= RUNS; run++) {
            VolatileCounterBroken counter = new VolatileCounterBroken();
            long value = AtomicsBenchmark.runCounterInvariant(counter, THREADS, OPERATIONS_PER_THREAD);

            assertThat(value)
                    .as("прогон %d: volatile-счётчик = %d из %d, потеряно %d — "
                                    + "volatile даёт видимость, но не атомарность",
                            run, value, EXPECTED, EXPECTED - value)
                    .isLessThan(EXPECTED);
        }
    }

    @Test
    @Timeout(120)
    @DisplayName("Тот же прогон на AtomicInteger точен: дефект именно в отсутствии атомарности")
    void atomicCounterIsExactOnTheSameLoad() throws InterruptedException {
        AtomicCounter counter = new AtomicCounter();
        long value = AtomicsBenchmark.runCounterInvariant(counter, THREADS, OPERATIONS_PER_THREAD);

        assertThat(value).isEqualTo(EXPECTED);
    }
}
