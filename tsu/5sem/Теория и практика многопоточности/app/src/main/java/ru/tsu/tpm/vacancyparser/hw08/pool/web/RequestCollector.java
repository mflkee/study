package ru.tsu.tpm.vacancyparser.hw08.pool.web;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import ru.tsu.tpm.vacancyparser.hw08.pool.HttpWorkerPool;

/**
 * Подача задач в пул и потокобезопасный сбор результатов «один результат на один адрес».
 *
 * <p>Задача на каждый адрес отправляется в общий пул; результат пишется в массив по индексу адреса.
 * Индекс уникален и принадлежит одному адресу, поэтому гонки нет даже без дополнительной
 * синхронизации, а порядок сводки совпадает с порядком входного списка без сортировки. Ожидание всех
 * результатов — через {@link CountDownLatch}.
 *
 * <p>По мере завершения запросов печатается прогресс «выполнено N из M» (если задан приёмник строк).
 */
public final class RequestCollector {

    private final HttpWorkerPool pool;
    private final HttpRequester requester;
    private final Consumer<String> progress;

    /** Создать сборщик. {@code progress} может быть {@code null} — тогда прогресс не печатается. */
    public RequestCollector(HttpWorkerPool pool, HttpRequester requester, Consumer<String> progress) {
        this.pool = pool;
        this.requester = requester;
        this.progress = progress;
    }

    /** Обработать список адресов и вернуть результаты в порядке списка. */
    public RunResult run(List<String> urls) throws InterruptedException {
        int total = urls.size();
        RequestResult[] results = new RequestResult[total];
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(total);

        long start = System.nanoTime();
        for (int index = 0; index < total; index++) {
            int slot = index;
            String url = urls.get(index);
            pool.executor().execute(() -> {
                try {
                    results[slot] = requester.fetch(url);
                } catch (RuntimeException e) {
                    results[slot] = RequestResult.rejected(url, "отказ выполнения: " + e.getMessage());
                } finally {
                    int finished = completed.incrementAndGet();
                    if (progress != null) {
                        progress.accept("выполнено " + finished + " из " + total);
                    }
                    done.countDown();
                }
            });
        }

        done.await();
        long runNanos = System.nanoTime() - start;
        return new RunResult(Arrays.asList(results), runNanos, pool.rejectionCount());
    }
}
