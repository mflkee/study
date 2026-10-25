package ru.tsu.tpm.vacancyparser.hw13.queue.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.UrlTask;

/**
 * Ограниченная очередь задач между производителями и потребителями.
 *
 * <p>{@link LinkedBlockingQueue} с <b>обязательной</b> ёмкостью: именно ограничение создаёт обратное
 * давление. С неограниченной очередью производитель «выстреливает» все адреса мгновенно, очередь
 * раздувается до размера входного списка, и память становится заложником разницы скоростей. Здесь
 * {@code put} блокируется, когда буфер полон, и производитель ждёт, пока потребитель освободит место.
 *
 * <p>Наблюдения (глубина, число блокировок, суммарное время ожидания) нужны отчёту: по ним видно, что
 * очередь действительно заполнялась и что обратное давление работало. Счётчики атомарные: пишут
 * несколько потоков одновременно.
 *
 * <p>Остановка — «таблетки» {@link UrlTask#POISON}: производители, закончив, кладут по одной на каждого
 * потребителя. Это надёжнее флага «работа окончена»: пока в очереди есть задачи, потребитель обязан их
 * обработать, а сигнал стоит в очереди <b>после</b> них, поэтому ни одна задача не теряется.
 */
public final class TaskQueue {

    private final BlockingQueue<UrlTask> queue;
    private final int capacity;
    private final AtomicInteger maxDepth = new AtomicInteger();
    private final LongAdder blockedPuts = new LongAdder();
    private final LongAdder blockedPutNanos = new LongAdder();
    private final AtomicInteger poisonPills = new AtomicInteger();

    /** Создать очередь с ограниченной ёмкостью. */
    public TaskQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("ёмкость очереди должна быть положительной, получено " + capacity);
        }
        this.capacity = capacity;
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    /** Положить задачу, при необходимости подождав освобождения места. */
    public void put(UrlTask task) throws InterruptedException {
        if (task == null) {
            throw new IllegalArgumentException("задача не задана");
        }
        long start = System.nanoTime();
        queue.put(task);
        long waited = System.nanoTime() - start;
        if (waited > TimeUnit.MILLISECONDS.toNanos(1)) {
            blockedPuts.increment();
            blockedPutNanos.add(waited);
        }
        updateMaxDepth();
    }

    /** Взять задачу, при необходимости подождав появления. */
    public UrlTask take() throws InterruptedException {
        return queue.take();
    }

    /** Положить по «таблетке» на каждого потребителя. */
    public void poisonAll(int consumers) throws InterruptedException {
        if (consumers < 0) {
            throw new IllegalArgumentException("число потребителей не может быть отрицательным");
        }
        for (int index = 0; index < consumers; index++) {
            queue.put(UrlTask.POISON);
            poisonPills.incrementAndGet();
        }
        updateMaxDepth();
    }

    /** Текущая глубина очереди. */
    public int depth() {
        return queue.size();
    }

    /** Ёмкость очереди. */
    public int capacity() {
        return capacity;
    }

    /** Максимальная наблюдённая глубина. */
    public int maxDepth() {
        return maxDepth.get();
    }

    /** Сколько раз {@code put} ждал освобождения места. */
    public long blockedPuts() {
        return blockedPuts.sum();
    }

    /** Суммарное время ожидания в {@code put}, миллисекунды. */
    public double blockedPutMillis() {
        return blockedPutNanos.sum() / 1_000_000.0;
    }

    /** Сколько «таблеток» положено в очередь. */
    public int poisonPills() {
        return poisonPills.get();
    }

    private void updateMaxDepth() {
        int current = queue.size();
        while (true) {
            int observed = maxDepth.get();
            if (current <= observed || maxDepth.compareAndSet(observed, current)) {
                return;
            }
        }
    }
}
