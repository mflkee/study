package ru.tsu.tpm.vacancyparser.hw09.callable.cancel;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Длительная задача, которая умеет быть отменённой.
 *
 * <p>Задача имитирует обработку в 5–10 секунд, выполняя работу **шагами** с прерываемым ожиданием
 * ({@link Thread#sleep} в шаге бросает {@link InterruptedException} при прерывании). Это принципиально:
 * {@code Future.cancel(true)} лишь посылает прерывание, и если задача его игнорирует, она досчитает до
 * конца, хотя {@code isCancelled()} вернёт {@code true}. Здесь задача, получив прерывание, прекращает
 * работу и возвращает достигнутый прогресс.
 *
 * <p>Прогресс, факт естественного завершения и факт прерывания наблюдаемы снаружи — по ним тест
 * проверяет, что отмена действительно остановила работу, а не только выставила флаг.
 */
public final class LongTask implements Callable<Long> {

    private final long totalMillis;
    private final long stepMillis;
    private final CountDownLatch started = new CountDownLatch(1);
    private final AtomicLong progress = new AtomicLong();
    private final AtomicBoolean interrupted = new AtomicBoolean();
    private final AtomicBoolean completedNaturally = new AtomicBoolean();

    /**
     * @param totalMillis сколько всего «работать»
     * @param stepMillis  длительность одного шага (шаг — точка проверки прерывания)
     */
    public LongTask(long totalMillis, long stepMillis) {
        if (totalMillis <= 0) {
            throw new IllegalArgumentException("длительность задачи должна быть положительной");
        }
        if (stepMillis <= 0) {
            throw new IllegalArgumentException("шаг задачи должен быть положительным");
        }
        this.totalMillis = totalMillis;
        this.stepMillis = stepMillis;
    }

    @Override
    public Long call() {
        started.countDown();
        long elapsed = 0L;
        try {
            while (elapsed < totalMillis) {
                Thread.sleep(stepMillis);
                elapsed += stepMillis;
                progress.set(progress.get() + 1);
            }
            completedNaturally.set(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            interrupted.set(true);
        }
        return progress.get();
    }

    /** Дождаться начала выполнения задачи. */
    public boolean awaitStarted(long timeoutMillis) throws InterruptedException {
        return started.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Сколько шагов задача успела выполнить. */
    public long progress() {
        return progress.get();
    }

    /** Всего шагов при полном выполнении. */
    public long totalSteps() {
        return (totalMillis + stepMillis - 1) / stepMillis;
    }

    /** Была ли задача прервана. */
    public boolean wasInterrupted() {
        return interrupted.get();
    }

    /** Завершилась ли задача сама, без прерывания. */
    public boolean completedNaturally() {
        return completedNaturally.get();
    }
}
