package ru.tsu.tpm.vacancyparser.hw07.deadlock.starvation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Моделирование starvation — некорректный вариант.
 *
 * <p>Несколько потоков делят ресурс через <b>несправедливый</b> захват: приоритеты «обычных» потоков
 * подняты до максимума, «жертва» работает с обычным приоритетом, а после каждого захвата поток
 * добровольно уступает процессор ({@code Thread.yield()}). Факты неравного доступа выводятся как есть —
 * вместе с явной оговоркой, что это <b>не гарантировано</b>: виртуальная машина не обязана учитывать
 * приоритеты потоков, и результат зависит от планировщика и числа ядер.
 *
 * <p>Корректный вариант — в {@link FairStarvationScenario}: он намеренно в отдельном классе, чтобы
 * проверка «средство защиты не опирается на приоритеты и уступку» читалась по исходнику именно этого
 * варианта.
 */
public final class StarvationScenario {

    /** Имя сценария для выбора в демонстрации. */
    public static final String NAME = "starvation";

    /** Префикс имён потоков сценария. */
    public static final String THREAD_PREFIX = "Starvation-";

    /** Имя потока-«жертвы». */
    public static final String VICTIM_NAME = "Starvation-victim";

    /** Сколько раз «жертва» обязана получить ресурс при честном захвате за окно наблюдения. */
    public static final long FAIR_MIN_VICTIM_ACQUISITIONS = 20L;

    /** Оговорка к некорректному варианту. */
    public static final String UNFAIR_CAVEAT =
            "starvation здесь НЕ гарантирован: JVM не обязана учитывать приоритеты потоков, "
                    + "результат зависит от планировщика и числа ядер — это иллюстрация, а не воспроизводимый факт";

    /** Пояснение к корректному варианту. */
    public static final String FAIR_CAVEAT =
            "доступ предоставляет честная очередь блокировки (ReentrantLock(true)) при одинаковых приоритетах; "
                    + "приоритеты и Thread.yield() как средство защиты не используются";

    /**
     * Итог сценария starvation.
     *
     * @param fair               честный ли захват
     * @param workers            число потоков
     * @param windowMillis       окно наблюдения
     * @param workerNames        имена потоков в порядке индексов
     * @param acquisitions       число захватов каждого потока
     * @param victimAcquisitions захваты потока-«жертвы»
     * @param min                минимальное число захватов среди потоков
     * @param max                максимальное число захватов среди потоков
     * @param caveat             пояснение к варианту
     */
    public record Outcome(
            boolean fair,
            int workers,
            long windowMillis,
            List<String> workerNames,
            List<Long> acquisitions,
            long victimAcquisitions,
            long min,
            long max,
            String caveat) {

        public Outcome {
            workerNames = List.copyOf(workerNames);
            acquisitions = List.copyOf(acquisitions);
        }

        /** Есть ли неравенство доступа (не все потоки получили одинаково). */
        public boolean unequal() {
            return min != max;
        }
    }

    private StarvationScenario() {
    }

    /** Некорректный вариант: несправедливый захват, поднятые приоритеты, уступка процессора. */
    public static Outcome runUnfair(int workers, long windowMillis) throws InterruptedException {
        AtomicBoolean stop = new AtomicBoolean();
        ReentrantLock lock = new ReentrantLock(false);
        List<AtomicLong> counters = counters(workers);
        List<Thread> threads = new ArrayList<>(workers);

        for (int i = 0; i < workers; i++) {
            int index = i;
            Thread worker = new Thread(() -> {
                while (!stop.get()) {
                    if (lock.tryLock()) {
                        try {
                            counters.get(index).incrementAndGet();
                        } finally {
                            lock.unlock();
                        }
                        Thread.yield();
                    } else {
                        Thread.onSpinWait();
                    }
                }
            }, index == 0 ? VICTIM_NAME : THREAD_PREFIX + index);
            if (index != 0) {
                worker.setPriority(Thread.MAX_PRIORITY);
            }
            worker.setDaemon(true);
            threads.add(worker);
        }

        runWindow(threads, stop, windowMillis);
        return build(false, workers, windowMillis, threads, counters, UNFAIR_CAVEAT);
    }

    /** Счётчики захватов по потокам. Общий помощник корректного и некорректного вариантов. */
    static List<AtomicLong> counters(int workers) {
        List<AtomicLong> counters = new ArrayList<>(workers);
        for (int i = 0; i < workers; i++) {
            counters.add(new AtomicLong());
        }
        return counters;
    }

    /** Запустить потоки, выдержать окно наблюдения и остановить их. */
    static void runWindow(List<Thread> threads, AtomicBoolean stop, long windowMillis) throws InterruptedException {
        threads.forEach(Thread::start);
        Thread.sleep(Math.max(1L, windowMillis));
        stop.set(true);
        for (Thread worker : threads) {
            worker.join(5_000L);
        }
    }

    /** Собрать итог по именам потоков и счётчикам. */
    static Outcome build(
            boolean fair,
            int workers,
            long windowMillis,
            List<Thread> threads,
            List<AtomicLong> counters,
            String caveat) {
        List<String> names = threads.stream().map(Thread::getName).toList();
        List<Long> acquisitions = counters.stream().map(AtomicLong::get).toList();
        long min = acquisitions.stream().mapToLong(Long::longValue).min().orElse(0L);
        long max = acquisitions.stream().mapToLong(Long::longValue).max().orElse(0L);
        return new Outcome(fair, workers, windowMillis, names, acquisitions, acquisitions.get(0), min, max, caveat);
    }
}
