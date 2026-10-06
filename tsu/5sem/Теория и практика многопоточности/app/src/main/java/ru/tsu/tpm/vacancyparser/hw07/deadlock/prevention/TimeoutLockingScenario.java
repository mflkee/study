package ru.tsu.tpm.vacancyparser.hw07.deadlock.prevention;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Предотвращение взаимной блокировки через захват с ограничением по времени.
 *
 * <p>Если ресурс занят, поток не ждёт его бесконечно: {@code tryLock(timeout)} возвращает {@code false}
 * через отведённое время, поток не удерживает ресурс и продолжает работу. Это второй приём защиты после
 * единого порядка захвата: даже когда захват с таймаутом не удаётся, «залипания» не происходит.
 */
public final class TimeoutLockingScenario {

    /** Имя сценария для выбора в демонстрации. */
    public static final String NAME = "timeout";

    /** Сколько ждать занятый ресурс прежде чем отказаться. */
    public static final long TIMEOUT_MILLIS = 150L;

    /**
     * Итог сценария.
     *
     * @param timedOut        истёк ли таймаут захвата
     * @param waitedMillis    сколько фактически длилось ожидание
     * @param resourceReleased свободен ли ресурс после сценария
     * @param holderFinished  завершился ли поток-держатель
     */
    public record Outcome(boolean timedOut, long waitedMillis, boolean resourceReleased, boolean holderFinished) {
    }

    private TimeoutLockingScenario() {
    }

    /** Выполнить сценарий: один поток держит ресурс, другой пытается взять его с таймаутом. */
    public static Outcome run() throws InterruptedException {
        ReentrantLock resource = new ReentrantLock();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            resource.lock();
            try {
                held.countDown();
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                resource.unlock();
            }
        }, "Prevention-holder");
        holder.setDaemon(true);
        holder.start();
        held.await(5, TimeUnit.SECONDS);

        long start = System.nanoTime();
        boolean acquired;
        try {
            acquired = resource.tryLock(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        long waitedMillis = (System.nanoTime() - start) / 1_000_000L;

        release.countDown();
        holder.join(5_000L);

        return new Outcome(!acquired, waitedMillis, !resource.isLocked(), !holder.isAlive());
    }
}
