package ru.tsu.tpm.vacancyparser.hw12.collections.events;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Шина событий кэша на потокобезопасных коллекциях.
 *
 * <p>Четыре разных инструмента, каждый на своём месте:
 * <ul>
 *   <li>{@link LinkedBlockingQueue} — очередь публикации: {@code publish} не блокирует и не ждёт
 *       подписчиков, а разбор идёт в отдельном потоке-диспетчере;</li>
 *   <li>{@link CopyOnWriteArrayList} — список подписчиков: читается на каждом событии, меняется редко,
 *       поэтому копирование при записи здесь дешевле блокировок;</li>
 *   <li>{@link ConcurrentHashMap} + {@link LongAdder} — счётчики по типам: обновляются из многих потоков,
 *       а {@code LongAdder} под нагрузкой масштабируется лучше {@code AtomicLong};</li>
 *   <li>{@link ConcurrentLinkedDeque} — ограниченный журнал последних событий: добавление и обрезка
 *       с двух концов без общей блокировки.</li>
 * </ul>
 *
 * <p>Диспетчер один: так порядок доставки совпадает с порядком публикации, а подписчики не должны быть
 * потокобезопасными. Сбой одного подписчика не мешает остальным — исключение ловится и не выходит за
 * пределы рассылки.
 */
public final class VacancyEventBus implements AutoCloseable {

    /** Имя потока-диспетчера. */
    public static final String DISPATCHER_THREAD = "hw12-event-dispatch";

    private final int logLimit;
    private final LinkedBlockingQueue<VacancyEvent> queue = new LinkedBlockingQueue<>();
    private final CopyOnWriteArrayList<Consumer<VacancyEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<EventType, LongAdder> counts = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<VacancyEvent> recent = new ConcurrentLinkedDeque<>();
    private final AtomicLong sequence = new AtomicLong();
    private final LongAdder published = new LongAdder();
    private final LongAdder delivered = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder subscriberFailures = new LongAdder();
    private final Thread dispatcher;
    private volatile boolean running = true;

    /** Создать шину с ограничением журнала последних событий. */
    public VacancyEventBus(int logLimit) {
        if (logLimit < 1) {
            throw new IllegalArgumentException("предел журнала должен быть положительным, получено " + logLimit);
        }
        this.logLimit = logLimit;
        this.dispatcher = new Thread(this::dispatch, DISPATCHER_THREAD);
        this.dispatcher.setDaemon(true);
        this.dispatcher.start();
    }

    /** Опубликовать событие. Возвращает событие даже если шина уже остановлена (с признаком отказа). */
    public VacancyEvent publish(EventType type, String vacancyId) {
        if (type == null) {
            throw new IllegalArgumentException("тип события не задан");
        }
        VacancyEvent event = new VacancyEvent(
                sequence.incrementAndGet(), type, vacancyId, Thread.currentThread().getName(),
                System.nanoTime());
        counts.computeIfAbsent(type, key -> new LongAdder()).increment();
        published.increment();
        if (!running || !queue.offer(event)) {
            rejected.increment();
        }
        return event;
    }

    /** Подписаться на события. Подписчик вызывается потоком-диспетчером. */
    public void subscribe(Consumer<VacancyEvent> subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("подписчик не задан");
        }
        subscribers.add(subscriber);
    }

    /** Сколько подписчиков зарегистрировано. */
    public int subscriberCount() {
        return subscribers.size();
    }

    /** Сколько событий опубликовано. */
    public long published() {
        return published.sum();
    }

    /** Сколько событий доставлено подписчикам (по одному разу на событие). */
    public long delivered() {
        return delivered.sum();
    }

    /** Сколько событий не попало в очередь (шина остановлена или очередь переполнена). */
    public long rejected() {
        return rejected.sum();
    }

    /** Сколько раз подписчик бросил исключение. */
    public long subscriberFailures() {
        return subscriberFailures.sum();
    }

    /** Сколько событий данного типа опубликовано. */
    public long count(EventType type) {
        LongAdder adder = counts.get(type);
        return adder == null ? 0L : adder.sum();
    }

    /** Копия счётчиков по типам. */
    public Map<EventType, Long> counts() {
        Map<EventType, Long> copy = new EnumMap<>(EventType.class);
        for (EventType type : EventType.values()) {
            copy.put(type, count(type));
        }
        return copy;
    }

    /** Последние события, самые свежие первыми. */
    public List<VacancyEvent> recent() {
        return new ArrayList<>(recent);
    }

    /** Предел журнала. */
    public int logLimit() {
        return logLimit;
    }

    /**
     * Дождаться, пока все опубликованные события будут доставлены.
     *
     * <p>Нужно тестам и демонстрации: без ожидания счётчики читались бы в момент, когда часть событий
     * ещё лежит в очереди, и «потеря» была бы артефактом замера.
     */
    public boolean flush(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (delivered.sum() + rejected.sum() < published.sum()) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(2L);
        }
        return true;
    }

    /** Остановить диспетчер, доставив всё, что уже в очереди. */
    @Override
    public void close() {
        if (!running) {
            return;
        }
        running = false;
        queue.offer(VacancyEvent.STOP);
        try {
            dispatcher.join(2_000L);
            if (dispatcher.isAlive()) {
                dispatcher.interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dispatcher.interrupt();
        }
    }

    private void dispatch() {
        while (true) {
            VacancyEvent event;
            try {
                event = queue.take();
            } catch (InterruptedException e) {
                if (!running) {
                    return;
                }
                Thread.currentThread().interrupt();
                return;
            }
            if (event.isStop()) {
                // Доставляем всё, что успело накопиться до сигнала остановки.
                VacancyEvent pending;
                while ((pending = queue.poll()) != null && !pending.isStop()) {
                    deliver(pending);
                }
                return;
            }
            deliver(event);
        }
    }

    private void deliver(VacancyEvent event) {
        recent.addFirst(event);
        while (recent.size() > logLimit) {
            recent.pollLast();
        }
        for (Consumer<VacancyEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (RuntimeException e) {
                subscriberFailures.increment();
            }
        }
        delivered.increment();
    }
}
