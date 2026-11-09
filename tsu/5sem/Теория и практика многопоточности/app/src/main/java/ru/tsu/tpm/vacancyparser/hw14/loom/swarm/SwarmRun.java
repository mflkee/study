package ru.tsu.tpm.vacancyparser.hw14.loom.swarm;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Итог прогона «10 000 задач, каждая печатает имя своего потока и ждёт 1 мс».
 *
 * <p>В отличие от {@link ru.tsu.tpm.vacancyparser.hw14.loom.model.BenchmarkResult} здесь важен не
 * только итог, но и <b>цена запуска</b>: {@code startNanos} — сколько занял сам цикл
 * {@code start()} по всем задачам, {@code totalNanos} — сколько прошло до завершения последней.
 * Для платформенных потоков эти два числа отличаются в разы: поток ОС — это стек, структуры ядра и
 * запись в планировщик, а не объект в куче.
 *
 * <p>{@code names} — имена потоков, которые «напечатала» каждая задача. Список полный (столько же
 * элементов, сколько задач), поэтому по нему проверяется и главное требование задания: каждая задача
 * действительно работала в своём потоке. В журнал демонстрации попадают только первые строки —
 * десять тысяч строк подряд отчёт не украшают.
 *
 * @param label              название прогона
 * @param tasks              сколько задач запускали
 * @param completed          сколько дошло до конца
 * @param failed             сколько было прервано (поток не дождался конца)
 * @param batches            на сколько пачек разбит запуск (1 — все задачи стартовали сразу)
 * @param peakPlatformThreads прирост пика платформенных потоков JVM по {@code ThreadMXBean};
 *                            у виртуальных потоков он нулевой всегда — они в этот счётчик не входят
 * @param peakConcurrentTasks пик одновременно работавших задач (счётчик внутри задач): у виртуальных
 *                            потоков он равен числу задач, у платформенных — скольким потокам ОС
 *                            реально удалось пожить одновременно
 * @param startNanos         время создания и запуска всех потоков
 * @param totalNanos         время от первой задачи до завершения последней
 * @param names              имена потоков в порядке номеров задач
 * @param virtualExecutions  задач, исполнявшихся на виртуальных потоках
 * @param platformExecutions задач, исполнявшихся на платформенных потоках
 * @param heapDeltaBytes     приблизительный прирост занятой кучи
 * @param cpuNanos           процессорное время процесса, потраченное на прогон (по
 *                           {@code OperatingSystemMXBean}): работа планировщика, создание потоков,
 *                           сам {@code start()} — всё, что съедает CPU
 */
public record SwarmRun(
        String label,
        int tasks,
        int completed,
        int failed,
        int batches,
        int peakPlatformThreads,
        int peakConcurrentTasks,
        long startNanos,
        long totalNanos,
        List<String> names,
        int virtualExecutions,
        int platformExecutions,
        long heapDeltaBytes,
        long cpuNanos) {

    public SwarmRun {
        Objects.requireNonNull(label, "label");
        names = List.copyOf(names);
    }

    /** Время запуска всех потоков в миллисекундах. */
    public double startMillis() {
        return startNanos / 1_000_000.0;
    }

    /** Время от запуска первой задачи до завершения последней, миллисекунды. */
    public double wallMillis() {
        return totalNanos / 1_000_000.0;
    }

    /** Процессорное время прогона в миллисекундах. */
    public double cpuMillis() {
        return cpuNanos / 1_000_000.0;
    }

    /** Пропускная способность: завершённых задач в секунду. */
    public double throughputPerSecond() {
        if (totalNanos <= 0L) {
            return 0.0;
        }
        return completed * 1_000_000_000.0 / totalNanos;
    }

    /** Все задачи дошли до конца. */
    public boolean complete() {
        return completed == tasks && failed == 0;
    }

    /** Все задачи исполнялись на виртуальных потоках. */
    public boolean virtualOnly() {
        return virtualExecutions == tasks && platformExecutions == 0;
    }

    /** Все задачи исполнялись на платформенных потоках. */
    public boolean platformOnly() {
        return platformExecutions == tasks && virtualExecutions == 0;
    }

    /** Сколько разных имён потоков встретилось (у «поток на задачу» должно быть равно числу задач). */
    public long distinctNames() {
        return names.stream().distinct().count();
    }

    /** Первые {@code limit} имён для журнала. */
    public List<String> sample(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return names.stream().limit(limit).toList();
    }

    /** Во сколько раз {@code reference} медленнее {@code other}. */
    public static double speedup(SwarmRun reference, SwarmRun other) {
        if (other.totalNanos <= 0L) {
            return 0.0;
        }
        return (double) reference.totalNanos / other.totalNanos;
    }

    /** Строка для журнала. */
    public String summaryLine() {
        return String.format(
                Locale.ROOT,
                "%s: задач %d, завершено %d, прервано %d, запуск %.1f мс, всего %.1f мс, "
                        + "%.0f задач/с, пачек %d, пик одновременных %d, разных имён %d, "
                        + "прирост потоков JVM %d, CPU процесса %.0f мс",
                label, tasks, completed, failed, startMillis(), wallMillis(),
                throughputPerSecond(), batches, peakConcurrentTasks, distinctNames(),
                peakPlatformThreads, cpuMillis());
    }

    /** Короткое представление: полный список имён в отладочный вывод не помещается. */
    @Override
    public String toString() {
        return "SwarmRun[" + label + ", задач " + tasks + ", всего " + wallMillis() + " мс]";
    }
}
