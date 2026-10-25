package ru.tsu.tpm.vacancyparser.hw13.queue.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.PageResult;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.UrlTask;
import ru.tsu.tpm.vacancyparser.hw13.queue.queue.PageConsumer;
import ru.tsu.tpm.vacancyparser.hw13.queue.queue.TaskQueue;
import ru.tsu.tpm.vacancyparser.hw13.queue.queue.UrlProducer;
import ru.tsu.tpm.vacancyparser.hw13.queue.report.CrawlReport;
import ru.tsu.tpm.vacancyparser.hw13.queue.web.PageSource;

/**
 * «Сервер задач»: поднимает производителей и потребителей на общей ограниченной очереди.
 *
 * <p>Собирает всё вместе и отвечает за две вещи, которые легко сделать неправильно. Первая —
 * <b>порядок запуска</b>: сначала потребители, потом производители, иначе быстрый производитель успел бы
 * заполнить небольшую очередь и заблокироваться на пустом конвейере. Вторая — <b>время</b>: ожидание
 * потоков ограничено, и если кто-то не завершился, его прерывают, а результат помечается как
 * незавершённый. Зависшая демонстрация хуже неудачной.
 *
 * <p>Потоки обычные (не демоны) и именованные: {@code hw13-producer-N} и {@code hw13-consumer-N}.
 * По именам в отчёте видно, кто какую страницу обработал и что работу разбирали все потребители.
 */
public final class CrawlServer {

    /** Префикс имён производителей. */
    public static final String PRODUCER_PREFIX = "hw13-producer-";

    /** Префикс имён потребителей. */
    public static final String CONSUMER_PREFIX = "hw13-consumer-";

    private CrawlServer() {
    }

    /**
     * Выполнить один прогон.
     *
     * @param label                название варианта для отчёта
     * @param urls                 адреса страниц
     * @param producers            число производителей
     * @param consumers            число потребителей
     * @param queueCapacity        ёмкость очереди
     * @param source               источник страницы
     * @param trace                журнал (может быть {@code null})
     * @param shutdownTimeoutMillis сколько ждать завершения потоков
     */
    public static CrawlReport run(
            String label,
            List<String> urls,
            int producers,
            int consumers,
            int queueCapacity,
            PageSource source,
            Consumer<String> trace,
            long shutdownTimeoutMillis) throws InterruptedException {
        if (urls == null) {
            throw new IllegalArgumentException("список адресов не задан");
        }
        if (producers < 1 || consumers < 1) {
            throw new IllegalArgumentException("нужен хотя бы один производитель и один потребитель");
        }
        if (shutdownTimeoutMillis <= 0) {
            throw new IllegalArgumentException("таймаут завершения должен быть положительным");
        }

        List<UrlTask> tasks = new ArrayList<>(urls.size());
        for (int index = 0; index < urls.size(); index++) {
            tasks.add(new UrlTask(urls.get(index), index));
        }

        TaskQueue queue = new TaskQueue(queueCapacity);
        ConcurrentLinkedQueue<PageResult> results = new ConcurrentLinkedQueue<>();
        AtomicInteger produced = new AtomicInteger();
        AtomicInteger producersLeft = new AtomicInteger(producers);

        List<Thread> consumersThreads = new ArrayList<>(consumers);
        for (int index = 1; index <= consumers; index++) {
            String name = CONSUMER_PREFIX + index;
            consumersThreads.add(new Thread(new PageConsumer(queue, source, results, trace), name));
        }
        List<Thread> producersThreads = new ArrayList<>(producers);
        for (int index = 1; index <= producers; index++) {
            String name = PRODUCER_PREFIX + index;
            List<UrlTask> share = share(tasks, index - 1, producers);
            producersThreads.add(new Thread(
                    new UrlProducer(queue, share, producersLeft, consumers, produced, trace), name));
        }

        long start = System.nanoTime();
        consumersThreads.forEach(Thread::start);
        producersThreads.forEach(Thread::start);

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(shutdownTimeoutMillis);
        boolean stopped = joinAll(producersThreads, deadline) && joinAll(consumersThreads, deadline);
        if (!stopped) {
            consumersThreads.forEach(Thread::interrupt);
            producersThreads.forEach(Thread::interrupt);
            stopped = joinAll(consumersThreads, System.nanoTime() + TimeUnit.SECONDS.toNanos(2))
                    && joinAll(producersThreads, System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
        }
        double durationMillis = (System.nanoTime() - start) / 1_000_000.0;

        List<PageResult> collected = new ArrayList<>(results);
        int succeeded = (int) collected.stream().filter(PageResult::ok).count();
        int failed = collected.size() - succeeded;

        return new CrawlReport(
                label,
                urls.size(),
                produced.get(),
                collected.size(),
                succeeded,
                failed,
                queue.maxDepth(),
                queue.capacity(),
                producers,
                consumers,
                queue.blockedPuts(),
                queue.blockedPutMillis(),
                durationMillis,
                stopped,
                collected);
    }

    /** Распределить задачи между производителями по кругу; порядок страниц сохраняется внутри доли. */
    private static List<UrlTask> share(List<UrlTask> tasks, int producerIndex, int producers) {
        List<UrlTask> share = new ArrayList<>();
        for (int index = producerIndex; index < tasks.size(); index += producers) {
            share.add(tasks.get(index));
        }
        return share;
    }

    /** Дождаться завершения всех потоков до общего срока. */
    private static boolean joinAll(List<Thread> threads, long deadlineNanos) throws InterruptedException {
        for (Thread thread : threads) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            thread.join(TimeUnit.NANOSECONDS.toMillis(remainingNanos) + 1);
            if (thread.isAlive()) {
                return false;
            }
        }
        return true;
    }
}
