package ru.tsu.tpm.vacancyparser.hw14.loom.benchmark;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Счётчик одновременной работы: сколько задач выполняется прямо сейчас и каким был пик.
 *
 * <p>Для виртуальных потоков обычные счётчики JVM не подходят: {@code ThreadMXBean.getThreadCount()}
 * считает только платформенные потоки, а {@code Thread.getAllStackTraces()} виртуальные потоки вообще
 * не возвращает. Поэтому «пик живых потоков» измеряется изнутри самих задач: каждая сообщает о входе
 * и выходе, а счётчик хранит максимум. Для платформенного пула этот же показатель равен размеру пула,
 * для виртуальных потоков — числу задач, которые действительно выполнялись одновременно.
 *
 * <p>Дополнительно копятся имена потоков и число исполнений на виртуальных и платформенных потоках:
 * это прямое доказательство, что в одном прогоне работали виртуальные потоки, а в другом —
 * платформенные, без доверия к названию сценария.
 *
 * <p>Класс потокобезопасен. Пик обновляется через CAS, чтобы не потерять максимум при гонке.
 */
public final class ConcurrencyMeter {

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();
    private final AtomicInteger virtualExecutions = new AtomicInteger();
    private final AtomicInteger platformExecutions = new AtomicInteger();
    private final Set<String> workerNames = ConcurrentHashMap.newKeySet();
    private final Set<Long> workerIds = ConcurrentHashMap.newKeySet();

    /** Отметить вход задачи в критическую секцию учёта. */
    public void enter(Thread thread) {
        if (thread.isVirtual()) {
            virtualExecutions.incrementAndGet();
        } else {
            platformExecutions.incrementAndGet();
        }
        workerIds.add(thread.threadId());
        if (!thread.getName().isEmpty()) {
            workerNames.add(thread.getName());
        }
        int current = inFlight.incrementAndGet();
        int observed = peak.get();
        while (current > observed && !peak.compareAndSet(observed, current)) {
            observed = peak.get();
        }
    }

    /** Отметить выход задачи. */
    public void exit() {
        inFlight.decrementAndGet();
    }

    /** Сколько задач выполняется прямо сейчас. */
    public int inFlight() {
        return inFlight.get();
    }

    /** Пик одновременно выполнявшихся задач. */
    public int peak() {
        return peak.get();
    }

    /** Сколько раз задачи исполнялись на виртуальных потоках. */
    public int virtualExecutions() {
        return virtualExecutions.get();
    }

    /** Сколько раз задачи исполнялись на платформенных потоках. */
    public int platformExecutions() {
        return platformExecutions.get();
    }

    /** Разные имена потоков, участвовавших в работе. */
    public Set<String> workerNames() {
        return Collections.unmodifiableSet(workerNames);
    }

    /** Сколько разных потоков (по идентификатору) участвовало в работе. */
    public int workerCount() {
        return workerIds.size();
    }
}
