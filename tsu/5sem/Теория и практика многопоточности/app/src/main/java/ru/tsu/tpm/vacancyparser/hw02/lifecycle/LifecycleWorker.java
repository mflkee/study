package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Базовый демонстрационный поток ДЗ 2.
 *
 * <p>Задаёт общий каркас прохождения состояний, одинаковый для всех потоков демонстрации:
 *
 * <pre>
 *   NEW → [окно RUNNABLE] → фаза ожидания → [окно RUNNABLE] → TERMINATED
 * </pre>
 *
 * <p>Набор наблюдённых состояний у потока зависит только от того, реализована ли фаза
 * ожидания ({@link #awaitWaitPhase()}) и каким примитивом она удерживается. Никаких ожиданий
 * вне этих двух окон здесь нет, поэтому «лишних» состояний поток не приобретает.
 */
public abstract class LifecycleWorker extends Thread {

    /** Страховочный срок ожидания по умолчанию: истёк — идём дальше, а не виснем. */
    public static final long DEFAULT_SAFETY_MILLIS = 10_000L;

    private final RunnableWindow workWindow;
    private final RunnableWindow finishWindow;
    private final long safetyMillis;

    protected LifecycleWorker(
            String name, RunnableWindow workWindow, RunnableWindow finishWindow, long safetyMillis) {
        super(name);
        this.workWindow = workWindow;
        this.finishWindow = finishWindow;
        this.safetyMillis = safetyMillis;
    }

    /** Страховочный срок любого ожидания этого потока. */
    protected final long safetyMillis() {
        return safetyMillis;
    }

    /** Окно работы перед фазой ожидания — здесь наблюдается первый {@code RUNNABLE}. */
    protected RunnableWindow workWindow() {
        return workWindow;
    }

    /** Окно работы после освобождения — здесь наблюдается возврат в {@code RUNNABLE}. */
    protected RunnableWindow finishWindow() {
        return finishWindow;
    }

    @Override
    public final void run() {
        workWindow.hold();
        awaitWaitPhase();
        finishWindow.hold();
    }

    /**
     * Фаза ожидания потока.
     *
     * <p>Здесь и только здесь поток приобретает состояние, специфичное для своей роли:
     * {@code TIMED_WAITING}, {@code WAITING} или {@code BLOCKED}. Реализация обязана уйти из
     * метода при любом исходе — иначе поток не дойдёт до {@code TERMINATED}.
     */
    protected abstract void awaitWaitPhase();

    /** Ожидание latch'а с таймаутом: истёк срок — возвращаем управление, а не блокируемся. */
    protected final boolean awaitLatch(CountDownLatch latch) {
        try {
            return latch.await(safetyMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
