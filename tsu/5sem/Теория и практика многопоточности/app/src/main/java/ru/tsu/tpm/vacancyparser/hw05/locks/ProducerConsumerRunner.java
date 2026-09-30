package ru.tsu.tpm.vacancyparser.hw05.locks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Нагрузочный прогон: несколько producer'ов и несколько consumer'ов работают с буфером параллельно.
 *
 * <p>Прогон написан против интерфейса {@link Buffer}, поэтому одна и та же нагрузка применяется и к
 * реализации на явной блокировке, и к мониторной. Это обязательное условие сравнения: если бы у
 * каждой реализации было своё «упражнение», сравнивались бы разные вещи.
 *
 * <p>Порядок завершения: сначала дожидаемся producer'ов, потом закрываем буфер — закрытие переводит
 * все ожидающие потоки в состояние «пора заканчивать», и consumer'ы выходят сами. Ожидание
 * producer'ов до закрытия принципиально: иначе буфер закроется раньше, чем добавлено последнее, и
 * инвариант «ничего не потеряно» проверялся бы на неполной нагрузке.
 */
public final class ProducerConsumerRunner {

    /** Срок ожидания завершения потока: страховка от вечного ожидания в отчёте о нагрузке. */
    private static final long JOIN_TIMEOUT_MILLIS = 30_000L;

    private ProducerConsumerRunner() {
    }

    /**
     * Результат прогона.
     *
     * @param producers        число потоков добавления
     * @param consumers        число потоков извлечения
     * @param capacity         ёмкость буфера
     * @param perProducer      сколько элементов клал каждый producer
     * @param expected         сколько элементов должно быть добавлено всего
     * @param produced         сколько удалось положить
     * @param taken            все извлечённые элементы в порядке извлечения
     * @param remainingInBuffer сколько осталось в буфере
     * @param perConsumer      распределение извлечённых элементов по потокам
     * @param elapsedNanos     время прогона
     */
    public record Result(
            int producers,
            int consumers,
            int capacity,
            int perProducer,
            int expected,
            int produced,
            List<String> taken,
            int remainingInBuffer,
            Map<String, Integer> perConsumer,
            long elapsedNanos) {

        public Result {
            taken = List.copyOf(taken);
            perConsumer = Collections.unmodifiableMap(new LinkedHashMap<>(perConsumer));
        }

        /** Ни один элемент не потерян: извлечено ровно столько, сколько должно быть. */
        public boolean nothingLost() {
            return taken.size() == expected && produced == expected;
        }

        /** Ни один элемент не извлечён дважды. */
        public boolean nothingDuplicated() {
            return new LinkedHashSet<>(taken).size() == taken.size();
        }

        /** Множество извлечённых совпадает с множеством добавленных. */
        public boolean sameElements() {
            return new LinkedHashSet<>(taken).size() == expected;
        }

        /** Буфер пуст по завершении. */
        public boolean drained() {
            return remainingInBuffer == 0;
        }

        /** Все инварианты выполнены. */
        public boolean consistent() {
            return nothingLost() && nothingDuplicated() && sameElements() && drained();
        }

        /** Ожидаемый набор идентификаторов. */
        public Set<String> expectedItems() {
            Set<String> all = new LinkedHashSet<>();
            for (int p = 0; p < producers; p++) {
                for (int i = 0; i < perProducer; i++) {
                    all.add("p" + p + "-i" + i);
                }
            }
            return all;
        }
    }

    /** Выполнить нагрузочный прогон. */
    public static Result run(Buffer<String> buffer, int producers, int consumers, int perProducer)
            throws InterruptedException {
        if (producers < 1 || consumers < 1) {
            throw new IllegalArgumentException("нужен хотя бы один producer и один consumer");
        }
        if (perProducer < 1) {
            throw new IllegalArgumentException("каждый producer обязан положить хотя бы один элемент");
        }

        CountDownLatch startGate = new CountDownLatch(1);
        List<BufferProducer> producerTasks = new ArrayList<>(producers);
        List<BufferConsumer> consumerTasks = new ArrayList<>(consumers);
        List<Thread> threads = new ArrayList<>(producers + consumers);

        for (int p = 0; p < producers; p++) {
            List<String> items = new ArrayList<>(perProducer);
            for (int i = 0; i < perProducer; i++) {
                items.add("p" + p + "-i" + i);
            }
            BufferProducer task = new BufferProducer(buffer, items, startGate);
            producerTasks.add(task);
            threads.add(new Thread(task, "producer-" + p));
        }
        for (int c = 0; c < consumers; c++) {
            BufferConsumer task = new BufferConsumer(buffer, startGate);
            consumerTasks.add(task);
            threads.add(new Thread(task, "consumer-" + c));
        }

        long startedAt = System.nanoTime();
        threads.forEach(Thread::start);
        startGate.countDown();

        // Ждём producer'ов, затем закрываем буфер — это и есть команда «заканчивайте» для consumer'ов.
        for (int i = 0; i < producers; i++) {
            threads.get(i).join(JOIN_TIMEOUT_MILLIS);
        }
        buffer.close();
        for (int i = producers; i < threads.size(); i++) {
            threads.get(i).join(JOIN_TIMEOUT_MILLIS);
        }
        long elapsedNanos = System.nanoTime() - startedAt;

        int produced = producerTasks.stream().mapToInt(BufferProducer::producedCount).sum();
        List<String> taken = new ArrayList<>();
        Map<String, Integer> perConsumer = new LinkedHashMap<>();
        for (int c = 0; c < consumers; c++) {
            BufferConsumer task = consumerTasks.get(c);
            taken.addAll(task.takenItems());
            perConsumer.put("consumer-" + c, task.takenCount());
        }

        return new Result(
                producers,
                consumers,
                buffer.capacity(),
                perProducer,
                producers * perProducer,
                produced,
                taken,
                buffer.size(),
                perConsumer,
                elapsedNanos);
    }
}
