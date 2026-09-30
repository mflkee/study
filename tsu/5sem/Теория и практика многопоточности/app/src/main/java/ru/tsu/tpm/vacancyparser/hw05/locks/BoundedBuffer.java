package ru.tsu.tpm.vacancyparser.hw05.locks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ёмкостный буфер (FIFO) на явной блокировке с двумя раздельными условиями ожидания.
 *
 * <p><b>Зачем два условия, а не одно.</b> У монитора объекта один набор ожидающих и одно условие:
 * разбудить «правильного» ожидающего на нём нельзя. Здесь условий два, и оповещается только та
 * сторона, чьё условие стало истинным: добавление элемента будит ожидающих <i>извлечения</i>,
 * освобождение места — ожидающих <i>добавления</i>. Это единственная причина, по которой задание
 * требует {@link Condition}.
 *
 * <p><b>Почему условие проверяется в цикле {@code while}.</b> {@link Condition#await()} возвращает
 * управление без гарантии, что условие стало истинным: сигнал не адресный в смысле «причина», поток
 * может проснуться от чужого события, а между проверкой условия и засыпанием есть окно, в которое
 * состояние меняется. Цикл заставляет перепроверить условие и заснуть снова, если ждать всё ещё
 * нужно. С {@code if} это классический lost wakeup.
 *
 * <p><b>Почему блокировка отпускается до {@code await()}.</b> Её отпускает сам {@code await()}
 * атомарно вместе с переходом в ожидание. Попытка захватить блокировку после {@code await()} или
 * обернуть ожидание во внешнюю блокировку приводит к тому, что буфер блокируется намертво: никто не
 * может ни добавить, ни извлечь, потому что единственная блокировка занята ожидающим.
 *
 * <p><b>Почему {@code unlock()} в {@code finally}.</b> У монитора синхронизация неявна, и забыть её
 * нельзя. Явная блокировка такой защиты не даёт: любой {@code return} или исключение на пути выхода
 * оставит её захваченной, и буфер станет мёртвым до перезапуска процесса. Поэтому все операции
 * построены как {@code lock(); try { ... } finally { unlock(); }}.
 *
 * <p>Элементы {@code null} запрещены: {@code null} — это признак «ничего не извлечено» у
 * {@link #poll(long)}, и совмещать его с «элемент существует, но он null» нельзя.
 *
 * @param <T> тип элемента
 */
public final class BoundedBuffer<T> implements Buffer<T> {

    private final int capacity;
    private final Deque<T> storage = new ArrayDeque<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    private final long defaultTimeoutMillis;
    private boolean closed;

    /** Сколько раз ожидающий просыпался и обнаруживал, что его условие всё ещё ложно. */
    private long wastedWakeups;

    /** Буфер с ёмкостью по умолчанию равной ёмкости и сроком ожидания по умолчанию. */
    public BoundedBuffer(int capacity) {
        this(capacity, DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * @param capacity             ёмкость; меньше единицы отвергается
     * @param defaultTimeoutMillis срок ожидания для {@link #put}/{@link #take}
     */
    public BoundedBuffer(int capacity, long defaultTimeoutMillis) {
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
    public boolean offer(T item, long timeoutMillis) {
        Objects.requireNonNull(item, "элемент обязателен: null — это признак «ничего не извлечено»");
        lock.lock();
        try {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (storage.size() == capacity && !closed) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                awaitOn(notFull, remaining);
                // Пробуждение признаётся пустым только если условие ПОСЛЕ него всё ещё ложно: место
                // успел занять другой ожидающий добавления. Успешное пробуждение пустым не считается.
                if (storage.size() == capacity && !closed) {
                    wastedWakeups++;
                }
            }
            if (closed) {
                throw new BufferOperationException(BufferOperationException.Reason.CLOSED);
            }
            storage.addLast(item);
            // Адресное оповещение: один добавленный элемент даёт работу ровно одному ожидающему
            // извлечения. Будить всех (signalAll) здесь значило бы платить лишними пробуждениями.
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
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
    public T poll(long timeoutMillis) {
        lock.lock();
        try {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (storage.isEmpty() && !closed) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return null;
                }
                awaitOn(notEmpty, remaining);
                if (storage.isEmpty() && !closed) {
                    wastedWakeups++;
                }
            }
            if (storage.isEmpty()) {
                throw new BufferOperationException(BufferOperationException.Reason.CLOSED);
            }
            T item = storage.removeFirst();
            // Адресное оповещение: одно освободившееся место даёт работу ровно одному ожидающему
            // добавления.
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
            // Здесь signalAll обязателен: состояние изменилось для ВСЕХ ожидающих сразу, у события
            // нет одного адресата. Адресное оповещение оставило бы часть потоков спать навсегда.
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

    /**
     * Сколько раз ожидающий просыпался впустую.
     *
     * <p>С двумя условиями такое возможно только когда пробуждённый успел «опоздать»: место занял
     * другой ожидающий добавления. Пробуждений «не той стороны» здесь не бывает по построению — в
     * отличие от монитора, где одно множество ожидающих.
     */
    public long wastedWakeups() {
        lock.lock();
        try {
            return wastedWakeups;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Дождаться на условии, не теряя признак прерывания.
     *
     * <p>При прерывании признак восстанавливается <b>после</b> выхода из ожидания и работа
     * завершается: продолжать цикл нельзя — при вызове {@code await()} на потоке с выставленным
     * признаком прерывание сработало бы немедленно снова, и поток крутился бы в бесконечном
     * прерывании вместо ожидания.
     */
    private void awaitOn(Condition condition, long remainingNanos) {
        try {
            condition.await(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            // Восстановление признака — после выхода из ожидания, а не до повторного входа в него.
            Thread.currentThread().interrupt();
            throw new BufferOperationException(BufferOperationException.Reason.INTERRUPTED, e);
        }
    }
}
