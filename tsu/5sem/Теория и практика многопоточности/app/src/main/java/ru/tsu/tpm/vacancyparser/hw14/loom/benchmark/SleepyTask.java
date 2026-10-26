package ru.tsu.tpm.vacancyparser.hw14.loom.benchmark;

import java.util.concurrent.CancellationException;
import java.util.function.IntFunction;

/**
 * Задача, которая только ждёт: имитация блокирующего IO без сети.
 *
 * <p>{@link Thread#sleep(long)} — самый удобный способ изобразить ожидание: поток не занимает
 * процессор, но и не может продолжать работу. Для виртуального потока это означает «сними меня с
 * carrier-потока и дай поработать другим», для платформенного — «поток ОС спит и недоступен».
 * Именно на таком сценарии видно, что виртуальные потоки масштабируются на ожидании, а не на CPU.
 */
public final class SleepyTask implements IntFunction<Integer> {

    private final long millis;

    public SleepyTask(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("задержка не может быть отрицательной");
        }
        this.millis = millis;
    }

    @Override
    public Integer apply(int index) {
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("задача №" + index + " прервана");
            }
        }
        return index;
    }

    /** Задержка в миллисекундах. */
    public long millis() {
        return millis;
    }
}
