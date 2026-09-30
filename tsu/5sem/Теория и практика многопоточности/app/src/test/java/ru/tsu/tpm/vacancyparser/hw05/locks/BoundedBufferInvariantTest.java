package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

/**
 * Основной инвариант под многими producer'ами и consumer'ами.
 *
 * <p>Прогон идёт на ёмкости <b>1</b>: буфер мгновенно переходит из «пуст» в «полон» и обратно,
 * поэтому обе стороны постоянно живут в состоянии ожидания и оповещения. Именно там ломается
 * неверный выбор условия или вида оповещения. На большой ёмкости стороны редко сталкиваются, и
 * неправильная реализация может пройти проверку по случайности.
 *
 * <p>Тест повторяется десять раз: гонка проявляется не всегда, и один прогон создал бы ложную
 * уверенность в корректности.
 */
class BoundedBufferInvariantTest {

    private static final int THREADS = 4;
    private static final int ITEMS_PER_PRODUCER = 1_000;
    private static final int EXPECTED = THREADS * ITEMS_PER_PRODUCER;

    @RepeatedTest(10)
    @DisplayName("Ёмкость 1: добавлено ровно столько же, сколько извлечено, буфер пуст")
    @Timeout(120)
    void balancedOnCapacityOne() throws InterruptedException {
        ProducerConsumerRunner.Result result = ProducerConsumerRunner.run(
                new BoundedBuffer<>(1), THREADS, THREADS, ITEMS_PER_PRODUCER);

        assertThat(result.produced()).isEqualTo(EXPECTED);
        assertThat(result.taken()).hasSize(EXPECTED);
        assertThat(result.remainingInBuffer()).isZero();
        assertThat(result.consistent()).isTrue();
    }

    @RepeatedTest(3)
    @DisplayName("Мониторная реализация проходит тот же прогон: инвариант не зависит от механизма")
    @Timeout(120)
    void monitorImplementationIsBalancedToo() throws InterruptedException {
        ProducerConsumerRunner.Result result = ProducerConsumerRunner.run(
                new MonitorBuffer<>(1), THREADS, THREADS, ITEMS_PER_PRODUCER);

        assertThat(result.produced()).isEqualTo(EXPECTED);
        assertThat(result.taken()).hasSize(EXPECTED);
        assertThat(result.consistent()).isTrue();
    }
}
