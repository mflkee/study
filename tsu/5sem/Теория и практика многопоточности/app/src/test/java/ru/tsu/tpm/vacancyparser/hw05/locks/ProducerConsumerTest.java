package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Задачи producer'а и consumer'а: корректное завершение и совпадение счётчиков на малом объёме.
 */
class ProducerConsumerTest {

    @Test
    @DisplayName("Задачи завершаются, счётчики добавленного и извлечённого совпадают")
    @Timeout(60)
    void tasksFinishAndCountersMatch() throws InterruptedException {
        ProducerConsumerRunner.Result result =
                ProducerConsumerRunner.run(new BoundedBuffer<>(2), 2, 2, 50);

        assertThat(result.produced()).isEqualTo(100);
        assertThat(result.taken()).hasSize(100);
        assertThat(result.remainingInBuffer()).isZero();
        assertThat(result.perConsumer().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(100);
        assertThat(result.nothingLost()).isTrue();
        assertThat(result.elapsedNanos()).isPositive();
    }

    @Test
    @DisplayName("Один producer и один consumer на ёмкости 1: минимальный рабочий сценарий")
    @Timeout(60)
    void singleProducerAndConsumer() throws InterruptedException {
        ProducerConsumerRunner.Result result =
                ProducerConsumerRunner.run(new BoundedBuffer<>(1), 1, 1, 20);

        assertThat(result.produced()).isEqualTo(20);
        assertThat(result.taken()).hasSize(20);
        assertThat(result.consistent()).isTrue();
    }

    @Test
    @DisplayName("Требуется хотя бы один producer и один consumer, и хотя бы один элемент на producer")
    void invalidLoadIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProducerConsumerRunner.run(new BoundedBuffer<>(1), 0, 1, 10))
                .withMessageContaining("хотя бы");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProducerConsumerRunner.run(new BoundedBuffer<>(1), 1, 0, 10))
                .withMessageContaining("хотя бы");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProducerConsumerRunner.run(new BoundedBuffer<>(1), 1, 1, 0))
                .withMessageContaining("хотя бы");
    }
}
