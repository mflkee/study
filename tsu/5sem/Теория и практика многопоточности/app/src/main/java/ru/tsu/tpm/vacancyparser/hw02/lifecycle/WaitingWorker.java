package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Поток, удерживаемый в состоянии {@link Thread.State#WAITING} через {@link Object#wait()}.
 *
 * <p>Ожидание без таймаута, поэтому состояние держится ровно до сигнала от наблюдателя. Ключевой
 * примитив — объект-монитор: сигнал {@code notify} имеет смысл только внутри
 * {@code synchronized} по тому же объекту, поэтому наблюдатель обязан сначала войти в секцию.
 *
 * <p>Гонка закрыта взаимным порядком, а не таймаутом: поток удерживает монитор непрерывно от
 * входа в секцию до вызова {@code wait()}, поэтому наблюдатель физически не может войти в монитор
 * раньше, чем поток в нём уснёт. Как только наблюдатель получил монитор, {@code getState()}
 * гарантированно возвращает {@code WAITING} — а не {@code RUNNABLE} в момент перехода.
 *
 * <p>Набор наблюдённых состояний:
 * {@code NEW} → {@code RUNNABLE} → {@code WAITING} → {@code RUNNABLE} → {@code TERMINATED}.
 */
public final class WaitingWorker extends LifecycleWorker {

    private final Object monitor = new Object();
    private final CountDownLatch aboutToWait = new CountDownLatch(1);
    private final CountDownLatch resumed = new CountDownLatch(1);

    public WaitingWorker(
            String name, RunnableWindow workWindow, RunnableWindow finishWindow, long safetyMillis) {
        super(name, workWindow, finishWindow, safetyMillis);
    }

    @Override
    protected void awaitWaitPhase() {
        synchronized (monitor) {
            aboutToWait.countDown();
            try {
                monitor.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        resumed.countDown();
    }

    /** Дождаться сигнала «поток сейчас уснёт». {@code false} — по таймауту. */
    public boolean awaitAboutToWait(long timeoutMillis) throws InterruptedException {
        return aboutToWait.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Прочитать состояние потока, удерживая монитор.
     *
     * <p>Вызов блокируется до тех пор, пока поток не отдаст монитор изнутри {@code wait()}.
     * Возвращённое значение — это состояние в гарантированно нужный момент, а не результат
     * удачной попытки попасть в окно перехода.
     */
    public Thread.State stateInsideMonitor() {
        synchronized (monitor) {
            return getState();
        }
    }

    /** Разбудить поток: {@code notify} отрабатывает только под тем же монитором. */
    public void release() {
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    /** Дождаться, пока поток проснётся и выйдет из секции. {@code false} — по таймауту. */
    public boolean awaitResumed(long timeoutMillis) throws InterruptedException {
        return resumed.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}
