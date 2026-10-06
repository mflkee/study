package ru.tsu.tpm.vacancyparser.hw07.deadlock.livelock;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Раундовый барьер без блокировки: потоки встречаются в конце каждого раунда и расходятся вместе.
 *
 * <p>Нужен, чтобы столкновение было детерминированным: если оба потока обязаны встретиться перед тем,
 * как решить исход раунда, они не могут разминуться по времени и «проскочить» мимо livelock. В отличие
 * от {@code CyclicBarrier}, ожидание здесь — активный спин ({@link Thread#onSpinWait()}), поэтому
 * потоки остаются {@code RUNNABLE}, а не переходят в состояние ожидания. Именно это и отличает livelock
 * от deadlock и starvation: потоки не заблокированы, но не продвигаются.
 *
 * <p>Барьер учитывает признак остановки: иначе после сигнала остановки один поток мог бы навсегда
 * остаться в ожидании второго.
 */
final class RoundBarrier {

    private final int parties;
    private final AtomicInteger arrived = new AtomicInteger();
    private final AtomicInteger generation = new AtomicInteger();

    RoundBarrier(int parties) {
        this.parties = parties;
    }

    /**
     * Встретиться в конце раунда.
     *
     * @param stopped признак остановки
     * @return номер нового раунда для всех участников или {@code -1}, если работа остановлена
     */
    long meet(BooleanSupplier stopped) {
        int startGeneration = generation.get();
        if (arrived.incrementAndGet() == parties) {
            arrived.set(0);
            return generation.incrementAndGet();
        }
        while (generation.get() == startGeneration) {
            if (stopped.getAsBoolean()) {
                return -1L;
            }
            Thread.onSpinWait();
        }
        return generation.get();
    }
}
