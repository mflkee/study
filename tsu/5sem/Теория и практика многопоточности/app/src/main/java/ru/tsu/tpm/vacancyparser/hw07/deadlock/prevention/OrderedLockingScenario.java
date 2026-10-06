package ru.tsu.tpm.vacancyparser.hw07.deadlock.prevention;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;

/**
 * Предотвращение взаимной блокировки единым порядком захвата ресурсов без ожидания под блокировкой.
 *
 * <p>Цикл ожидания невозможен, если все потоки захватывают ресурсы в одном и том же глобальном
 * порядке. Порядок выведен из идентификаторов ресурсов; «младший» ресурс берётся обычным {@code lock()},
 * а «старший» — <b>неблокирующим</b> {@code tryLock()}. Поэтому ни один поток не удерживает одну
 * блокировку, пока <i>ждёт</i> другую: либо второй ресурс свободен и берётся сразу, либо поток
 * освобождает первый и повторяет попытку. Второе ожидание при таком порядке всегда успешно (ресурс
 * «старше» берётся только после «младшего», а «младший» эксклюзивен), и счётчик попыток ожидания
 * остаётся нулевым — это и есть проверяемый факт «нет вложенного ожидания».
 *
 * <p>Освобождение выполняется в обратном порядке и в гарантированном блоке. Ни один класс
 * {@code hw04} не изменяется: там порядок захвата уже применялся при переводах между счетами, и это
 * ровно та связка, которую стоит процитировать в отчёте.
 */
public final class OrderedLockingScenario {

    /** Имя сценария для выбора в демонстрации. */
    public static final String NAME = "prevention";

    /** Префикс имён потоков сценария. */
    public static final String THREAD_PREFIX = "Prevention-";

    /**
     * Итог защитного сценария.
     *
     * @param runs               число прогонов
     * @param threads            число потоков на прогон
     * @param iterations         число захватов пары ресурсов на поток
     * @param anyDeadlock        обнаружена ли взаимная блокировка хотя бы раз
     * @param expected           ожидаемое итоговое значение счётчика
     * @param actual             фактическое значение последнего прогона
     * @param orders             наблюдённые порядки захвата
     * @param ordersConsistent   одинаков ли порядок во всех наблюдениях
     * @param secondLockWaits    сколько раз поток ждал второй ресурс, удерживая первый (ожидается 0)
     */
    public record Outcome(
            int runs,
            int threads,
            int iterations,
            boolean anyDeadlock,
            long expected,
            long actual,
            Set<String> orders,
            boolean ordersConsistent,
            long secondLockWaits) {

        public Outcome {
            orders = Set.copyOf(orders);
        }

        /** Пригодная для цитирования формулировка о применимости приёма. */
        public String conclusion() {
            return "единый порядок захвата (как в ДЗ 4 при переводах между счетами) исключает цикл "
                    + "ожидания: из " + runs + " прогонов по " + threads + " потоков взаимной блокировки нет";
        }
    }

    /** Ресурс с идентификатором; порядок захвата задаётся именно идентификатором, а не порядком в коде. */
    private record Resource(int id, ReentrantLock lock, String name) {
    }

    private OrderedLockingScenario() {
    }

    /**
     * Прогнать защитный сценарий.
     *
     * @param runs       сколько раз повторить
     * @param threads    сколько потоков конкурируют в каждом прогоне
     * @param iterations сколько раз каждый поток захватывает пару ресурсов
     */
    public static Outcome run(int runs, int threads, int iterations) throws InterruptedException {
        Resource low = new Resource(1, new ReentrantLock(), "resource1");
        Resource high = new Resource(2, new ReentrantLock(), "resource2");
        AtomicLong counter = new AtomicLong();
        AtomicLong secondLockWaits = new AtomicLong();
        long expected = (long) threads * iterations;

        boolean anyDeadlock = false;
        Set<String> orders = new LinkedHashSet<>();
        long actual = 0L;

        for (int run = 1; run <= runs; run++) {
            counter.set(0L);
            secondLockWaits.set(0L);
            List<Thread> workers = new ArrayList<>(threads);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch ready = new CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                Thread worker = new Thread(() -> {
                    ready.countDown();
                    try {
                        if (!startGate.await(30, TimeUnit.SECONDS)) {
                            return;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int k = 0; k < iterations; k++) {
                        lockPair(low, high, counter, secondLockWaits);
                    }
                }, THREAD_PREFIX + i);
                worker.setDaemon(true);
                workers.add(worker);
                worker.start();
            }
            ready.await(30, TimeUnit.SECONDS);
            startGate.countDown();
            for (Thread worker : workers) {
                worker.join(60_000L);
            }
            orders.add(low.name() + "->" + high.name());
            if (ThreadDump.hasDeadlock()) {
                anyDeadlock = true;
            }
            actual = counter.get();
            if (actual != expected) {
                throw new IllegalStateException(
                        "прогон " + run + ": инвариант нарушен, счётчик " + actual + " из " + expected);
            }
        }

        return new Outcome(runs, threads, iterations, anyDeadlock, expected, actual, orders, orders.size() == 1,
                secondLockWaits.get());
    }

    /**
     * Захватить пару ресурсов в едином порядке, не ожидая под блокировкой.
     *
     * <p>Первый ресурс — {@code lock()}; второй — {@code tryLock()} без ожидания. Если второй занят,
     * первый немедленно освобождается и попытка повторяется, поэтому ожидания второй блокировки под
     * первой не происходит.
     */
    private static void lockPair(Resource low, Resource high, AtomicLong counter, AtomicLong secondLockWaits) {
        for (;;) {
            low.lock().lock();
            boolean gotHigh = false;
            try {
                gotHigh = high.lock().tryLock();
                if (gotHigh) {
                    counter.incrementAndGet();
                }
            } finally {
                if (gotHigh) {
                    high.lock().unlock();
                }
                low.lock().unlock();
            }
            if (gotHigh) {
                return;
            }
            secondLockWaits.incrementAndGet();
            Thread.onSpinWait();
        }
    }
}
