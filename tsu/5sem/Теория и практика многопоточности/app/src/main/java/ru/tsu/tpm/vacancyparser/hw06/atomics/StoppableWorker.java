package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Рабочий поток, останавливаемый {@code volatile}-признаком завершения.
 *
 * <p>Признак {@code finished} объявлен {@code volatile}. Это ровно то, что требуется заданием: поток
 * проверяет флаг в цикле работы и завершается, когда флаг становится {@code true}. Без {@code volatile}
 * цикл проверки может превратиться в «зомби»-цикл (см. {@link NonVolatileFlagWorker}): JIT вправе
 * вынести чтение неизменяемого в этом потоке поля из цикла.
 *
 * <p><b>Конвенция флага обратная привычной.</b> Распространённый вид — {@code running}, где
 * {@code true} означает «работать». Текст задания требует противоположного: поток должен завершиться,
 * когда флаг станет {@code true}. Поэтому поле названо {@code finished}, и его значения такие:
 * {@code false} — поток работает (не завершён), {@code true} — потоку следует завершиться. Спорить с
 * формулировкой задания смысла нет: {@code volatile} даёт одинаковые гарантии при любом имени.
 *
 * <p>Счётчик {@code iterations} намеренно <b>не</b> {@code volatile}: он читается только после
 * {@code join()} того же потока, а {@code join()} сам устанавливает отношение «случилось-до», поэтому
 * отдельная синхронизация не нужна. Если сделать его {@code volatile}, цикл получит запись в
 * volatile-поле, и демонстрация дефекта видимости у {@link NonVolatileFlagWorker} потеряла бы смысл —
 * поэтому здесь этого делать нельзя.
 */
public final class StoppableWorker implements Runnable {

    private final CountDownLatch started = new CountDownLatch(1);

    /** Признак завершения: {@code false} — работать, {@code true} — завершиться. */
    private volatile boolean finished;

    /** Сколько итераций выполнил рабочий поток; читается после {@code join()}. */
    private long iterations;

    @Override
    public void run() {
        started.countDown();
        while (!finished) {
            iterations++;
        }
    }

    /** Установить признак завершения. Повторный вызов безопасен и ничего не меняет. */
    public void finish() {
        finished = true;
    }

    /** Текущее значение признака завершения. */
    public boolean isFinished() {
        return finished;
    }

    /** Число выполненных итераций. */
    public long iterations() {
        return iterations;
    }

    /** Дождаться старта рабочего цикла (поток успел войти в {@link #run()}). */
    public boolean awaitStarted(long timeoutMillis) throws InterruptedException {
        return started.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}
