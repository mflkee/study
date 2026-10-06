package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <b>Учебный неверный вариант:</b> тот же рабочий цикл, но признак завершения объявлен <b>без</b>
 * {@code volatile}.
 *
 * <p>Класс существует только ради демонстрации дефекта видимости. Отличие от {@link StoppableWorker} —
 * ровно один модификатор, и этого достаточно, чтобы программа перестала работать: цикл
 * {@code while (!finished)} не содержит ни синхронизации, ни вызовов, а {@code finished} в этом
 * потоке не изменяется. Компилятор JIT вправе решить, что значение поля неизменно на протяжении
 * цикла, вынести чтение из цикла (hoistable loop-invariant load) и превратить цикл в бесконечный —
 * поток никогда не увидит, что главный поток установил флаг.
 *
 * <p>Дефект воспроизводится устойчиво и не является «теоретическим»: именно поэтому рабочий код
 * обязан объявлять такой флаг {@code volatile}. Никогда не используется как рабочая реализация.
 */
public final class NonVolatileFlagWorker implements Runnable {

    private final CountDownLatch started = new CountDownLatch(1);

    /** Признак завершения без {@code volatile} — намеренный дефект видимости. */
    private boolean finished;

    /** Счётчик итераций; читается после {@code join()} (если поток вообще завершился). */
    private long iterations;

    @Override
    public void run() {
        started.countDown();
        while (!finished) {
            iterations++;
        }
    }

    /** Установить признак завершения. */
    public void finish() {
        finished = true;
    }

    /** Число выполненных итераций. */
    public long iterations() {
        return iterations;
    }

    /** Дождаться старта рабочего цикла. */
    public boolean awaitStarted(long timeoutMillis) throws InterruptedException {
        return started.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}
