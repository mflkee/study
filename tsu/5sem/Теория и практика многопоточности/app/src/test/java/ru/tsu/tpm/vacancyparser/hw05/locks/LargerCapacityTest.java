package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Корректность на большей ёмкости: несколько потоков каждой роли, каждый работает параллельно.
 */
class LargerCapacityTest {

    @Test
    @DisplayName("Ёмкость 8: инварианты держатся, в выводе есть число потоков и число элементов")
    @Timeout(120)
    void largerCapacityKeepsInvariants() throws InterruptedException {
        int capacity = 8;
        int producers = 6;
        int consumers = 6;
        int perProducer = 500;

        ProducerConsumerRunner.Result result =
                ProducerConsumerRunner.run(new BoundedBuffer<>(capacity), producers, consumers, perProducer);

        assertThat(result.capacity()).isEqualTo(capacity);
        assertThat(result.producers()).isEqualTo(producers);
        assertThat(result.consumers()).isEqualTo(consumers);
        assertThat(result.expected()).isEqualTo(producers * perProducer);
        assertThat(result.nothingLost()).isTrue();
        assertThat(result.nothingDuplicated()).isTrue();
        assertThat(result.sameElements()).isTrue();
        assertThat(result.drained()).isTrue();

        String text = String.join("\n", LocksReport.balanceBlock(result));
        assertThat(text)
                .contains("producer'ов: 6")
                .contains("consumer'ов: 6")
                .contains("ёмкость буфера: 8")
                .contains("ожидалось всего: 3000");
    }

    @Test
    @DisplayName("Ёмкость больше числа элементов: producer'ы не ждут места")
    @Timeout(60)
    void capacityLargerThanLoad() throws InterruptedException {
        ProducerConsumerRunner.Result result =
                ProducerConsumerRunner.run(new BoundedBuffer<>(100), 2, 2, 10);

        assertThat(result.consistent()).isTrue();
        assertThat(result.expected()).isEqualTo(20);
    }
}
