package ru.tsu.tpm.vacancyparser.hw01.basics;

import java.util.concurrent.CountDownLatch;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

/**
 * Задача счётчика — первый способ создания потока: наследование от {@link Thread}.
 *
 * <p>Поток сам является задачей, поэтому его состояние и результат работы живут в одном
 * объекте. Номер задаётся конструктором, печатается вместе с именем выполняющего потока.
 */
public class CountingTask extends Thread {

    private final int ordinal;
    private final ConsoleOutput console;
    private final CountDownLatch ready;
    private final CountDownLatch gate;

    /**
     * @param name    доменное имя потока, а не сгенерированное JVM {@code Thread-N}
     * @param ordinal порядковый номер задачи
     * @param ready   оповещает сервис, что поток начал работу и дошёл до барьера
     * @param gate    барьер, удерживающий поток до открытия
     */
    public CountingTask(String name, int ordinal, ConsoleOutput console, CountDownLatch ready, CountDownLatch gate) {
        super(name);
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
        console.info(threadName + " (задача №" + ordinal + ", создан наследованием от Thread) начал работу");
        ready.countDown();
        awaitGate();
        console.info(threadName + " (задача №" + ordinal + ") обработал элемент и увеличил счётчик");
        console.item("счётчик потока " + threadName + " = " + ordinal);
    }

    private void awaitGate() {
        try {
            gate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
