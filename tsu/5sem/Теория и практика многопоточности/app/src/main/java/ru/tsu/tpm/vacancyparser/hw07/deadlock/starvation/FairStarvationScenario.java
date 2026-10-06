package ru.tsu.tpm.vacancyparser.hw07.deadlock.starvation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Моделирование starvation — корректный вариант: гарантированный доступ через честную очередь.
 *
 * <p>Тот же ресурс, но захват — через честную блокировку ({@code new ReentrantLock(true)}), все потоки
 * с <b>одинаковым</b> приоритетом. Честная очередь даёт потоку-«жертве» гарантированную долю доступа
 * независимо от планировщика. Ни поднятие приоритета, ни добровольная уступка процессора здесь не
 * используются — ни то, ни другое не является средством защиты (см. оговорку в {@link StarvationScenario}).
 *
 * <p>Класс отдельный намеренно: проверка «средство защиты не опирается на приоритеты и уступку»
 * читается по исходнику именно корректного варианта и не смешивается с контрпримером.
 */
public final class FairStarvationScenario {

    private FairStarvationScenario() {
    }

    /** Корректный вариант: честная блокировка, одинаковые приоритеты, без уступки процессора. */
    public static StarvationScenario.Outcome run(int workers, long windowMillis) throws InterruptedException {
        AtomicBoolean stop = new AtomicBoolean();
        ReentrantLock lock = new ReentrantLock(true);
        List<AtomicLong> counters = StarvationScenario.counters(workers);
        List<Thread> threads = new ArrayList<>(workers);

        for (int i = 0; i < workers; i++) {
            int index = i;
            Thread worker = new Thread(() -> {
                while (!stop.get()) {
                    lock.lock();
                    try {
                        counters.get(index).incrementAndGet();
                    } finally {
                        lock.unlock();
                    }
                }
            }, index == 0 ? StarvationScenario.VICTIM_NAME : StarvationScenario.THREAD_PREFIX + index);
            worker.setDaemon(true);
            threads.add(worker);
        }

        StarvationScenario.runWindow(threads, stop, windowMillis);
        return StarvationScenario.build(true, workers, windowMillis, threads, counters, StarvationScenario.FAIR_CAVEAT);
    }
}
