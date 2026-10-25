package ru.tsu.tpm.vacancyparser.hw11.forkjoin.count;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Счётчики выполнения задачи: сколько партий и задач создано, на каких потоках шла работа.
 *
 * <p>Данные нужны отчёту, а не логике подсчёта, поэтому они вынесены в отдельный объект и меняются
 * атомарно: подзадачи выполняются одновременно, и обычный {@code int} здесь потерял бы часть
 * инкрементов. Именно этот объект позволяет сказать «партий 200, задач 399» и подтвердить, что работу
 * выполняли разные потоки пула.
 */
public final class CounterStats {

    private final AtomicInteger batches = new AtomicInteger();
    private final AtomicInteger tasks = new AtomicInteger();
    private final Set<String> threads = ConcurrentHashMap.newKeySet();

    /** Создана новая задача (лист или внутренний узел). */
    public void taskCreated() {
        tasks.incrementAndGet();
    }

    /** Задача дошла до листа и считает свою партию. */
    public void batchStarted() {
        batches.incrementAndGet();
    }

    /** Зафиксировать поток, выполняющий подсчёт. */
    public void threadUsed() {
        threads.add(Thread.currentThread().getName());
    }

    public int batches() {
        return batches.get();
    }

    public int tasks() {
        return tasks.get();
    }

    public Set<String> threads() {
        return Set.copyOf(threads);
    }
}
