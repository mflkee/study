package ru.tsu.tpm.vacancyparser.hw09.callable;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Фабрика потоков исполнителей ДЗ 9 с доменными именами.
 *
 * <p>Потоки не демоны намеренно: незакрытый исполнитель должен быть заметен (процесс не завершится), а
 * не маскироваться демон-потоками. Поэтому каждый исполнитель в этом ДЗ обязательно останавливается.
 * Осмысленные имена нужны для читаемости дампа потоков (приём из ДЗ 1 и ДЗ 7).
 */
public final class WorkerThreads {

    private WorkerThreads() {
    }

    /** Фабрика потоков с указанным префиксом и уникальным номером. */
    public static ThreadFactory factory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }
}
