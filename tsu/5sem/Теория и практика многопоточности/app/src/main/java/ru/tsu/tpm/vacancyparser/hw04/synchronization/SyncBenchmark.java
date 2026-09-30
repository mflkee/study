package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import ru.tsu.tpm.vacancyparser.common.Timings;

/**
 * Замеры ДЗ 4: нагрузочный прогон и сравнение защищённого и незащищённого режимов.
 *
 * <p>Всё, что здесь считается, меряется одним и тем же способом — {@link Timings} из {@code common}
 * на {@code System.nanoTime()}. Обе ветви сравнения исполняют одинаковую нагрузку при одинаковом
 * числе потоков: иначе разница во времени объяснялась бы не защитой, а объёмом работы.
 *
 * <p>Ничего не печатает: печать собирается отдельно, чтобы вывод был проверяем без запуска нагрузки.
 */
public final class SyncBenchmark {

    /** Потоки по умолчанию: восемь — достаточно, чтобы гонка проявлялась устойчиво. */
    public static final int DEFAULT_THREADS = 8;

    /** Операций на поток в замере счётчика. */
    public static final int DEFAULT_OPERATIONS = 200_000;

    /** Сколько раз повторять замер, чтобы в отчёте был разброс, а не одно число. */
    public static final int DEFAULT_RUNS = 5;

    /** Размер перевода — маленький, чтобы узким местом была именно защита. */
    public static final long TRANSFER_AMOUNT = 1L;

    private SyncBenchmark() {
    }

    /**
     * Итог нагрузочного прогона: сколько потоков, сколько операций и согласовано ли состояние.
     *
     * @param threads             число потоков нагрузки
     * @param itemsPerThread      сколько элементов собирал каждый поток
     * @param transfersPerThread  сколько переводов делал каждый поток
     * @param collectedCount      размер списка собранных элементов
     * @param counterExpected     сколько увеличений должно было получиться
     * @param counterActual       сколько получилось
     * @param invariantViolations расхождения внутри сборщика (пусто — согласовано)
     * @param accountsBefore      сумма балансов до прогона
     * @param accountsAfter       сумма балансов после прогона
     * @param elapsedNanos        время прогона
     */
    public record MixedLoadOutcome(
            int threads,
            int itemsPerThread,
            int transfersPerThread,
            int collectedCount,
            long counterExpected,
            long counterActual,
            List<String> invariantViolations,
            long accountsBefore,
            long accountsAfter,
            long elapsedNanos) {

        public MixedLoadOutcome {
            invariantViolations = List.copyOf(invariantViolations);
        }

        /** Сохранилось ли состояние сборщика. */
        public boolean collectorConsistent() {
            return invariantViolations.isEmpty() && counterActual == counterExpected;
        }

        /** Сохранилась ли сумма балансов. */
        public boolean accountsPreserved() {
            return accountsBefore == accountsAfter;
        }
    }

    /**
     * Один замер одного режима.
     *
     * @param protection  режим защиты словами
     * @param nanos       время прогона
     * @param value       итоговое значение счётчика
     * @param expected    ожидаемое значение
     */
    public record CounterRun(String protection, long nanos, long value, long expected) {

        /** Сколько обновлений потеряно. */
        public long lost() {
            return expected - value;
        }

        /** Совпало ли значение с ожидаемым. */
        public boolean exact() {
            return value == expected;
        }
    }

    /**
     * Сравнение режимов защиты на одинаковой нагрузке.
     *
     * @param runs замеры по порядку выполнения: сначала `N` прогонов одного режима, затем `N` другого
     */
    public record CounterComparison(int threads, int operationsPerThread, long expected, List<CounterRun> runs) {

        public CounterComparison {
            runs = List.copyOf(runs);
        }

        /** Время по режиму защиты. */
        public List<Long> nanosFor(String protection) {
            return runs.stream().filter(run -> run.protection().equals(protection)).map(CounterRun::nanos).toList();
        }
    }

    /**
     * Один замер перевода.
     *
     * @param protection режим защиты словами
     * @param nanos      время прогона
     * @param before     сумма балансов до прогона
     * @param after      сумма балансов после прогона
     */
    public record TransferRun(String protection, long nanos, long before, long after) {

        /** Сохранилась ли сумма балансов. */
        public boolean preserved() {
            return before == after;
        }
    }

    /** Сравнение перевода в двух режимах. */
    public record TransferComparison(int threads, int transfersPerThread, long amount, List<TransferRun> runs) {

        public TransferComparison {
            runs = List.copyOf(runs);
        }

        public List<Long> nanosFor(String protection) {
            return runs.stream()
                    .filter(run -> run.protection().equals(protection))
                    .map(TransferRun::nanos)
                    .toList();
        }
    }

