package ru.tsu.tpm.vacancyparser.hw14.loom.pinning;

import java.util.Set;

/**
 * Итог одного прогона опыта с pinning.
 *
 * <p>Главное поле — {@code peakConcurrentTasks}. Пока виртуальные потоки «спят» внутри
 * {@code synchronized}, они остаются смонтированными на carrier-потоках, поэтому одновременно
 * работающих задач не может быть больше, чем carrier-потоков. С {@code ReentrantLock} поток на время
 * сна снимается с carrier, и одновременно в работе оказываются все задачи.
 *
 * <p>{@code carrierSwitches} — сколько раз поток после блокировки оказался на другом carrier-потоке.
 * У пришпиленного потока это невозможно (он не снимался), у свободного — обычное дело. Это второй,
 * независимый признак того же явления, не основанный на времени.
 *
 * @param label                название прогона
 * @param lockKind             чем защищали критическую секцию ({@code synchronized} / {@code ReentrantLock})
 * @param tasks                сколько задач подано
 * @param blockMillis          длительность блокировки внутри секции
 * @param wallTimeNanos        время прогона
 * @param peakConcurrentTasks  пик одновременно выполнявшихся задач
 * @param carriersUsed         сколько разных carrier-потоков участвовало
 * @param carrierNames         имена замеченных carrier-потоков
 * @param carrierSwitches      сколько раз carrier сменился после блокировки
 * @param completed            сколько задач дошло до конца
 * @param parallelism          число carrier-потоков планировщика
 */
public record PinningRun(
        String label,
        String lockKind,
        int tasks,
        long blockMillis,
        long wallTimeNanos,
        int peakConcurrentTasks,
        int carriersUsed,
        Set<String> carrierNames,
        int carrierSwitches,
        int completed,
        int parallelism) {

    public PinningRun {
        carrierNames = Set.copyOf(carrierNames);
    }

    /** Время прогона в миллисекундах. */
    public double wallMillis() {
        return wallTimeNanos / 1_000_000.0;
    }

    /** Все задачи завершились. */
    public boolean complete() {
        return completed == tasks;
    }

    /**
     * Сколько «кругов» по carrier-потокам сделал бы прогон, если бы все задачи были пришпилены.
     *
     * <p>Нижняя граница времени при pinning: {@code ceil(tasks / parallelism) * blockMillis}.
     */
    public int pinnedRounds() {
        if (parallelism <= 0) {
            return tasks;
        }
        return (tasks + parallelism - 1) / parallelism;
    }

    /** Ожидаемое время пришпиленного прогона: круги × длительность блокировки. */
    public double expectedPinnedMillis() {
        return pinnedRounds() * (double) blockMillis;
    }

    /** Наблюдалось ли ограничение параллелизма числом carrier-потоков. */
    public boolean limitedByCarriers() {
        return peakConcurrentTasks <= parallelism;
    }

    /** Строка для журнала. */
    public String summaryLine() {
        return String.format(
                java.util.Locale.ROOT,
                "%s: задач %d, блокировка %d мс, %.1f мс, пик одновременных %d (carrier'ов %d), "
                        + "carrier'ов задействовано %d, смен carrier'а %d, завершено %d из %d",
                label, tasks, blockMillis, wallMillis(), peakConcurrentTasks, parallelism,
                carriersUsed, carrierSwitches, completed, tasks);
    }
}
