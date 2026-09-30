package ru.tsu.tpm.vacancyparser.hw05.locks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Тот же ёмкостный буфер, но на мониторе объекта — база сравнения (ДЗ 4).
 *
 * <p>Контракт {@link Buffer} тот же самый, логика (FIFO, ёмкость, порядок извлечения) та же.
 * Отличается единственное: механизм ожидания и оповещения.
 *
 * <p><b>Ключевое отличие.</b> У монитора <b>одно</b> множество ожидающих и <b>одно</b> условие.
 * Поэтому адресно разбудить «только извлекающих» или «только добавляющих» физически нельзя:
 * остаётся {@code notifyAll()}, который будит всех, и каждая сторона просыпается, чтобы
 * перепроверить своё условие и — если оно ложно — заснуть снова. Эти лишние пробуждения видны и в
 * коде, и в замерах, и именно из-за них задание требует {@link java.util.concurrent.locks.Condition}.
 *
 * <p>Второе отличие — в дисциплине. Монитор нельзя «забыть отпустить»: синхронизация неявна и
 * снимается при выходе из метода, в том числе при исключении. Здесь нет и не нужно
 * {@code try/finally} — это и есть та простота, ради которой монитор остаётся предпочтительным там,
 * где достаточно одного условия.
 *
 * <p>Этот класс используется только как эталон сравнения. Рабочая реализация ДЗ 5 —
 * {@link BoundedBuffer}.
 *
 * @param <T> тип элемента
 */
public final class MonitorBuffer<T> implements Buffer<T> {

    private final int capacity;
    private final Deque<T> storage = new ArrayDeque<>();
    private final long defaultTimeoutMillis;
    private boolean closed;

    /** Сколько раз ожидающий просыпался и обнаруживал, что его условие всё ещё ложно. */
    private long wastedWakeups;

    public MonitorBuffer(int capacity) {
        this(capacity, DEFAULT_TIMEOUT_MILLIS);
    }

    public MonitorBuffer(int capacity, long defaultTimeoutMillis) {
        if (capacity < 1) {
            throw new IllegalArgumentException("ёмкость буфера должна быть не меньше 1, получено: " + capacity);
        }
        this.capacity = capacity;
        this.defaultTimeoutMillis = defaultTimeoutMillis;
    }

    @Override
    public void put(T item) {
        if (!offer(item, defaultTimeoutMillis)) {
            throw new BufferOperationException(BufferOperationException.Reason.TIMEOUT);
        }
    }

    @Override
    public synchronized boolean offer(T item, long timeoutMillis) {
        Objects.requireNonNull(item, "элемент обязателен");
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (storage.size() == capacity && !closed) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            if (!awaitOn(remaining)) {
                throw new BufferOperationException(BufferOperationException.Reason.INTERRUPTED);
            }
            // Пустым считается только пробуждение, после которого условие всё ещё ложно: сигнал
            // был не для нас. Одно множество ожидающих гарантирует такие пробуждения на каждом
            // событии, два условия — нет.
            if (storage.size() == capacity && !closed) {
                wastedWakeups++;
            }
        }
        if (closed) {
            throw new BufferOperationException(BufferOperationException.Reason.CLOSED);
        }
        storage.addLast(item);
        // Одно множество ожидающих: разбудить только извлекающих нельзя. Будим всех, и ожидающие
        // добавления просыпаются зря — это и есть цена монитора, которую видно в замерах.
        notifyAll();
        return true;
    }

    @Override
    public T take() {
        T item = poll(defaultTimeoutMillis);
        if (item == null) {
            throw new BufferOperationException(BufferOperationException.Reason.TIMEOUT);
        }
        return item;
    }

    @Override
    public synchronized T poll(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (storage.isEmpty() && !closed) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return null;
            }
            if (!awaitOn(remaining)) {
                throw new BufferOperationException(BufferOperationException.Reason.INTERRUPTED);
            }
            if (storage.isEmpty() && !closed) {
                wastedWakeups++;
            }
        }
        if (storage.isEmpty()) {
            throw new BufferOperationException(BufferOperationException.Reason.CLOSED);
        }
        T item = storage.removeFirst();
        notifyAll();
        return item;
    }

    @Override
    public synchronized int size() {
        return storage.size();
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
    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    /** Сколько раз ожидающий просыпался впустую — цена одного множества ожидающих. */
    public synchronized long wastedWakeups() {
        return wastedWakeups;
    }

    /**
     * Ожидание на мониторе с восстановлением признака прерывания после выхода.
     *
     * <p>Название {@code awaitOn} выбрано так же, как у приватного хелпера в {@link BoundedBuffer}:
     * сам {@code wait()} вызывается только отсюда, а отсюда — только изнутри цикла {@code while}
     * с перепроверкой условия. Ни одного ожидания под {@code if} в коде нет.
     *
     * @return {@code false}, если поток был прерван
     */
    private boolean awaitOn(long remainingNanos) {
        try {
            long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
            wait(millis, nanos);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
