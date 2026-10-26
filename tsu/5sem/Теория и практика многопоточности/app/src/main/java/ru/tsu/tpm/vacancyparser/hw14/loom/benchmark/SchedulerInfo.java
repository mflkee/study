package ru.tsu.tpm.vacancyparser.hw14.loom.benchmark;

import java.util.Locale;

/**
 * Параметры планировщика виртуальных потоков.
 *
 * <p>Виртуальные потоки исполняются на общем {@code ForkJoinPool}, размер которого по умолчанию равен
 * числу доступных процессоров. Именно это число — «сколько виртуальных потоков реально могут
 * выполняться одновременно»; всё остальное время они либо ждут, либо сняты с carrier-потока.
 *
 * <p>Значения читаются из системных свойств, потому что демонстрация может быть запущена с
 * переопределением, например {@code -Djdk.virtualThreadScheduler.parallelism=4}. Свойство
 * {@code maxPoolSize} ограничивает аварийное расширение пула, когда carrier-потоки заблокированы
 * (например, при pinning).
 */
public final class SchedulerInfo {

    private static final String PARALLELISM = "jdk.virtualThreadScheduler.parallelism";
    private static final String MAX_POOL_SIZE = "jdk.virtualThreadScheduler.maxPoolSize";

    private SchedulerInfo() {
    }

    /** Число carrier-потоков планировщика. */
    public static int parallelism() {
        return Integer.getInteger(PARALLELISM, Runtime.getRuntime().availableProcessors());
    }

    /** Верхняя граница размера пула планировщика. */
    public static int maxPoolSize() {
        return Integer.getInteger(MAX_POOL_SIZE, 256);
    }

    /** Число доступных процессоров. */
    public static int processors() {
        return Runtime.getRuntime().availableProcessors();
    }

    /** Строка для отчёта. */
    public static String describe() {
        return String.format(
                Locale.ROOT,
                "carrier-потоков (parallelism) %d, предел пула планировщика %d, процессоров %d",
                parallelism(), maxPoolSize(), processors());
    }
}
