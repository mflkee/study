package ru.tsu.tpm.vacancyparser.hw07.deadlock.deadlock;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Стенд взаимной блокировки: два ресурса и два потока, захватывающих их в противоположном порядке.
 *
 * <p>Сценарий устроен детерминированно, а не «если повезёт». Поток {@code Deadlock-A} сначала
 * захватывает {@code resourceA}, поток {@code Deadlock-B} — {@code resourceB}. После первого захвата
 * оба останавливаются на барьере готовности, и только потом по общему сигналу идут за вторым ресурсом.
 * Поэтому к моменту второго захвата каждый гарантированно держит свой первый, и взаимное ожидание
 * воспроизводится в каждом прогоне.
 *
 * <p>Второй захват выполняется {@code lockInterruptibly()}, и это принципиально: {@code lock()}
 * непрерываем, и снять заблокированные потоки было бы нельзя. С прерываемым захватом гарантированный
 * выход (задача 2.3) выполняется просто: прервать оба потока, дождаться завершения — каждый освобождает
 * первый ресурс в {@code finally} и процесс продолжается.
 */
public final class DeadlockRig {

    /** Доменные имена потоков цикла. */
    public static final String THREAD_A = "Deadlock-A";
    public static final String THREAD_B = "Deadlock-B";

    /** Имена ресурсов, выведенные в порядке захвата потоком A. */
    public static final String RESOURCE_A = "resourceA";
    public static final String RESOURCE_B = "resourceB";

    private final ReentrantLock resourceA = new ReentrantLock();
    private final ReentrantLock resourceB = new ReentrantLock();
    private final CountDownLatch firstLocksDone = new CountDownLatch(2);
    private final CountDownLatch proceed = new CountDownLatch(1);
    private final Thread threadA;
    private final Thread threadB;

    /** Создать стенд: потоки ещё не запущены. */
    public DeadlockRig() {
        threadA = new Thread(() -> holdInOppositeOrder(resourceA, resourceB), THREAD_A);
        threadB = new Thread(() -> holdInOppositeOrder(resourceB, resourceA), THREAD_B);
        threadA.setDaemon(true);
        threadB.setDaemon(true);
    }

    /** Запустить оба потока. */
    public void start() {
        threadA.start();
        threadB.start();
    }

    /**
     * Захват двух ресурсов в противоположном порядке: сначала {@code first}, затем {@code second}.
     *
     * <p>Первая блокировка — обычная, вторая — прерываемая: именно на ней потоки встают в цикл
     * взаимного ожидания, и именно её можно прервать при освобождении.
     */
    private void holdInOppositeOrder(ReentrantLock first, ReentrantLock second) {
        first.lock();
        firstLocksDone.countDown();
        try {
            proceed.await();
            second.lockInterruptibly();
            try {
                // Второй ресурс удержан — состояние взаимного ожидания достигнуто.
            } finally {
                second.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            first.unlock();
        }
    }

    /** Дождаться, что оба потока захватили свои первые ресурсы. */
    public boolean awaitFirstLocks(long timeoutMillis) throws InterruptedException {
        return firstLocksDone.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Разрешить потокам перейти ко второму захвату. */
    public void proceedToSecondLock() {
        proceed.countDown();
    }

    /** Удерживаются ли оба первых ресурса (и взаимного ожидания ещё нет). */
    public boolean bothFirstResourcesHeld() {
        return resourceA.isLocked() && resourceB.isLocked();
    }

    /** Дождаться, пока JVM распознает взаимную блокировку между потоками стенда. */
    public boolean awaitDeadlock(long timeoutMillis) throws InterruptedException {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long idA = threadA.getId();
        long idB = threadB.getId();
        while (System.nanoTime() < deadline) {
            long[] ids = bean.findDeadlockedThreads();
            if (ids != null && contains(ids, idA) && contains(ids, idB)) {
                return true;
            }
            Thread.sleep(10L);
        }
        return false;
    }

    /** Имена потоков, которые JVM сейчас считает взаимно заблокированными. */
    public String deadlockedThreadNames() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] ids = bean.findDeadlockedThreads();
        if (ids == null || ids.length == 0) {
            return "";
        }
        StringBuilder names = new StringBuilder();
        for (var info : bean.getThreadInfo(ids)) {
            if (info == null) {
                continue;
            }
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(info.getThreadName());
        }
        return names.toString();
    }

    /** Прервать оба потока и дождаться их завершения с таймаутом. */
    public boolean release() throws InterruptedException {
        threadA.interrupt();
        threadB.interrupt();
        threadA.join(5_000L);
        threadB.join(5_000L);
        return !threadA.isAlive() && !threadB.isAlive();
    }

    /** Потоки стенда. */
    public java.util.List<Thread> threads() {
        return java.util.List.of(threadA, threadB);
    }

    private static boolean contains(long[] ids, long id) {
        return Arrays.stream(ids).anyMatch(value -> value == id);
    }
}
