package ru.tsu.tpm.vacancyparser.hw05.locks;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Задача добавления: кладёт подготовленные элементы в буфер.
 *
 * <p>Все добавляющие потоки стартуют по общему затвору — так нагрузка действительно параллельная,
 * а не «по очереди». Ошибки не проглатываются: причина отказа сохраняется, и тест/демонстрация
 * видят, что именно помешало (срок ожидания, закрытие буфера или прерывание).
 */
public final class BufferProducer implements Runnable {

    private final Buffer<String> buffer;
    private final List<String> items;
    private final CountDownLatch startGate;
    private final AtomicInteger producedCount = new AtomicInteger();
    private volatile BufferOperationException.Reason stopReason;

    BufferProducer(Buffer<String> buffer, List<String> items, CountDownLatch startGate) {
        this.buffer = buffer;
        this.items = List.copyOf(items);
        this.startGate = startGate;
    }

    @Override
    public void run() {
        try {
            startGate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        for (String item : items) {
            try {
                buffer.put(item);
                producedCount.incrementAndGet();
            } catch (BufferOperationException e) {
                stopReason = e.reason();
                return;
            }
        }
    }

    /** Сколько элементов действительно удалось положить. */
    int producedCount() {
        return producedCount.get();
    }

    /** Почему добавление прекратилось досрочно, если прекратилось. */
    BufferOperationException.Reason stopReason() {
        return stopReason;
    }
}
