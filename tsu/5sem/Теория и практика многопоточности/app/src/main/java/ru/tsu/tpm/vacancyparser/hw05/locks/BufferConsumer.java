package ru.tsu.tpm.vacancyparser.hw05.locks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Задача извлечения: забирает элементы из буфера до его закрытия.
 *
 * <p>Поток извлечения заканчивает работу по признаку {@code CLOSED}: это и есть тот случай, когда
 * адресного оповещения недостаточно и нужен {@code signalAll()} — «работа окончена» касается всех
 * ожидающих сразу. Все извлечённые элементы сохраняются: по ним проверяются инварианты «ничего не
 * потеряно» и «ничего не продублировано», а также распределение по потокам.
 */
public final class BufferConsumer implements Runnable {

    /** Срок одного ожидания: длинный, потому что нормальный выход — не тайм-аут, а закрытие буфера. */
    static final long POLL_TIMEOUT_MILLIS = 5_000L;

    private final Buffer<String> buffer;
    private final CountDownLatch startGate;
    private final List<String> taken = new ArrayList<>();
    private volatile BufferOperationException.Reason stopReason;

    BufferConsumer(Buffer<String> buffer, CountDownLatch startGate) {
        this.buffer = buffer;
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
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String item = buffer.poll(POLL_TIMEOUT_MILLIS);
                if (item == null) {
                    // Ничего не пришло за отведённое время: это не конец работы, продолжаем ждать.
                    continue;
                }
                taken.add(item);
            } catch (BufferOperationException e) {
                stopReason = e.reason();
                return;
            }
        }
    }

    /** Сколько элементов забрал этот поток. */
    int takenCount() {
        return taken.size();
    }

    /** Копия извлечённых элементов — для проверки инвариантов. */
    List<String> takenItems() {
        return List.copyOf(taken);
    }

    /** Почему извлечение прекратилось. */
    BufferOperationException.Reason stopReason() {
        return stopReason;
    }
}
