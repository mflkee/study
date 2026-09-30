package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Ожидание и оба вида уведомления.
 *
 * <p>Проверяется именно то, ради чего механизм существует: поток, которому нечего делать, не крутит
 * процессор, а освобождает монитор и ждёт; уведомление приходит после изменения состояния;
 * {@code notify()} будит одного, {@code notifyAll()} — всех, и это различие наблюдаемо, а не
 * декларативно.
 */
class NotificationTest {

    private static final long TIMEOUT_MILLIS = 5_000L;

    /**
     * Таймаут потребителя в сценарии с {@code notify()}.
     *
     * <p>Короткий намеренно: проигравший потребитель досыпает до конца таймаута, и тест ждал бы его
     * всё это время. Оба потребителя гарантированно успевают заснуть раньше — это проверяется
     * ожиданием состояния {@code TIMED_WAITING}.
     */
    private static final long TAKER_TIMEOUT_MILLIS = 1_000L;

    /** Дождаться, что поток перешёл в нужное состояние. */
    private static boolean awaitState(Thread thread, Thread.State expected, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == expected) {
                return true;
            }
            Thread.sleep(1L);
        }
        return thread.getState() == expected;
    }

    private static boolean awaitAllInState(List<Thread> threads, Thread.State expected, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (threads.stream().allMatch(thread -> thread.getState() == expected)) {
                return true;
            }
            Thread.sleep(1L);
        }
        return threads.stream().allMatch(thread -> thread.getState() == expected);
    }

    private static Thread daemon(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        return thread;
    }

    @Test
    @Timeout(20)
    @DisplayName("До уведомления ожидающий поток находится в WAITING")
    void waitingThreadIsInWaitingState() throws InterruptedException {
        DataCollector collector = new DataCollector();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread waiter = daemon("readiness-waiter", () -> {
            try {
                collector.awaitReady();
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        waiter.start();

        assertThat(awaitState(waiter, Thread.State.WAITING, TIMEOUT_MILLIS))
                .as("ожидание без таймаута обязано давать WAITING, а не TIMED_WAITING")
                .isTrue();

        collector.markReady();
        waiter.join(TIMEOUT_MILLIS);

        assertThat(waiter.isAlive()).as("после уведомления поток обязан завершиться").isFalse();
        assertThat(failure.get()).isNull();
        assertThat(waiter.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    @Test
    @Timeout(20)
    @DisplayName("Ожидание с таймаутом даёт TIMED_WAITING и не виснет, если события не будет")
    void timedWaitingReturnsWhenEventNeverComes() throws InterruptedException {
        DataCollector collector = new DataCollector();
        AtomicReference<Boolean> result = new AtomicReference<>();

        Thread waiter = daemon("timed-waiter", () -> {
            try {
                result.set(collector.awaitReady(200L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();

        assertThat(awaitState(waiter, Thread.State.TIMED_WAITING, TIMEOUT_MILLIS)).isTrue();
        waiter.join(TIMEOUT_MILLIS);

        assertThat(result.get())
                .as("без уведомления ожидание обязано завершиться по времени и сообщить о неудаче")
                .isFalse();
    }

    @Test
    @Timeout(20)
    @DisplayName("Уведомление отправляется после изменения состояния: сигнал не теряется")
    void notificationHappensAfterStateChange() throws InterruptedException {
        DataCollector collector = new DataCollector();
        AtomicReference<Boolean> awaited = new AtomicReference<>();
        CountDownLatch checkNow = new CountDownLatch(1);

        Thread lateChecker = daemon("late-checker", () -> {
            try {
                checkNow.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                // Проверка идёт уже ПОСЛЕ того, как состояние изменено и уведомление отправлено.
                // Порядок «изменить, затем уведомить» и одна критическая секция на оба действия
                // гарантируют, что проверяющий увидит выполненное условие и не заснёт.
                awaited.set(collector.awaitReady(1_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        lateChecker.start();

        collector.markReady();
        checkNow.countDown();
        lateChecker.join(TIMEOUT_MILLIS);

        assertThat(awaited.get())
                .as("поток, проверивший условие после уведомления, обязан обнаружить его выполненным")
                .isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("notify(): один элемент будит ровно одного ожидающего, лишних пробуждений нет")
    void notifyWakesExactlyOneTaker() throws InterruptedException {
        DataCollector collector = new DataCollector();
        AtomicInteger taken = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();

        List<Thread> takers = List.of(
                daemon("taker-1", () -> {
                    try {
                        if (collector.takeItem(TAKER_TIMEOUT_MILLIS) != null) {
                            taken.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.incrementAndGet();
                    }
                }),
                daemon("taker-2", () -> {
                    try {
                        if (collector.takeItem(TAKER_TIMEOUT_MILLIS) != null) {
                            taken.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.incrementAndGet();
                    }
                }));
        takers.forEach(Thread::start);

        assertThat(awaitAllInState(takers, Thread.State.TIMED_WAITING, TIMEOUT_MILLIS))
                .as("оба потребителя должны заснуть до появления элемента")
                .isTrue();

        assertThat(collector.collectItem(new Item("v-1", "текст")))
                .as("один элемент — одно уведомление")
                .isTrue();

        for (Thread taker : takers) {
            taker.join(TIMEOUT_MILLIS);
        }

        assertThat(taken.get()).as("элемент забрал ровно один потребитель").isEqualTo(1);
        assertThat(finallyCount(finished)).isEqualTo(2);
        assertThat(collector.wastedWakeups())
                .as("notify() не будит тех, кому нечего забрать: при notifyAll() второй потребитель "
                        + "проснулся бы впустую, счётчик стал бы больше нуля, и тест упал бы")
                .isZero();
    }

    @Test
    @Timeout(30)
    @DisplayName("notifyAll(): смена состояния будит всех ожидающих, каждый перепроверяет условие")
    void notifyAllWakesEveryWaiter() throws InterruptedException {
        DataCollector collector = new DataCollector();
        int waiters = 6;
        AtomicInteger woken = new AtomicInteger();

        List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < waiters; i++) {
            threads.add(daemon("ready-" + i, () -> {
                try {
                    if (collector.awaitReady(TIMEOUT_MILLIS)) {
                        woken.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        threads.forEach(Thread::start);

        assertThat(awaitAllInState(threads, Thread.State.TIMED_WAITING, TIMEOUT_MILLIS))
                .as("все ожидающие должны заснуть до уведомления")
                .isTrue();

        collector.markReady();

        for (Thread thread : threads) {
            thread.join(TIMEOUT_MILLIS);
        }

        assertThat(woken.get())
                .as("notifyAll() обязан разбудить всех ожидающих, а не одного")
                .isEqualTo(waiters);
    }

    private static int finallyCount(AtomicInteger counter) {
        return counter.get();
    }
}
