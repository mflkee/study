package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Счёт (ресурс), который потоки захватывают для перевода средств.
 *
 * <p><b>Зачем отдельный захват, если есть монитор.</b> Монитор сам по себе не умеет тайм-аут:
 * {@code synchronized} либо ждёт неограниченно, либо не ждёт вовсе. Требование задания —
 * «использовать тайм-ауты для предотвращения взаимных блокировок» — поэтому реализовано как флаг
 * владения плюс {@link Object#wait()} с временем: ожидание освобождает монитор и ограничено
 * дедлайном, а флаг не пускает второго владельца.
 *
 * <p><b>Почему не {@code ReentrantLock}.</b> Пункт 2 задания требует защиты монитором, и
 * {@code ReentrantLock}/{@code Condition} — предмет ДЗ 5. Свой тайм-аут на {@code wait()} решает
 * задачу ДЗ 4, не забегая вперёд.
 *
 * <p><b>Владение проверяется, а не подразумевается.</b> Изменять баланс разрешено только тому
 * потоку, который держит захват: {@link #applyUnderLock(long)} и {@link #balanceUnderLock()}
 * сверяют владельца и бросают исключение при нарушении. Это делает ошибку «изменил без захвата»
 * громкой, а не тихой.
 */
public final class Account {

    private final String id;
    private long balance;

    /** Флаг владения и его владелец — оба защищены монитором этого объекта. */
    private boolean locked;
    private Thread owner;

    public Account(String id, long balance) {
        this.id = Objects.requireNonNull(id, "идентификатор счёта обязателен");
        this.balance = balance;
    }

    public String id() {
        return id;
    }

    /**
     * Баланс «на покое».
     *
     * <p>Обычное чтение под монитором. Во время чужого перевода этот метод вернул бы промежуточное
     * значение, поэтому для замеров и сверок после нагрузки используется он, а внутри критической
     * секции — {@link #balanceUnderLock()}.
     */
    public synchronized long balance() {
        return balance;
    }

    /**
     * Попытка занять счёт, ожидая не дольше тайм-аута.
     *
     * @return {@code true}, если счёт занят этим потоком; {@code false}, если он не освободился
     *         за отведённое время
     */
    synchronized boolean tryLock(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (locked) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
        locked = true;
        owner = Thread.currentThread();
        return true;
    }

    /** Освободить счёт и разбудить тех, кто ждал его освобождения. */
    synchronized void unlock() {
        if (owner != Thread.currentThread()) {
            throw new IllegalStateException("освобождать счёт " + id + " может только его владелец");
        }
        locked = false;
        owner = null;
        notifyAll();
    }

    /** Изменить баланс; вызывается только владельцем захвата. */
    synchronized void applyUnderLock(long delta) {
        requireOwnership();
        balance += delta;
    }

    /** Прочитать баланс изнутри критической секции; вызывается только владельцем захвата. */
    synchronized long balanceUnderLock() {
        requireOwnership();
        return balance;
    }

    /** Занят ли счёт прямо сейчас. */
    synchronized boolean isLocked() {
        return locked;
    }

    private void requireOwnership() {
        if (owner != Thread.currentThread()) {
            throw new IllegalStateException(
                    "баланс счёта " + id + " изменяется и читается только под захватом; "
                            + "текущий поток владельцем не является");
        }
    }

    @Override
    public String toString() {
        return id;
    }
}
