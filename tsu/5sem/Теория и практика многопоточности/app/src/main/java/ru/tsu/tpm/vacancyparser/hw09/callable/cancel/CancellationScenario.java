package ru.tsu.tpm.vacancyparser.hw09.callable.cancel;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import ru.tsu.tpm.vacancyparser.hw09.callable.WorkerThreads;

/**
 * Сценарий отмены длительной задачи через {@link Future} и {@link FutureTask}.
 *
 * <p>Задача оборачивается в {@code FutureTask}, отправляется в пул потоков, и по истечении заданной
 * задержки отменяется {@code cancel(true)}. Сценарий возвращает наблюдаемые факты: отменена ли задача,
 * стал ли недоступен результат, действительно ли задача прекратила работу (по прерыванию) и завершился
 * ли исполнитель. Отдельно проверяется обратный случай — задача успела завершиться до отмены.
 */
public final class CancellationScenario {

    /** Шаг задачи по умолчанию: достаточно короткий, чтобы отмена сработала быстро. */
    public static final long DEFAULT_STEP_MILLIS = 100L;

    /** Префикс имени потока-исполнителя. */
    public static final String THREAD_PREFIX = "hw09-cancel-";

    /**
     * Итог сценария.
     *
     * @param started             задача успела начать работу
     * @param cancelled           {@code cancel()} сообщил об отмене
     * @param resultUnavailable   результат после отмены недоступен
     * @param interrupted         задача прекратила работу по прерыванию
     * @param completedNaturally  задача успела завершиться сама (без прерывания)
     * @param progressAtCancel    прогресс на момент отмены
     * @param totalSteps          полный прогресс при завершении
     * @param result              результат, если задача завершилась сама; иначе {@code null}
     * @param executorTerminated  исполнитель завершился
     */
    public record CancelOutcome(
            boolean started,
            boolean cancelled,
            boolean resultUnavailable,
            boolean interrupted,
            boolean completedNaturally,
            long progressAtCancel,
            long totalSteps,
            Long result,
            boolean executorTerminated) {
    }

    private CancellationScenario() {
    }

    /** Отправить длительную задачу и отменить её через {@code cancelAfterMillis}. */
    public static CancelOutcome runCancelled(long taskMillis, long cancelAfterMillis, long stepMillis)
            throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor(WorkerThreads.factory(THREAD_PREFIX));
        try {
            LongTask task = new LongTask(taskMillis, stepMillis);
            FutureTask<Long> futureTask = new FutureTask<>(task);
            pool.execute(futureTask);
            Future<Long> future = futureTask;

            boolean started = task.awaitStarted(2_000L);
            Thread.sleep(cancelAfterMillis);
            long progressAtCancel = task.progress();

            boolean cancelled = future.cancel(true);
            boolean resultUnavailable = resultIsUnavailable(future);

            pool.shutdown();
            boolean terminated = pool.awaitTermination(5, TimeUnit.SECONDS);

            return new CancelOutcome(
                    started,
                    cancelled,
                    resultUnavailable,
                    task.wasInterrupted(),
                    task.completedNaturally(),
                    progressAtCancel,
                    task.totalSteps(),
                    null,
                    terminated);
        } finally {
            if (!pool.isShutdown()) {
                pool.shutdownNow();
            }
        }
    }

    /** Дождаться естественного завершения задачи, затем попытаться отменить её. */
    public static CancelOutcome runCompleted(long taskMillis, long stepMillis) throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor(WorkerThreads.factory(THREAD_PREFIX));
        try {
            LongTask task = new LongTask(taskMillis, stepMillis);
            FutureTask<Long> futureTask = new FutureTask<>(task);
            pool.execute(futureTask);
            Future<Long> future = futureTask;
            task.awaitStarted(2_000L);

            Long result = null;
            try {
                result = future.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                // результат не получен — сценарий отметит это как отсутствие результата
            }
            boolean cancelled = future.cancel(true);

            pool.shutdown();
            boolean terminated = pool.awaitTermination(5, TimeUnit.SECONDS);

            return new CancelOutcome(
                    true,
                    cancelled,
                    future.isCancelled(),
                    task.wasInterrupted(),
                    task.completedNaturally(),
                    task.progress(),
                    task.totalSteps(),
                    result,
                    terminated);
        } finally {
            if (!pool.isShutdown()) {
                pool.shutdownNow();
            }
        }
    }

    private static boolean resultIsUnavailable(Future<Long> future) {
        try {
            future.get(1, TimeUnit.SECONDS);
            return false;
        } catch (CancellationException e) {
            return true;
        } catch (ExecutionException | TimeoutException e) {
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
