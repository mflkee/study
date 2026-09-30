package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Претендент на монитор — поток, который получает {@link Thread.State#BLOCKED}.
 *
 * <p>{@code BLOCKED} нельзя получить «добровольно»: ни один API не переводит поток в это
 * состояние намеренно, JVM назначает его при неудачной попытке захватить удерживаемый монитор.
 * Поэтому здесь создаётся настоящая конкуренция — поток пытается войти в секцию, которую уже
 * занял {@link MonitorHolderThread}.
 *
 * <p>Запрашивать разрешение на попытку входа нужно строго после подтверждения, что держатель
 * уже внутри секции. Иначе претендент войдёт первым, и блокировки не возникнет вовсе.
 *
 * <p>Набор наблюдённых состояний:
 * {@code NEW} → {@code RUNNABLE} → {@code BLOCKED} → {@code RUNNABLE} → {@code TERMINATED}.
 */
public final class BlockedWorker extends LifecycleWorker {

    private final Object monitor;
    private final CountDownLatch entryRequested = new CountDownLatch(1);
    private final CountDownLatch entryPermit = new CountDownLatch(1);
    private final CountDownLatch enteredSection = new CountDownLatch(1);

    public BlockedWorker(
            String name, RunnableWindow workWindow, RunnableWindow finishWindow, Object monitor, long safetyMillis) {
        super(name, workWindow, finishWindow, safetyMillis);
        this.monitor = monitor;
    }

    @Override
    protected void awaitWaitPhase() {
        entryRequested.countDown();
        if (!awaitLatch(entryPermit)) {
            return;
        }
        synchronized (monitor) {
            enteredSection.countDown();
        }
    }

    /** Дождаться просьбы потока о попытке входа. {@code false} — по таймауту. */
    public boolean awaitEntryRequested(long timeoutMillis) throws InterruptedException {
        return entryRequested.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Разрешить попытку входа — только когда монитор уже удерживается держателем. */
    public void grantEntryPermit() {
        entryPermit.countDown();
    }

    /** Дождаться, пока претендент действительно вошёл в секцию. */
    public boolean awaitEnteredSection(long timeoutMillis) throws InterruptedException {
        return enteredSection.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}
