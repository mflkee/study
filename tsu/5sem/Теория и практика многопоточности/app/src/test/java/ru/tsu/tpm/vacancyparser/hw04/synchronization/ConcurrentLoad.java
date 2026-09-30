package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Общий харнесс для нагрузочных проверок: запускает потоки одновременно и дожидается их окончания.
 *
 * <p>Одновременный старт — не украшение, а условие эксперимента: если запускать потоки по одному,
 * следующий может успеть закончить до старта предыдущего, и гонка просто не возникнет. Общий
 * «затвор старта» гарантирует, что все потоки входят в нагрузку вместе.
 */
final class ConcurrentLoad {

    /** Сколько ждать поток нагрузки, прежде чем признать тест сломанным. */
    private static final long JOIN_TIMEOUT_MILLIS = 60_000L;

    private ConcurrentLoad() {
    }

    /** Запустить {@code threads} потоков с общей нагрузкой {@code body} и дождаться их. */
    static void run(int threads, Runnable body) throws InterruptedException {
        runIndexed(threads, index -> body.run());
    }

    /** Запустить {@code threads} потоков, передав каждому его индекс. */
    static void runIndexed(int threads, IntConsumer body) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(threads);
        List<Thread> workers = new ArrayList<>(threads);

        for (int i = 0; i < threads; i++) {
            int index = i;
            Thread worker = new Thread(() -> {
                ready.countDown();
                awaitQuietly(startGate);
                body.accept(index);
            }, "load-" + i);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }

        awaitQuietly(ready);
        startGate.countDown();

        for (Thread worker : workers) {
            worker.join(JOIN_TIMEOUT_MILLIS);
            if (worker.isAlive()) {
                throw new IllegalStateException("поток нагрузки не завершился: " + worker.getName());
            }
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(JOIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("поток нагрузки не дождался старта");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("поток нагрузки прерван", e);
        }
    }
}
