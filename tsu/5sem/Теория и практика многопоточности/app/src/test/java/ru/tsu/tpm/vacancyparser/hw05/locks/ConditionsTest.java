package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Два раздельных условия ожидания и адресное оповещение.
 *
 * <p>Проверяется то, ради чего задание требует {@code Condition}: ожидающие добавления и ожидающие
 * извлечения живут в разных множествах и будятся по своему условию, а не «все сразу».
 */
class ConditionsTest {

    private static final long TIMEOUT_MILLIS = 2_000L;

    /** Достать приватное поле — иначе привязку условий к блокировке не увидеть. */
    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Test
    @DisplayName("Условий ровно два, они различны и получены от одной блокировки")
    void twoDistinctConditionsFromOneLock() throws Exception {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1);

        ReentrantLock lock = (ReentrantLock) field(buffer, "lock");
        Condition notFull = (Condition) field(buffer, "notFull");
        Condition notEmpty = (Condition) field(buffer, "notEmpty");

        assertThat(lock).as("блокировка одна").isNotNull();
        assertThat(notFull).isNotSameAs(notEmpty).isNotNull();
        assertThat(notFull).isInstanceOf(Condition.class);
        assertThat(notEmpty).isInstanceOf(Condition.class);
        assertThat(notFull).isNotSameAs(notEmpty);
        // Привязка обоих условий к этой самой блокировке проверяется поведением в тестах ниже:
        // ожидание на условии чужой блокировки немедленно дало бы IllegalMonitorStateException,
        // а оба теста проходят — значит, условия принадлежат блокировке буфера.
    }

    @Test
    @DisplayName("Добавление в заполненном буфере ёмкости 1 просыпается после извлечения")
    @Timeout(20)
    void producerWakesWhenSpaceFrees() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);
        buffer.put("занял место");

        Thread producer = new Thread(() -> buffer.put("нужно место"), "producer");
        producer.setDaemon(true);
        producer.start();
        Thread.sleep(100L);
        assertThat(producer.getState())
                .as("producer ждёт на условии notFull")
                .isIn(Thread.State.WAITING, Thread.State.TIMED_WAITING);

        assertThat(buffer.take()).isEqualTo("занял место");
        producer.join(5_000L);

        assertThat(producer.isAlive()).isFalse();
        assertThat(buffer.take())
                .as("элемент ожидавшего producer'а действительно попал в буфер")
                .isEqualTo("нужно место");
    }

    @Test
    @DisplayName("Извлечение из пустого буфера просыпается после добавления")
    @Timeout(20)
    void consumerWakesWhenItemAppears() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);
        List<String> taken = new ArrayList<>();

        Thread consumer = new Thread(() -> taken.add(buffer.take()), "consumer");
        consumer.setDaemon(true);
        consumer.start();
        Thread.sleep(100L);
        assertThat(consumer.getState())
                .as("consumer ждёт на условии notEmpty")
                .isIn(Thread.State.WAITING, Thread.State.TIMED_WAITING);

        buffer.put("долгожданный");
        consumer.join(5_000L);

        assertThat(taken).containsExactly("долгожданный");
        assertThat(buffer.size()).isZero();
    }

    @Test
    @DisplayName("Оповещается только сторона, чьё условие выполнено: один элемент — один потребитель")
    @Timeout(20)
    void onlyMatchingSideIsNotified() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);
        int waitingProducers = 3;
        List<Thread> producers = new ArrayList<>();

        buffer.put("заполнил");
        for (int i = 0; i < waitingProducers; i++) {
            Thread producer = new Thread(() -> buffer.put("элемент"), "producer-" + i);
            producer.setDaemon(true);
            producers.add(producer);
            producer.start();
        }
        Thread.sleep(200L);
        assertThat(producers).allSatisfy(producer -> assertThat(producer.isAlive()).isTrue());

        // Одно освободившееся место удовлетворяет ровно одного ожидающего добавления.
        buffer.take();
        Thread.sleep(150L);
        long finished = producers.stream().filter(thread -> !thread.isAlive()).count();

        assertThat(finished)
                .as("адресное оповещение будит одного, а не всех сразу")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("signalAll при закрытии выпускает всех ожидающих, и они завершаются")
    @Timeout(20)
    void closeReleasesEveryWaiter() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(4, TIMEOUT_MILLIS);
        int waiters = 5;
        List<Thread> consumers = new ArrayList<>();
        List<BufferOperationException.Reason> reasons = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < waiters; i++) {
            Thread consumer = new Thread(() -> {
                try {
                    buffer.take();
                } catch (BufferOperationException e) {
                    reasons.add(e.reason());
                }
            }, "consumer-" + i);
            consumer.setDaemon(true);
            consumers.add(consumer);
            consumer.start();
        }
        Thread.sleep(200L);
        assertThat(consumers).allSatisfy(consumer -> assertThat(consumer.isAlive()).isTrue());

        buffer.close();
        for (Thread consumer : consumers) {
            consumer.join(5_000L);
        }

        assertThat(consumers).noneMatch(Thread::isAlive);
        assertThat(reasons)
                .as("причина выхода — закрытие буфера")
                .hasSize(waiters)
                .containsOnly(BufferOperationException.Reason.CLOSED);
    }

    @Test
    @DisplayName("Ожидание отпускает блокировку: другой поток успевает работать, пока первый ждёт")
    @Timeout(20)
    void waitingReleasesTheLock() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);
        buffer.put("заполнил");

        Thread waitingProducer = new Thread(() -> buffer.put("ждёт"), "producer");
        waitingProducer.setDaemon(true);
        waitingProducer.start();
        Thread.sleep(150L);
        assertThat(waitingProducer.getState()).isIn(Thread.State.WAITING, Thread.State.TIMED_WAITING);

        // Если бы ожидающий держал блокировку, этот вызов не вернулся бы никогда.
        String taken = buffer.take();

        assertThat(taken).isEqualTo("заполнил");
        waitingProducer.join(5_000L);
        assertThat(waitingProducer.isAlive()).isFalse();
    }

    @Test
    @DisplayName("Закрытый буфер не принимает новые элементы, но отдаёт оставшиеся")
    void closedBufferRejectsNewWorkButDrainsRemainder() {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(2, TIMEOUT_MILLIS);
        buffer.put("положен до закрытия");
        buffer.close();

        assertThat(buffer.isClosed()).isTrue();
        assertThatExceptionOfType(BufferOperationException.class)
                .isThrownBy(() -> buffer.put("после закрытия"))
                .satisfies(e -> assertThat(e.reason())
                        .isEqualTo(BufferOperationException.Reason.CLOSED));
        assertThat(buffer.take())
                .as("оставшийся элемент обязан быть доступен: закрытие — не потеря данных")
                .isEqualTo("положен до закрытия");
        assertThatExceptionOfType(BufferOperationException.class)
                .isThrownBy(buffer::take)
                .satisfies(e -> assertThat(e.reason())
                        .isEqualTo(BufferOperationException.Reason.CLOSED));
    }
}
