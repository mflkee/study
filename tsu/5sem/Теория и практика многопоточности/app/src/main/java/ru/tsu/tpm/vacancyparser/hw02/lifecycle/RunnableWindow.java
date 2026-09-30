package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Окно гарантированного {@link Thread.State#RUNNABLE}.
 *
 * <p>Смысл: {@code RUNNABLE} нельзя «подержать» через {@code sleep} или {@code wait} — это
 * состояние по умолчанию для потока, который исполняет код. Единственная корректная точка
 * наблюдения — середина работы. Чтобы наблюдатель гарантированно попал в эту середину,
 * границы участка фиксируются двумя latch'ами:
 *
 * <ol>
 *   <li>поток вызывает {@link #hold()} и начитывает {@code started} — «я в работе»;</li>
 *   <li>наблюдатель читает {@code getState()} и только затем вызывает {@link #open()};</li>
 *   <li>поток, увидев открытый затвор, выходит из участка и идёт дальше.</li>
 * </ol>
 *
 * <p>Пока затвор закрыт, поток крутит настоящий цикл вычислений, а не простаивает, — поэтому
 * {@code getState()} возвращает именно {@code RUNNABLE}, а не «спящий» статус.
 *
 * <p>Страховочный срок {@code safetyMillis} — вторая половина гарантии из design D6: даже если
 * наблюдатель по какой-то причине не откроет затвор, участок работы прервётся сам, и поток не
 * заблокирует завершение приложения.
 */
public final class RunnableWindow {

    private final String label;
    private final long safetyMillis;

    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch opened = new CountDownLatch(1);

    private long checksum;

    public RunnableWindow(String label, long safetyMillis) {
        this.label = label;
        this.safetyMillis = safetyMillis;
    }

    /**
     * Занимает поток участком работы. Вызывается самим потоком в начале {@code run()}.
     *
     * <p>Возвращает управление, когда затвор открыт либо истёк страховочный срок.
     */
    public void hold() {
        started.countDown();

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(safetyMillis);
        long acc = 0L;
        while (opened.getCount() > 0 && System.nanoTime() < deadline) {
            for (int i = 1; i <= 512; i++) {
                acc = acc * 31L + i;
            }
            Thread.onSpinWait();
        }
        checksum = acc;
    }

    /** Дождаться, пока поток войдёт в участок работы. {@code false} — по таймауту. */
    public boolean awaitStarted(long timeoutMillis) throws InterruptedException {
        return started.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Открыть затвор: поток выходит из участка работы и идёт к следующей фазе. */
    public void open() {
        opened.countDown();
    }

    /**
     * Контрольная сумма вычислений участка.
     *
     * <p>Существует, чтобы цикл в {@link #hold()} не был выброшен JIT-компилятором как мёртвый.
     */
    public long checksum() {
        return checksum;
    }

    @Override
    public String toString() {
        return "RunnableWindow[" + label + "]";
    }
}
