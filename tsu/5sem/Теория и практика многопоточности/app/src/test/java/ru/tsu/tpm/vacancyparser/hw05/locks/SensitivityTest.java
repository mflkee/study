package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Чувствительность инвариантного теста: он обязан падать на заведомо неверной реализации.
 *
 * <p>Проверка чувствительности обязательна, иначе зелёный тест ничего не доказывает: возможно, он
 * просто не задевает дефект. Здесь берётся реализация с одной подменой — после добавления элемента
 * оповещается <b>не то</b> условие (ожидающие добавления вместо ожидающих извлечения). Всё остальное
 * (ёмкость, FIFO, `lock`/`unlock` в `finally`, цикл `while`) — правильное.
 *
 * <p>Последствие подмены предсказуемо: ожидающий потребитель не получает адресного сигнала, элемент
 * остаётся в буфере, производители упираются в заполненный буфер и в итоге сдают по тайм-ауту.
 * Корректная реализация с теми же параметрами проходит прогон без потерь.
 */
class SensitivityTest {

    private static final int THREADS = 3;
    private static final int ITEMS_PER_PRODUCER = 60;

    @Test
    @DisplayName("Тест на инвариант падает на реализации с оповещением не того условия")
    @Timeout(180)
    void invariantFailsOnWronglySignalledBuffer() throws InterruptedException {
        ProducerConsumerRunner.Result broken =
                ProducerConsumerRunner.run(new WrongSignalBuffer<>(1, 200L), THREADS, THREADS, ITEMS_PER_PRODUCER);

        assertThat(broken.consistent())
                .as("на заведомо неверной реализации инвариант обязан нарушиться, "
                        + "иначе проверка не чувствительна к дефекту: добавлено %d, извлечено %d, осталось %d",
                        broken.produced(), broken.taken().size(), broken.remainingInBuffer())
                .isFalse();
        assertThat(broken.nothingLost())
                .as("часть добавлений сдалась по тайм-ауту: их некому было забрать")
                .isFalse();
    }

    @Test
    @DisplayName("После исправления тот же прогон снова зелёный")
    @Timeout(120)
    void correctImplementationPassesTheSameRun() throws InterruptedException {
        ProducerConsumerRunner.Result correct =
                ProducerConsumerRunner.run(new BoundedBuffer<>(1), THREADS, THREADS, ITEMS_PER_PRODUCER);

        assertThat(correct.consistent())
                .as("исправленная реализация проходит тот же прогон без потерь")
                .isTrue();
        assertThat(correct.produced()).isEqualTo(THREADS * ITEMS_PER_PRODUCER);
        assertThat(correct.taken()).hasSize(THREADS * ITEMS_PER_PRODUCER);
    }

    /**
     * Заведомо неверная реализация: после добавления оповещается условие добавления, а не извлечения.
     *
     * <p>Существует только в тесте — это инструмент проверки чувствительности, а не вариант кода.
     */
    private static final class WrongSignalBuffer<T> implements Buffer<T> {

        private final int capacity;
        private final long timeoutMillis;
        private final Deque<T> storage = new ArrayDeque<>();
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();
        private boolean closed;

        private WrongSignalBuffer(int capacity, long timeoutMillis) {
            this.capacity = capacity;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public void put(T item) {
            if (!offer(item, timeoutMillis)) {
                throw new BufferOperationException(BufferOperationException.Reason.TIMEOUT);
            }
        }

        @Override
        public boolean offer(T item, long timeoutMillis) {
            lock.lock();
            try {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
                while (storage.size() == capacity && !closed) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return false;
                    }
                    await(notFull, remaining);
                }
                if (closed) {
                    throw new BufferOperationException(BufferOperationException.Reason.CLOSED);
                }
                storage.addLast(item);
                // ДЕФЕКТ: оповещается условие добавления вместо условия извлечения.
                notFull.signal();
                return true;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public T take() {
            T item = poll(timeoutMillis);
            if (item == null) {
                throw new BufferOperationException(BufferOperationException.Reason.TIMEOUT);
            }
            return item;
        }

        @Override
        public T poll(long timeoutMillis) {
            lock.lock();
            try {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
                while (storage.isEmpty() && !closed) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return null;
                    }
                    await(notEmpty, remaining);
                }
                if (storage.isEmpty()) {
                    throw new BufferOperationException(BufferOperationException.Reason.CLOSED);
                }
                T item = storage.removeFirst();
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public int size() {
            lock.lock();
            try {
                return storage.size();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public int capacity() {
            return capacity;
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public boolean isFull() {
            return size() == capacity;
        }

        @Override
        public void close() {
            lock.lock();
            try {
                closed = true;
                notFull.signalAll();
                notEmpty.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean isClosed() {
            lock.lock();
            try {
                return closed;
            } finally {
                lock.unlock();
            }
        }

        private void await(Condition condition, long remainingNanos) {
            try {
                condition.await(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BufferOperationException(BufferOperationException.Reason.INTERRUPTED, e);
            }
        }
    }
}
