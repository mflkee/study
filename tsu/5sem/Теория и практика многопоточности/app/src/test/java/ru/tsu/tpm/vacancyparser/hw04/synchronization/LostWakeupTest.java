package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Пропущенный сигнал (lost wakeup) — главная ловушка {@code wait()}/{@code notify()}.
 *
 * <p>Сценарий: один поток изменил состояние и уведомил всех **раньше**, чем интересующийся поток
 * вообще дошёл до ожидания. Наивная реализация «если условие ложно — заснуть» в этом случае теряет
 * сигнал и спит вечно: уведомление уже прошло. Спасает цикл: перед сном условие проверяется ещё
 * раз, и поток видит, что ждать нечего.
 */
class LostWakeupTest {

    private static final long TIMEOUT_MILLIS = 5_000L;

    @Test
    @Timeout(20)
    @DisplayName("Уведомление до начала ожидания: поток видит выполненное условие и не засыпает")
    void notificationBeforeWaitingIsNotLost() throws InterruptedException {
        DataCollector collector = new DataCollector();
        AtomicReference<Boolean> awaited = new AtomicReference<>();
        AtomicBoolean returnedQuickly = new AtomicBoolean();

        // Состояние меняется и уведомление отправляется ДО того, как ожидающий начнёт ждать —
        // ни одного ожидающего в этот момент нет, сигнал уходит «в пустоту».
        collector.markReady();
        collector.markReady();

        Thread lateWaiter = new Thread(() -> {
            try {
                long startedAt = System.nanoTime();
                collector.awaitReady();
                awaited.set(true);
                returnedQuickly.set(System.nanoTime() - startedAt < TimeUnit.MILLISECONDS.toNanos(500));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "late-waiter");
        lateWaiter.setDaemon(true);
        lateWaiter.start();
        lateWaiter.join(TIMEOUT_MILLIS);

        assertThat(lateWaiter.isAlive())
                .as("поток не имеет права зависнуть из-за уведомления, отправленного до его ожидания")
                .isFalse();
        assertThat(awaited.get())
                .as("условие уже истинно — ждать нечего")
                .isTrue();
        assertThat(returnedQuickly.get())
                .as("поток обязан вернуться сразу, а не проспать таймаут")
                .isTrue();
    }

    @Test
    @Timeout(20)
    @DisplayName("Проснувшись не от того события, поток возвращается в ожидание и не теряет сигнал")
    void wakingWithoutItsConditionReturnsToWait() throws InterruptedException {
        DataCollector collector = new DataCollector();
        AtomicReference<Boolean> ready = new AtomicReference<>();
        CountDownLatch waiterIsParked = new CountDownLatch(1);

        Thread waiter = new Thread(() -> {
            try {
                waiterIsParked.countDown();
                ready.set(collector.awaitReady(TIMEOUT_MILLIS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "ready-waiter");
        waiter.setDaemon(true);
        waiter.start();

        assertThat(waiterIsParked.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        Thread.sleep(50L);

        // Приход элемента будит ожидающего через notify(), но его условие (готовность набора
        // данных) от этого не становится истинным: цикл обязан вернуть его в ожидание,
        // а не выпустить наружу с ложным результатом.
        collector.collectItem(new Item("v-1", "текст"));
        Thread.sleep(50L);

        assertThat(waiter.getState())
                .as("поток, разбуженный не по своему условию, обязан снова ждать")
                .isEqualTo(Thread.State.TIMED_WAITING);
        assertThat(waiter.isAlive()).isTrue();

        collector.markReady();
        waiter.join(TIMEOUT_MILLIS);

        assertThat(ready.get()).as("своё событие поток обязан дождаться").isTrue();
    }
}
