package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Главный инвариант курса: N потоков, каждый делает по M увеличений, итог обязан быть ровно N × M.
 *
 * <p>Тест повторяется, а не выполняется один раз, потому что гонка данных проявляется не всегда:
 * на быстрой машине незащищённый {@code value++} иногда «случайно» даёт правильный ответ, и один
 * прогон создал бы ложную уверенность в корректности. Десять повторов — способ отличить «работает»
 * от «иногда работает».
 */
class DataCollectorInvariantTest {

    private static final int THREADS = 8;
    private static final int INCREMENTS = 10_000;
    private static final long EXPECTED = (long) THREADS * INCREMENTS;

    /** Сколько раз пытаться поймать потерю обновлений на незащищённом счётчике. */
    private static final int UNPROTECTED_ATTEMPTS = 20;

    @RepeatedTest(10)
    @DisplayName("Защищённый счётчик: N потоков × M увеличений дают ровно N × M")
    void synchronizedCounterKeepsEveryUpdate() throws InterruptedException {
        DataCollector collector = new DataCollector();

        ConcurrentLoad.run(THREADS, () -> {
            for (int i = 0; i < INCREMENTS; i++) {
                collector.incrementProcessed();
            }
        });

        assertThat(collector.processedCount())
                .as("монитор обязан сохранить все обновления: %d потоков × %d", THREADS, INCREMENTS)
                .isEqualTo(EXPECTED);
    }

    @Test
    @DisplayName("То же утверждение на незащищённой реализации падает: обновления теряются")
    void unsynchronizedCounterBreaksTheSameInvariant() throws InterruptedException {
        List<Long> observed = new ArrayList<>();
        boolean lostUpdates = false;

        for (int attempt = 0; attempt < UNPROTECTED_ATTEMPTS && !lostUpdates; attempt++) {
            Counter counter = Protection.OFF.newCounter();
            ConcurrentLoad.run(THREADS, () -> {
                for (int i = 0; i < INCREMENTS; i++) {
                    counter.increment();
                }
            });
            long value = counter.value();
            observed.add(value);
            lostUpdates = value < EXPECTED;
        }

        assertThat(lostUpdates)
                .as("на незащищённом счётчике то же самое утверждение «счётчик == %d» обязано нарушиться; "
                        + "наблюдённые значения: %s", EXPECTED, observed)
                .isTrue();
        assertThat(observed)
                .as("фактическое расхождение выводится для отчёта")
                .anySatisfy(value -> assertThat(value).isLessThan(EXPECTED));
    }

    @Test
    @DisplayName("Без защиты теряется не одно, а много обновлений — это не единичный сбой")
    void unsynchronizedLossIsSystematic() throws InterruptedException {
        long worstLoss = 0L;

        for (int attempt = 0; attempt < 5; attempt++) {
            Counter counter = Protection.OFF.newCounter();
            ConcurrentLoad.run(THREADS, () -> {
                for (int i = 0; i < INCREMENTS; i++) {
                    counter.increment();
                }
            });
            worstLoss = Math.max(worstLoss, EXPECTED - counter.value());
        }

        assertThat(worstLoss)
                .as("потеря обновлений — систематический эффект, а не случайная флуктуация")
                .isGreaterThan(1_000L);
    }
}
