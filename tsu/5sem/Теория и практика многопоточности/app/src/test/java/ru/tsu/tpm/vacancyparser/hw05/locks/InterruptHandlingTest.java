package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Обработка прерывания ожидания.
 *
 * <p>Проверяется три вещи, каждая из которых легко делается неправильно: блокировка отпускается
 * (иначе буфер мёртв), операция прекращается (а не возвращается в цикл ожидания), и признак
 * прерывания остаётся выставленным (иначе вызывающий код никогда не узнает, что поток остановили).
 */
class InterruptHandlingTest {

    private static final long TIMEOUT_MILLIS = 5_000L;

    @Test
    @Timeout(30)
    @DisplayName("Producer, прерванный в заполненном буфере, выходит из операции и завершается")
    void interruptedProducerFinishes() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);
        buffer.put("заполнил");
        AtomicReference<BufferOperationException.Reason> reason = new AtomicReference<>();

        Thread producer = new Thread(() -> {
            try {
                buffer.put("не пройдёт");
            } catch (BufferOperationException e) {
                reason.set(e.reason());
            }
        }, "producer");
        producer.setDaemon(true);
        producer.start();
        Thread.sleep(150L);
        assertThat(producer.getState()).isIn(Thread.State.WAITING, Thread.State.TIMED_WAITING);

        producer.interrupt();
        producer.join(5_000L);

        assertThat(producer.isAlive()).as("прерванная операция обязана завершиться").isFalse();
        assertThat(reason.get())
                .as("причина названа явно")
                .isEqualTo(BufferOperationException.Reason.INTERRUPTED);
    }

    @Test
    @Timeout(30)
    @DisplayName("Consumer, прерванный в пустом буфере, не остаётся в ожидании")
    void interruptedConsumerFinishes() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);
        AtomicReference<BufferOperationException.Reason> reason = new AtomicReference<>();

        Thread consumer = new Thread(() -> {
            try {
                buffer.take();
            } catch (BufferOperationException e) {
                reason.set(e.reason());
            }
        }, "consumer");
        consumer.setDaemon(true);
        consumer.start();
        Thread.sleep(150L);
        assertThat(consumer.getState()).isIn(Thread.State.WAITING, Thread.State.TIMED_WAITING);

        consumer.interrupt();
        consumer.join(5_000L);

        assertThat(consumer.isAlive()).isFalse();
        assertThat(reason.get()).isEqualTo(BufferOperationException.Reason.INTERRUPTED);
    }

    @Test
    @Timeout(30)
    @DisplayName("Признак прерывания сохраняется: тест упал бы, если его сбросить молча")
    void interruptFlagIsPreserved() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);
        buffer.put("заполнил");
        AtomicBoolean flagAfterReturn = new AtomicBoolean();

        Thread producer = new Thread(() -> {
            try {
                buffer.put("не пройдёт");
            } catch (BufferOperationException e) {
                // Момент истины: обработчик обязан был восстановить признак до выхода наружу.
                flagAfterReturn.set(Thread.currentThread().isInterrupted());
            }
        }, "producer");
        producer.setDaemon(true);
        producer.start();
        Thread.sleep(150L);
        producer.interrupt();
        producer.join(5_000L);

        assertThat(flagAfterReturn.get())
                .as("признак прерывания обязан остаться выставленным: «проглоченное» прерывание — "
                        + "это сброшенный флаг, и поток продолжил бы работу как ни в чём не бывало")
                .isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("После прерывания одного потока буфер жив, остальные заканчивают штатно")
    void bufferSurvivesInterruptOfOneThread() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);
        List<String> taken = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicReference<BufferOperationException.Reason> interruptReason = new AtomicReference<>();

        Thread interruptedConsumer = new Thread(() -> {
            try {
                buffer.take();
            } catch (BufferOperationException e) {
                interruptReason.set(e.reason());
            }
        }, "consumer-interrupted");
        interruptedConsumer.setDaemon(true);
        interruptedConsumer.start();
        Thread.sleep(150L);

        Thread survivor = new Thread(() -> {
            try {
                String item = buffer.poll(TIMEOUT_MILLIS);
                if (item != null) {
                    taken.add(item);
                }
            } catch (BufferOperationException ignored) {
                // в этом сценарии не ожидается
            }
        }, "consumer-survivor");
        survivor.setDaemon(true);
        survivor.start();

        interruptedConsumer.interrupt();
        // Важно дождаться полного выхода прерванного потока из очереди ожидающих, прежде чем класть
        // элемент. Причина не в осторожности, а в свойстве самого механизма: signal() будит ОДНОГО
        // ожидающего на условии, и если прерванный ещё не покинул очередь, сигнал достанется ему, а
        // не выжившему. Это то же ограничение адресности, что и у монитора, только внутри одного
        // условия; задача 4.4 требует другого — чтобы буфер остался работоспособен и остальные
        // потоки завершились штатно.
        interruptedConsumer.join(5_000L);
        assertThat(interruptedConsumer.isAlive())
                .as("прерванный потребитель обязан полностью завершиться")
                .isFalse();

        buffer.put("дошло до выжившего");
        survivor.join(5_000L);

        assertThat(interruptedConsumer.isAlive()).isFalse();
        assertThat(survivor.isAlive()).as("второй поток не пострадал от прерывания первого").isFalse();
        assertThat(interruptReason.get()).isEqualTo(BufferOperationException.Reason.INTERRUPTED);
        assertThat(taken)
                .as("буфер продолжил работать: элемент дошёл до второго потребителя")
                .containsExactly("дошло до выжившего");
        assertThat(buffer.size()).isZero();
    }

    @Test
    @Timeout(30)
    @DisplayName("Мониторная реализация ведёт себя так же: прерывание и признак сохраняются")
    void monitorBufferHandlesInterruptTheSameWay() throws InterruptedException {
        MonitorBuffer<String> buffer = new MonitorBuffer<>(1, TIMEOUT_MILLIS);
        buffer.put("заполнил");
        AtomicBoolean flag = new AtomicBoolean();
        AtomicReference<BufferOperationException.Reason> reason = new AtomicReference<>();

        Thread producer = new Thread(() -> {
            try {
                buffer.put("не пройдёт");
            } catch (BufferOperationException e) {
                reason.set(e.reason());
                flag.set(Thread.currentThread().isInterrupted());
            }
        }, "producer");
        producer.setDaemon(true);
        producer.start();
        Thread.sleep(150L);
        producer.interrupt();
        producer.join(5_000L);

        assertThat(producer.isAlive()).isFalse();
        assertThat(reason.get()).isEqualTo(BufferOperationException.Reason.INTERRUPTED);
        assertThat(flag.get()).isTrue();
    }
}
