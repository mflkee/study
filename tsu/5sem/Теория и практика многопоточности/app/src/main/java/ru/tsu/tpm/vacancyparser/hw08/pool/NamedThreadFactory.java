package ru.tsu.tpm.vacancyparser.hw08.pool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Фабрика потоков пула с доменными именами {@code http-worker-<N>}.
 *
 * <p>Без неё в дампе потоков (см. ДЗ 7) видны технические {@code pool-1-thread-7}, и по ним нельзя
 * понять, чей это поток. Имя с уникальным номером от атомарного счётчика делает дамп читаемым: видно,
 * какие потоки пула заняты запросами.
 *
 * <p>Потоки — не демоны намеренно: пул обязан быть остановлен явно, и забытый {@code shutdown()} должен
 * быть заметен (процесс не завершится), а не маскироваться демон-потоками.
 */
public final class NamedThreadFactory implements ThreadFactory {

    /** Префикс имени потока пула. */
    public static final String PREFIX = "http-worker-";

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, PREFIX + counter.incrementAndGet());
        thread.setDaemon(false);
        return thread;
    }
}
