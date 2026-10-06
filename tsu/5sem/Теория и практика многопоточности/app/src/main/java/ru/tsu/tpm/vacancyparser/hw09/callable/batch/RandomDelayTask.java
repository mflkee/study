package ru.tsu.tpm.vacancyparser.hw09.callable.batch;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Задача, имитирующая работу случайной длительности и возвращающая результат.
 *
 * <p>Длительность выбирается из диапазона {@code [minMillis, maxMillis]} и запоминается, чтобы тест мог
 * сверить наблюдаемое время ожидания с заявленным. Результат — идентификатор задачи: по нему проверяется,
 * что {@code invokeAll} вернул результаты в порядке исходного списка.
 */
public final class RandomDelayTask implements Callable<Integer> {

    private final int id;
    private final long delayMillis;
    private final AtomicLong startedAtNanos = new AtomicLong(-1L);
    private final AtomicLong finishedAtNanos = new AtomicLong(-1L);

    public RandomDelayTask(int id, long minMillis, long maxMillis, long seed) {
        if (minMillis <= 0 || maxMillis < minMillis) {
            throw new IllegalArgumentException("некорректный диапазон длительности: " + minMillis + ".." + maxMillis);
        }
        this.id = id;
        long span = maxMillis - minMillis + 1;
        this.delayMillis = minMillis + Math.floorMod(new Random(seed).nextLong(), span);
    }

    @Override
    public Integer call() throws InterruptedException {
        startedAtNanos.set(System.nanoTime());
        try {
            Thread.sleep(delayMillis);
        } finally {
            finishedAtNanos.set(System.nanoTime());
        }
        return id;
    }

    /** Заданная длительность задачи. */
    public long delayMillis() {
        return delayMillis;
    }

    /** Идентификатор (он же результат). */
    public int id() {
        return id;
    }

    /** Момент начала работы (наносекунды монотонного таймера) или {@code -1}. */
    public long startedAtNanos() {
        return startedAtNanos.get();
    }

    /** Момент завершения работы или {@code -1}. */
    public long finishedAtNanos() {
        return finishedAtNanos.get();
    }
}
