package ru.tsu.tpm.vacancyparser.hw13.queue.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.UrlTask;

/**
 * Очередь задач: порядок, ограничение ёмкости, обратное давление, сигналы остановки.
 */
class TaskQueueTest {

    @Test
    @Timeout(30)
    @DisplayName("Задачи выдаются в порядке постановки, глубина отслеживается")
    void fifoAndDepth() throws InterruptedException {
        TaskQueue queue = new TaskQueue(4);
        queue.put(new UrlTask("http://one", 1));
        queue.put(new UrlTask("http://two", 2));
        queue.put(new UrlTask("http://three", 3));

        assertThat(queue.depth()).isEqualTo(3);
        assertThat(queue.maxDepth()).isEqualTo(3);
        assertThat(queue.take().pageNumber()).isEqualTo(1);
        assertThat(queue.take().pageNumber()).isEqualTo(2);
        assertThat(queue.take().pageNumber()).isEqualTo(3);
        assertThat(queue.depth()).isZero();
        assertThat(queue.capacity()).isEqualTo(4);
    }

    @Test
    @Timeout(30)
    @DisplayName("Полная очередь блокирует put, и ожидание видно в счётчиках")
    void fullQueueBlocksProducer() throws InterruptedException {
        TaskQueue queue = new TaskQueue(1);
        queue.put(new UrlTask("http://one", 1));

        AtomicBoolean finished = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        Thread producer = new Thread(() -> {
            started.countDown();
            try {
                queue.put(new UrlTask("http://two", 2));
                finished.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "test-producer");
        producer.start();

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100L);
        assertThat(finished).as("put обязан ждать, пока очередь полна").isFalse();
        assertThat(queue.depth()).isEqualTo(1);

        queue.take();
        producer.join(2_000L);

        assertThat(finished).isTrue();
        assertThat(queue.blockedPuts()).isPositive();
        assertThat(queue.blockedPutMillis()).isPositive();
        assertThat(queue.maxDepth()).isEqualTo(1);
    }

    @Test
    @Timeout(30)
    @DisplayName("Сигналы остановки кладутся по числу потребителей и читаются как «таблетки»")
    void poisonPills() throws InterruptedException {
        TaskQueue queue = new TaskQueue(5);
        queue.put(new UrlTask("http://one", 1));
        queue.poisonAll(3);

        assertThat(queue.poisonPills()).isEqualTo(3);
        List<UrlTask> taken = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            taken.add(queue.take());
        }
        assertThat(taken.get(0).isPoison()).isFalse();
        assertThat(taken.subList(1, 4)).allSatisfy(task -> assertThat(task.isPoison()).isTrue());
        assertThat(taken.get(1)).isSameAs(UrlTask.POISON);
    }

    @Test
    @DisplayName("Некорректные параметры отвергаются")
    void invalidParameters() {
        assertThatThrownBy(() -> new TaskQueue(0)).isInstanceOf(IllegalArgumentException.class);
        TaskQueue queue = new TaskQueue(2);
        assertThatThrownBy(() -> queue.put(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.poisonAll(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UrlTask(" ", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Задача описывает себя для журнала")
    void taskDescribesItself() {
        assertThat(new UrlTask("http://x", 7).reportLine()).contains("№7").contains("http://x");
        assertThat(UrlTask.POISON.reportLine()).contains("остановки");
    }
}
