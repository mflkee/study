package ru.tsu.tpm.vacancyparser.hw05.locks;

import java.util.ArrayList;
import java.util.List;
import ru.tsu.tpm.vacancyparser.common.Timings;

/**
 * Сравнение двух механизмов синхронизации на одной и той же нагрузке.
 *
 * <p>Сравниваются реализации одного контракта ({@link BoundedBuffer} против {@link MonitorBuffer})
 * при одинаковом числе producer'ов и consumer'ов, одинаковой ёмкости и одинаковом числе элементов.
 * Время берётся через {@link Timings} из {@code common} на {@code System.nanoTime()}.
 *
 * <p>Каждый прогон возвращает не только время, но и результат по инвариантам: сравнивать быстрое
 * решение с неправильным нельзя, поэтому «одинаковый результат, разное время» — обязательное условие
 * осмысленности замера, а не примечание.
 */
public final class BufferBenchmark {

    /** Имена механизмов в выводе. */
    public static final String LOCK_MECHANISM = "ReentrantLock + Condition";
    public static final String MONITOR_MECHANISM = "synchronized + wait/notify";

    /** Сколько раз повторять замер по умолчанию. */
    public static final int DEFAULT_RUNS = 5;

    private BufferBenchmark() {
    }

    /**
     * Один замер.
     *
     * @param mechanism    какой механизм измерялся
     * @param nanos        время прогона
     * @param result       результат прогона (инварианты)
     */
    public record Run(String mechanism, long nanos, ProducerConsumerRunner.Result result) {
    }

    /**
     * Итог сравнения.
     *
     * @param producers   число потоков добавления
     * @param consumers   число потоков извлечения
     * @param capacity    ёмкость буфера
     * @param perProducer сколько элементов клал каждый producer
     * @param runs        все замеры по порядку выполнения
     */
    public record Comparison(
            int producers, int consumers, int capacity, int perProducer, List<Run> runs) {

        public Comparison {
            runs = List.copyOf(runs);
        }

        /** Времена прогонов указанного механизма. */
        public List<Long> nanosFor(String mechanism) {
            return runs.stream().filter(run -> run.mechanism().equals(mechanism)).map(Run::nanos).toList();
        }

        /** Все ли прогоны обоих механизмов дали согласованный результат. */
        public boolean allConsistent() {
            return runs.stream().allMatch(run -> run.result().consistent());
        }
    }

    /**
     * Выполнить сравнение.
     *
     * @param runs сколько раз замерить каждый механизм
     */
    public static Comparison compare(int producers, int consumers, int capacity, int perProducer, int runs)
            throws InterruptedException {
        List<Run> results = new ArrayList<>(runs * 2);

        for (int run = 0; run < runs; run++) {
            results.add(measure(LOCK_MECHANISM, new BoundedBuffer<>(capacity), producers, consumers, perProducer));
            results.add(measure(MONITOR_MECHANISM, new MonitorBuffer<>(capacity), producers, consumers, perProducer));
        }
        return new Comparison(producers, consumers, capacity, perProducer, results);
    }

    private static Run measure(
            String mechanism, Buffer<String> buffer, int producers, int consumers, int perProducer)
            throws InterruptedException {
        ProducerConsumerRunner.Result result =
                ProducerConsumerRunner.run(buffer, producers, consumers, perProducer);
        return new Run(mechanism, result.elapsedNanos(), result);
    }
}
