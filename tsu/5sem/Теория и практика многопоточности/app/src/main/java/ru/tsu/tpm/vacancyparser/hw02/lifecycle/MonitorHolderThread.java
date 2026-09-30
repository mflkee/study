package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Держатель монитора — второй участник схемы получения {@link Thread.State#BLOCKED}.
 *
 * <p>Пока этот поток находится внутри {@code synchronized (monitor)}, монитор захвачен, и любой
 * другой поток, попытавшийся в него войти, вынужден ждать входа — то есть получит {@code BLOCKED}.
 *
 * <p>Почему это отдельный поток, а не главный: главному потоку в фазе наблюдения нужно читать
 * состояние претендента и отпускать держателя, а держать тот же монитор он не может — тогда
 * претендент не оказался бы в {@code BLOCKED}.
 *
 * <p><b>Почему ожидание идёт по отдельному монитору.</b> Внутри секции по общему монитору держатель
 * ждёт на собственном внутреннем мониторе через {@link Object#wait()} без таймаута. Так его
 * состояние — именно {@link Thread.State#WAITING}, а {@link Thread.State#TIMED_WAITING}: ожидание с
 * таймаутом дало бы {@code TIMED_WAITING}, и держатель в какой-то момент вышел бы из секции сам,
 * прежде чем претендент был бы пойман в {@code BLOCKED}. При этом общий монитор всё это время
 * остаётся захваченным — внутреннее ожидание его не отпускает, — поэтому претендент честно
 * ждёт входа. Смена состояния на {@code WAITING} не выдаёт общий монитор, так что гонки нет.
 *
 * <p>Набор наблюдённых состояний:
 * {@code NEW} → {@code RUNNABLE} → {@code WAITING} → {@code RUNNABLE} → {@code TERMINATED}.
 */
public final class MonitorHolderThread extends LifecycleWorker {

    private final Object monitor;
    private final Object waitTarget = new Object();

    private final CountDownLatch sectionRequested = new CountDownLatch(1);
    private final CountDownLatch sectionPermit = new CountDownLatch(1);
    private final CountDownLatch insideSection = new CountDownLatch(1);
    private final CountDownLatch waitingInside = new CountDownLatch(1);

    public MonitorHolderThread(
            String name, RunnableWindow workWindow, RunnableWindow finishWindow, Object monitor, long safetyMillis) {
        super(name, workWindow, finishWindow, safetyMillis);
        this.monitor = monitor;
    }

    @Override
    protected void awaitWaitPhase() {
        sectionRequested.countDown();
        if (!awaitLatch(sectionPermit)) {
            return;
        }
        synchronized (monitor) {
            insideSection.countDown();
            synchronized (waitTarget) {
                waitingInside.countDown();
                try {
                    waitTarget.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Дождаться просьбы потока о входе в секцию. {@code false} — по таймауту. */
    public boolean awaitSectionRequested(long timeoutMillis) throws InterruptedException {
        return sectionRequested.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Разрешить вход в секцию — только после этого монитор окажется захвачен. */
    public void grantSectionPermit() {
        sectionPermit.countDown();
    }

    /** Дождаться подтверждения, что поток внутри секции и держит общий монитор. */
    public boolean awaitInsideSection(long timeoutMillis) throws InterruptedException {
        return insideSection.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Дождаться, пока держатель действительно уснёт во внутреннем ожидании.
     *
     * <p>Подтверждение нужно отдельно от {@link #awaitInsideSection(long)}: между входом в секцию и
     * вызовом {@code wait()} держатель ещё {@code RUNNABLE}, и читать состояние в этот момент —
     * значит поймать переход вместо состояния.
     */
    public boolean awaitWaitingInside(long timeoutMillis) throws InterruptedException {
        return waitingInside.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Отпустить секцию: держатель выходит, и общий монитор достаётся претенденту. */
    public void release() {
        synchronized (waitTarget) {
            waitTarget.notifyAll();
        }
    }
}
