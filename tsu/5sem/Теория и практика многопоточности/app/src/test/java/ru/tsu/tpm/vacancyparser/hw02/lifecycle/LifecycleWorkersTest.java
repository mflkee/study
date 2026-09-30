package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки самих демо-потоков: каждый обязан удерживать ровно то состояние, ради которого
 * создан, и никакое другое.
 */
class LifecycleWorkersTest {

    private static final long TIMEOUT_MILLIS = 10_000L;

    /**
     * Страховочный срок ожиданий внутри потоков в этих тестах.
     *
     * <p>Короткий намеренно: проверяется, что поток выходит по собственному таймауту, а не висит,
     * поэтому ждать реальные десять секунд незачем.
     */
    private static final long SAFETY_MILLIS = 300L;

    private RunnableWindow openWindow(String label) {
        RunnableWindow window = new RunnableWindow(label, TIMEOUT_MILLIS);
        window.open();
        return window;
    }

    private RunnableWindow closedWindow(String label) {
        return new RunnableWindow(label, TIMEOUT_MILLIS);
    }

    @AfterEach
    void nothingToCleanUp() {
        // Потоки каждого теста завершаются внутри самого теста; отдельная уборка не нужна.
    }

    @Test
    @DisplayName("LifecycleStartWorker: NEW до start(), RUNNABLE в участке работы, TERMINATED после join()")
    void lifecycleStartWorkerPassesThroughNewRunnableTerminated() throws InterruptedException {
        RunnableWindow work = closedWindow("работа");
        RunnableWindow finish = closedWindow("финал");
        LifecycleStartWorker worker = new LifecycleStartWorker("LifecycleStartWorker", work, finish, SAFETY_MILLIS);

        assertThat(worker.getState())
                .as("до start() поток обязан быть в состоянии NEW")
                .isEqualTo(Thread.State.NEW);

        worker.start();
        assertThat(work.awaitStarted(TIMEOUT_MILLIS)).isTrue();

        assertThat(worker.getState())
                .as("пока затвор закрыт, поток крутит участок работы и остаётся RUNNABLE")
                .isEqualTo(Thread.State.RUNNABLE);

        work.open();
        finish.open();
        worker.join(TIMEOUT_MILLIS);

        assertThat(worker.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    @Test
    @DisplayName("SleepingWorker: во время сна состояние TIMED_WAITING, а не RUNNABLE")
    void sleepingWorkerHoldsTimedWaiting() throws InterruptedException {
        SleepingWorker worker = new SleepingWorker(
                "SleepingWorker", openWindow("работа"), openWindow("финал"), 2_000L, SAFETY_MILLIS);

        worker.start();
        assertThat(worker.awaitAboutToSleep(TIMEOUT_MILLIS)).isTrue();

        // Сигнал о готовности ко сну подаётся ДО Thread.sleep(), поэтому в сам момент сигнала
        // поток ещё RUNNABLE: он не успел припарковаться. Состояние поэтому дожидается с пределом
        // времени — запас длительности сна и делает этот захват надёжным.
        assertThat(awaitState(worker, Thread.State.TIMED_WAITING, TIMEOUT_MILLIS))
                .as("Thread.sleep() удерживает TIMED_WAITING, пока идёт сон")
                .isTrue();
        assertThat(worker.getState()).isEqualTo(Thread.State.TIMED_WAITING);

        worker.join(TIMEOUT_MILLIS);
        assertThat(worker.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    @Test
    @DisplayName("WaitingWorker: до сигнала WAITING, чтение состояния под монитором тоже даёт WAITING")
    void waitingWorkerHoldsWaiting() throws InterruptedException {
        RunnableWindow finish = closedWindow("финал");
        WaitingWorker worker = new WaitingWorker("WaitingWorker", openWindow("работа"), finish, SAFETY_MILLIS);

        worker.start();
        assertThat(worker.awaitAboutToWait(TIMEOUT_MILLIS)).isTrue();

        assertThat(worker.stateInsideMonitor())
                .as("под тем же монитором состояние читается однозначно — WAITING, а не переход")
                .isEqualTo(Thread.State.WAITING);
        assertThat(worker.getState()).isEqualTo(Thread.State.WAITING);

        worker.release();
        assertThat(finish.awaitStarted(TIMEOUT_MILLIS)).isTrue();
        assertThat(worker.getState())
                .as("после выхода из wait() поток снова RUNNABLE — он в участке работы")
                .isEqualTo(Thread.State.RUNNABLE);

        finish.open();
        worker.join(TIMEOUT_MILLIS);
        assertThat(worker.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    @Test
    @DisplayName("MonitorHolderThread и BlockedWorker: претендент получает именно BLOCKED")
    void blockedWorkerHoldsBlockedWhileMonitorHeld() throws InterruptedException {
        Object monitor = new Object();
        MonitorHolderThread holder = new MonitorHolderThread(
                "MonitorHolderThread", openWindow("работа"), openWindow("финал"), monitor, SAFETY_MILLIS);
        BlockedWorker blocked = new BlockedWorker(
                "BlockedWorker", openWindow("работа"), openWindow("финал"), monitor, SAFETY_MILLIS);

        holder.start();
        assertThat(holder.awaitSectionRequested(TIMEOUT_MILLIS)).isTrue();
        holder.grantSectionPermit();
        assertThat(holder.awaitInsideSection(TIMEOUT_MILLIS)).isTrue();

        blocked.start();
        assertThat(blocked.awaitEntryRequested(TIMEOUT_MILLIS)).isTrue();
        blocked.grantEntryPermit();

        assertThat(awaitState(blocked, Thread.State.BLOCKED, TIMEOUT_MILLIS))
                .as("пока монитор удерживается держателем, претендент обязан быть BLOCKED")
                .isTrue();
        assertThat(blocked.getState()).isEqualTo(Thread.State.BLOCKED);

        holder.release();
        assertThat(blocked.awaitEnteredSection(TIMEOUT_MILLIS)).isTrue();
        blocked.join(TIMEOUT_MILLIS);
        holder.join(TIMEOUT_MILLIS);

        assertThat(blocked.getState()).isEqualTo(Thread.State.TERMINATED);
        assertThat(holder.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    @Test
    @DisplayName("Без разрешения на вход претендент не блокируется и не занимает монитор")
    void blockedWorkerStaysRunnableWithoutPermit() throws InterruptedException {
        Object monitor = new Object();
        BlockedWorker blocked = new BlockedWorker(
                "BlockedWorker", openWindow("работа"), openWindow("финал"), monitor, SAFETY_MILLIS);

        blocked.start();
        assertThat(blocked.awaitEntryRequested(TIMEOUT_MILLIS)).isTrue();

        assertThat(blocked.getState())
                .as("без contended-схемы блокировки не возникает — это и подтверждает её природу")
                .isNotEqualTo(Thread.State.BLOCKED);

        blocked.grantEntryPermit();
        assertThat(blocked.awaitEnteredSection(TIMEOUT_MILLIS)).isTrue();
        blocked.join(TIMEOUT_MILLIS);
    }

    @Test
    @DisplayName("Окно RUNNABLE не блокирует поток навсегда, если затвор так и не открыли")
    void runnableWindowHasSafetyDeadline() throws InterruptedException {
        RunnableWindow window = new RunnableWindow("страховка", 200L);        CountDownLatch done = new CountDownLatch(1);

        Thread thread = new Thread(() -> {
            window.hold();
            done.countDown();
        }, "SafetyWorker");
        thread.start();

        assertThat(done.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                .as("по истечении страховочного срока участок работы прерывается сам")
                .isTrue();
        thread.join(TIMEOUT_MILLIS);
    }

    @Test
    @DisplayName("Поток не зависает, если разрешение на вход в секцию не выдано")
    void workerDoesNotHangWhenPermitNeverGranted() throws InterruptedException {
        Object monitor = new Object();
        MonitorHolderThread holder = new MonitorHolderThread(
                "MonitorHolderThread", openWindow("работа"), openWindow("финал"), monitor, SAFETY_MILLIS);

        holder.start();
        assertThat(holder.awaitSectionRequested(TIMEOUT_MILLIS)).isTrue();
        // Разрешение на вход в секцию не выдаём вовсе: держатель обязан выйти по своему
        // страховочному сроку, а не остаться висеть на непредоставленном сигнале.
        holder.join(SAFETY_MILLIS + TIMEOUT_MILLIS);

        assertThat(holder.isAlive())
                .as("поток обязан завершиться, а не висеть на непредоставленном разрешении")
                .isFalse();
        assertThat(holder.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    private static boolean awaitState(Thread thread, Thread.State expected, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == expected) {
                return true;
            }
            Thread.onSpinWait();
        }
        return thread.getState() == expected;
    }
}