    /**
     * Нагрузочный прогон: потоки одновременно собирают данные, читают состояние и переводят средства.
     *
     * <p>Смешение операций — не украшение: читатели работают параллельно писателям, поэтому без
     * защиты они увидели бы промежуточное состояние. После прогона проверяются инварианты — именно
     * они и есть результат этого шага.
     */
    public static MixedLoadOutcome runMixedLoad(int threads, int itemsPerThread, int transfersPerThread)
            throws InterruptedException {
        DataCollector collector = new DataCollector();
        Account first = new Account("acc-1", 1_000_000);
        Account second = new Account("acc-2", 1_000_000);
        long before = first.balance() + second.balance();
        TransferService transfers = new TransferService(2_000L);
        AtomicLong reads = new AtomicLong();

        long elapsed = Timings.measureNanos(() -> {
            try {
                startThreads(threads, index -> {
                    for (int i = 0; i < itemsPerThread; i++) {
                        collector.collectItem(new Item("v-" + index + "-" + i, "текст"));
                        collector.isAlreadyProcessed("v-" + index + "-" + i);
                        reads.incrementAndGet();
                    }
                    for (int i = 0; i < transfersPerThread; i++) {
                        try {
                            if (index % 2 == 0) {
                                transfers.transfer(first, second, TRANSFER_AMOUNT);
                            } else {
                                transfers.transfer(second, first, TRANSFER_AMOUNT);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("нагрузочный прогон прерван", e);
            }
        });

        return new MixedLoadOutcome(
                threads,
                itemsPerThread,
                transfersPerThread,
                collector.collectedCount(),
                (long) threads * itemsPerThread,
                collector.processedCount(),
                collector.invariantViolations(),
                before,
                first.balance() + second.balance(),
                elapsed);
    }

    /**
     * Сравнить режимы защиты счётчика на одинаковой нагрузке.
     *
     * @param runs сколько раз замерить каждый режим
     */
    public static CounterComparison compareCounters(int threads, int operationsPerThread, int runs)
            throws InterruptedException {
        long expected = (long) threads * operationsPerThread;
        List<CounterRun> results = new ArrayList<>(runs * Protection.values().length);

        for (Protection protection : Protection.values()) {
            for (int run = 0; run < runs; run++) {
                Counter counter = protection.newCounter();
                long nanos = Timings.measureNanos(() -> {
                    try {
                        startThreads(threads, index -> {
                            for (int i = 0; i < operationsPerThread; i++) {
                                counter.increment();
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("замер счётчика прерван", e);
                    }
                });
                results.add(new CounterRun(protection.label(), nanos, counter.value(), expected));
            }
        }

        return new CounterComparison(threads, operationsPerThread, expected, results);
    }

    /** Сравнить перевод между счетами в двух режимах. */
    public static TransferComparison compareTransfers(int threads, int transfersPerThread, int runs)
            throws InterruptedException {
        List<TransferRun> results = new ArrayList<>(runs * Protection.values().length);

        for (Protection protection : Protection.values()) {
            for (int run = 0; run < runs; run++) {
                results.add(protection == Protection.ON
                        ? protectedTransferRun(threads, transfersPerThread)
                        : unprotectedTransferRun(threads, transfersPerThread));
            }
        }

        return new TransferComparison(threads, transfersPerThread, TRANSFER_AMOUNT, results);
    }

    private static TransferRun protectedTransferRun(int threads, int transfersPerThread)
            throws InterruptedException {
        Account first = new Account("acc-1", 1_000_000);
        Account second = new Account("acc-2", 1_000_000);
        long before = first.balance() + second.balance();
        TransferService service = new TransferService(2_000L);

        long nanos = Timings.measureNanos(() -> {
            try {
                startThreads(threads, index -> transferWithProtection(service, first, second, index, transfersPerThread));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("замер перевода прерван", e);
            }
        });

        return new TransferRun(
                Protection.ON.label(), nanos, before, first.balance() + second.balance());
    }

    private static void transferWithProtection(
            TransferService service, Account first, Account second, int index, int transfers) {
        for (int i = 0; i < transfers; i++) {
            try {
                if (index % 2 == 0) {
                    service.transfer(first, second, TRANSFER_AMOUNT);
                } else {
                    service.transfer(second, first, TRANSFER_AMOUNT);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static TransferRun unprotectedTransferRun(int threads, int transfersPerThread)
            throws InterruptedException {
        UnprotectedAccount first = new UnprotectedAccount("acc-1", 1_000_000);
        UnprotectedAccount second = new UnprotectedAccount("acc-2", 1_000_000);
        long before = first.balance() + second.balance();

        long nanos = Timings.measureNanos(() -> {
            try {
                startThreads(threads, index -> {
                    for (int i = 0; i < transfersPerThread; i++) {
                        if (index % 2 == 0) {
                            UnprotectedTransferService.transfer(first, second, TRANSFER_AMOUNT);
                        } else {
                            UnprotectedTransferService.transfer(second, first, TRANSFER_AMOUNT);
                        }
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("замер перевода прерван", e);
            }
        });

        return new TransferRun(
                Protection.OFF.label(), nanos, before, first.balance() + second.balance());
    }

    /**
     * Запустить потоки нагрузки с общим стартом и дождаться их.
     *
     * <p>Общий старт обязателен: при последовательном запуске следующий поток успевает закончить
     * раньше, чем стартует предыдущий, и гонка просто не возникает.
     */
    static void startThreads(int threads, java.util.function.IntConsumer body) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(threads);
        List<Thread> workers = new ArrayList<>(threads);

        for (int i = 0; i < threads; i++) {
            int index = i;
            Thread worker = new Thread(() -> {
                ready.countDown();
                try {
                    if (!startGate.await(60, TimeUnit.SECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                body.accept(index);
            }, "load-" + i);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }

        ready.await(60, TimeUnit.SECONDS);
        startGate.countDown();
        for (Thread worker : workers) {
            worker.join(120_000L);
        }
    }
}
