package ru.tsu.tpm.vacancyparser.hw09.callable.scheduled;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import ru.tsu.tpm.vacancyparser.hw09.callable.WorkerThreads;

/**
 * Периодическая фоновая операция на {@link ScheduledExecutorService}.
 *
 * <p>Операция запускается через {@code scheduleAtFixedRate} с заданным периодом и считает циклы.
 * Особенность {@code scheduleAtFixedRate} в том, что **необработанное исключение внутри задачи молча
 * прекращает расписание**. Поэтому тело задачи ловит сбой: цикл учитывается, сбой отдельно считается, а
 * следующие циклы продолжаются. Это же поведение затем применяется в агрегаторе.
 *
 * <p>Обязательна остановка: иначе периодическая задача работала бы вечно и не давала процессу завершиться.
 */
public final class BackgroundMonitor implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final AtomicLong cycles = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private volatile Runnable operation = () -> {
    };

    /** Создать монитор с заданным префиксом имени потока. */
    public BackgroundMonitor(String threadNamePrefix) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(WorkerThreads.factory(threadNamePrefix));
    }

    /** Задать имитируемую фоновую операцию (может выбрасывать исключение). */
    public void setOperation(Runnable operation) {
        this.operation = operation == null ? () -> {
        } : operation;
    }

    /** Запустить периодическое выполнение с периодом {@code periodMillis}. */
    public void start(long periodMillis) {
        if (periodMillis <= 0) {
            throw new IllegalArgumentException("период должен быть положительным");
        }
        scheduler.scheduleAtFixedRate(() -> {
            cycles.incrementAndGet();
            try {
                operation.run();
            } catch (RuntimeException e) {
                // Сбой одного выполнения не должен останавливать расписание.
                failures.incrementAndGet();
            }
        }, 0L, periodMillis, TimeUnit.MILLISECONDS);
    }

    /** Сколько раз задача начала выполнение. */
    public long cycles() {
        return cycles.get();
    }

    /** Сколько выполнений завершилось сбоем. */
    public long failures() {
        return failures.get();
    }

    /** Дождаться не менее {@code count} выполнений. */
    public boolean awaitCycles(long count, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (cycles.get() < count) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(5L);
        }
        return true;
    }

    /** Остановить периодическую задачу: инициация, ожидание с таймаутом, принудительное завершение. */
    public void stop(long timeoutMillis) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    /** Завершён ли исполнитель периодической задачи. */
    public boolean isTerminated() {
        return scheduler.isTerminated();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
