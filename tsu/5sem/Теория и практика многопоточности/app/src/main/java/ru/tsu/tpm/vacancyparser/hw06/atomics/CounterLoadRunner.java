package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Общий харнесс нагрузки для ДЗ 6: запуск потоков по общему старту и ожидание завершения.
 *
 * <p>Общий старт обязателен: при последовательном запуске поток успевает закончить раньше, чем
 * стартует следующий, и конкуренция попросту не возникает. Все потоки демонстрации получают имена с
 * префиксом {@code hw06-}, чтобы после прогона можно было проверить фактом, что живых потоков не
 * осталось.
 */
public final class CounterLoadRunner {

    /** Префикс имён потоков нагрузки ДЗ 6. */
    public static final String THREAD_PREFIX = "hw06-load-";

    /** Предел ожидания готовности и завершения потоков. */
    private static final long GATE_TIMEOUT_SECONDS = 60L;
    private static final long JOIN_TIMEOUT_MILLIS = 120_000L;

    private CounterLoadRunner() {
    }

    /** Запустить {@code threads} потоков с общей стартовой точкой и дождаться их завершения. */
    public static void runThreads(int threads, IntConsumer body) throws InterruptedException {
        if (threads < 1) {
            throw new IllegalArgumentException("нужен хотя бы один поток нагрузки");
        }
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(threads);
        List<Thread> workers = new ArrayList<>(threads);

        for (int i = 0; i < threads; i++) {
            int index = i;
            Thread worker = new Thread(() -> {
                ready.countDown();
                try {
                    if (!startGate.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                body.accept(index);
            }, THREAD_PREFIX + i);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }

        if (!ready.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("потоки нагрузки не успели подготовиться");
        }
        startGate.countDown();
        for (Thread worker : workers) {
            worker.join(JOIN_TIMEOUT_MILLIS);
        }
    }
}
