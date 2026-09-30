package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Поток, удерживаемый в состоянии {@link Thread.State#TIMED_WAITING} через {@link Thread#sleep(long)}.
 *
 * <p>Длительность сна выбирается с запасом относительно шага наблюдателя: наблюдатель читает
 * состояние сразу после сигнала {@link #awaitAboutToSleep(long)}, то есть за микросекунды, тогда
 * как сон длится сотни миллисекунд. Досрочно разбудить поток нельзя — сигнала на пробуждение
 * здесь нет вовсе, единственный сигнал, который этот поток ждёт, — уже отработавшее время сна.
 *
 * <p>Набор наблюдённых состояний:
 * {@code NEW} → {@code RUNNABLE} → {@code TIMED_WAITING} → {@code RUNNABLE} → {@code TERMINATED}.
 */
public final class SleepingWorker extends LifecycleWorker {

    private final CountDownLatch aboutToSleep = new CountDownLatch(1);
    private final long sleepMillis;

    public SleepingWorker(
            String name, RunnableWindow workWindow, RunnableWindow finishWindow, long sleepMillis, long safetyMillis) {
        super(name, workWindow, finishWindow, safetyMillis);
        this.sleepMillis = sleepMillis;
    }

    @Override
    protected void awaitWaitPhase() {
        aboutToSleep.countDown();
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Дождаться сигнала «поток сейчас уснёт». {@code false} — по таймауту. */
    public boolean awaitAboutToSleep(long timeoutMillis) throws InterruptedException {
        return aboutToSleep.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}
