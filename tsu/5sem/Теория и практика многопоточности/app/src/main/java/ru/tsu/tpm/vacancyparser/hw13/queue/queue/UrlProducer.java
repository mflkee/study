package ru.tsu.tpm.vacancyparser.hw13.queue.queue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.UrlTask;

/**
 * Производитель: кладёт свои адреса в очередь и ждёт, если она полна.
 *
 * <p>Своя доля адресов у каждого производителя (разбиение делает {@code CrawlServer}), поэтому
 * координация между ними не нужна — только общий счётчик «сколько производителей ещё работает».
 * Последний ушедший кладёт по сигналу остановки на каждого потребителя: до этого момента очередь
 * содержит все оставшиеся задачи, и потребители не завершатся раньше времени.
 *
 * <p>Сигналы кладутся в {@code finally}: если производитель прерван или упал, конвейер всё равно должен
 * завершиться, а не оставить потребителей ждать вечно.
 */
public final class UrlProducer implements Runnable {

    private final TaskQueue queue;
    private final List<UrlTask> tasks;
    private final AtomicInteger producersLeft;
    private final int consumers;
    private final AtomicInteger produced;
    private final Consumer<String> trace;

    /**
     * @param queue          общая очередь
     * @param tasks          доля адресов этого производителя
     * @param producersLeft  общий счётчик ещё работающих производителей
     * @param consumers      сколько потребителей нужно остановить
     * @param produced       общий счётчик положенных задач
     * @param trace          журнал (может быть {@code null})
     */
    public UrlProducer(
            TaskQueue queue,
            List<UrlTask> tasks,
            AtomicInteger producersLeft,
            int consumers,
            AtomicInteger produced,
            Consumer<String> trace) {
        this.queue = queue;
        this.tasks = List.copyOf(tasks);
        this.producersLeft = producersLeft;
        this.consumers = consumers;
        this.produced = produced;
        this.trace = trace;
    }

    @Override
    public void run() {
        try {
            for (UrlTask task : tasks) {
                queue.put(task);
                produced.incrementAndGet();
                log("положил " + task.reportLine());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("прерван, положил не всё");
        } finally {
            if (producersLeft.decrementAndGet() == 0) {
                try {
                    queue.poisonAll(consumers);
                    log("последний производитель: отправлено сигналов остановки " + consumers);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log("не удалось отправить сигналы остановки: прерван");
                }
            }
        }
    }

    private void log(String message) {
        if (trace != null) {
            trace.accept("[" + Thread.currentThread().getName() + "] " + message);
        }
    }
}
