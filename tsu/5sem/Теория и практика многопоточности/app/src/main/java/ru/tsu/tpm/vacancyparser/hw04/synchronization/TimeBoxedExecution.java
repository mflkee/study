package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Выполнение задачи в ограниченном по времени окне.
 *
 * <p>Страховка демонстрации: если защита где-то не сработала и поток завис, процесс не должен
 * висеть вместе с ним. По истечении лимита задача прерывается, а вызывающий код получает признак
 * «не уложились» и печатает дамп потоков вместо бесконечного ожидания.
 */
public final class TimeBoxedExecution {

    /**
     * Итог выполнения.
     *
     * @param completed уложилась ли задача в лимит
     * @param failure   исключение, которым задача завершилась (если завершилась неуспешно)
     */
    public record Outcome(boolean completed, Throwable failure) {
    }

    private TimeBoxedExecution() {
    }

    /** Выполнить задачу, ожидая не дольше {@code limitMillis}. */
    public static Outcome run(long limitMillis, Runnable task) throws InterruptedException {
        if (limitMillis <= 0) {
            throw new IllegalArgumentException("лимит времени должен быть положительным");
        }

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                task.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "time-boxed-task");
        worker.setDaemon(true);
        worker.start();
        worker.join(limitMillis);

        if (worker.isAlive()) {
            worker.interrupt();
            return new Outcome(false, null);
        }
        return new Outcome(true, failure.get());
    }
}
