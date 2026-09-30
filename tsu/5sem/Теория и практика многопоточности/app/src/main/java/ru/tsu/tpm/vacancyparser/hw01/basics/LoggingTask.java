package ru.tsu.tpm.vacancyparser.hw01.basics;

import java.util.concurrent.CountDownLatch;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

/**
 * Задача журналирования — второй способ создания потока: реализация {@link Runnable}.
 *
 * <p>Задача отделена от потока, поэтому один и тот же экземпляр может быть передан в
 * {@code new Thread(runnable, name)}. Имя потока здесь узнаётся только в момент выполнения —
 * через {@code Thread.currentThread()}.
 */
public class LoggingTask implements Runnable {

    private final int ordinal;
    private final ConsoleOutput console;
    private final CountDownLatch ready;
    private final CountDownLatch gate;

    /**
     * @param ordinal порядковый номер задачи
     * @param ready   оповещает сервис, что поток начал работу и дошёл до барьера
     * @param gate    барьер, удерживающий поток до открытия
     */
    public LoggingTask(int ordinal, ConsoleOutput console, CountDownLatch ready, CountDownLatch gate) {
        this.ordinal = ordinal;
        this.console = console;
        this.ready = ready;
        this.gate = gate;
    }

    public int ordinal() {
        return ordinal;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        console.info(threadName + " (задача №" + ordinal + ", реализует Runnable) начал работу");
        ready.countDown();
        awaitGate();
        console.info(threadName + " (задача №" + ordinal + ") записал событие в журнал");
        console.item("журнал потока " + threadName + " принял событие №" + ordinal);
    }

    private void awaitGate() {
        try {
            gate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
