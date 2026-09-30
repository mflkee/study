package ru.tsu.tpm.vacancyparser.hw05.locks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Формирование вывода ДЗ 5.
 *
 * <p>Как и в ДЗ 3–4, печать отделена от измерений: строки — чистые функции от результатов, поэтому
 * их можно проверить, не запуская нагрузку, а печать не попадает в измеряемый участок.
 */
public final class LocksReport {

    private LocksReport() {
    }

    /** Итог нагрузочного прогона: сколько положили, сколько забрали, что осталось. */
    public static List<String> balanceBlock(ProducerConsumerRunner.Result result) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Баланс добавлений и извлечений:");
        lines.add(String.format(
                Locale.ROOT, "    producer'ов: %d, consumer'ов: %d, ёмкость буфера: %d",
                result.producers(), result.consumers(), result.capacity()));
        lines.add(String.format(
                Locale.ROOT, "    элементов на producer: %d, ожидалось всего: %d",
                result.perProducer(), result.expected()));
        lines.add(String.format(
                Locale.ROOT, "    добавлено: %d, извлечено: %d, осталось в буфере: %d",
                result.produced(), result.taken().size(), result.remainingInBuffer()));
        lines.add(String.format(
                Locale.ROOT, "    время прогона: %.3f мс", millis(result.elapsedNanos())));
        lines.add("    ничего не потеряно: " + marker(result.nothingLost())
                + ", ничего не продублировано: " + marker(result.nothingDuplicated())
                + ", буфер пуст: " + marker(result.drained()));
        return lines;
    }

    /** Распределение извлечённых элементов по потокам — пример взаимодействия для отчёта. */
    public static List<String> distributionBlock(ProducerConsumerRunner.Result result) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Распределение извлечённых элементов по потокам:");
        for (Map.Entry<String, Integer> entry : result.perConsumer().entrySet()) {
            lines.add(String.format(Locale.ROOT, "    %-12s %d", entry.getKey(), entry.getValue()));
        }
        return lines;
    }

    /** Сколько раз каждая реализация будила ожидающего без работы для него. */
    public static List<String> wastedWakeupsBlock(long lockWakeups, long monitorWakeups) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Впустую проснувшиеся ожидающие (разбудили, а работы не нашлось):");
        lines.add(String.format(Locale.ROOT, "    %-26s %d", BufferBenchmark.LOCK_MECHANISM, lockWakeups));
        lines.add(String.format(Locale.ROOT, "    %-26s %d", BufferBenchmark.MONITOR_MECHANISM, monitorWakeups));
        lines.add("    Два условия против одного: адресное оповещение не будит чужую сторону, "
                + "поэтому впустую проснувшихся в разы меньше.");
        return lines;
    }

    /** Сравнение механизмов: все прогоны обоих вариантов и разброс. */
    public static List<String> comparisonBlock(BufferBenchmark.Comparison comparison) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Сравнение механизмов синхронизации (одинаковая нагрузка, один процесс):");
        lines.add(String.format(
                Locale.ROOT, "    producer'ов: %d, consumer'ов: %d, ёмкость: %d, элементов: %d",
                comparison.producers(),
                comparison.consumers(),
                comparison.capacity(),
                comparison.producers() * comparison.perProducer()));

        for (String mechanism : List.of(BufferBenchmark.LOCK_MECHANISM, BufferBenchmark.MONITOR_MECHANISM)) {
            List<Long> nanos = comparison.nanosFor(mechanism);
            lines.add("    " + mechanism + ":");
            for (long value : nanos) {
                lines.add(String.format(Locale.ROOT, "        %9.3f мс", millis(value)));
            }
            lines.add(String.format(
                    Locale.ROOT,
                    "        среднее %.3f мс, минимум %.3f мс, максимум %.3f мс",
                    millis(average(nanos)),
                    millis(nanos.stream().mapToLong(Long::longValue).min().orElse(0L)),
                    millis(nanos.stream().mapToLong(Long::longValue).max().orElse(0L))));
        }
        return lines;
    }

    /**
     * Вывод о различии механизмов, привязанный к измеренным числам.
     *
     * <p>Формулировка намеренно оставляет место для «разницы не показано»: два механизма делают
     * одну и ту же работу, и разница между ними может целиком лежать в разбросе повторов.
     */
    public static String conclusion(BufferBenchmark.Comparison comparison) {
        List<Long> lockNanos = comparison.nanosFor(BufferBenchmark.LOCK_MECHANISM);
        List<Long> monitorNanos = comparison.nanosFor(BufferBenchmark.MONITOR_MECHANISM);
        if (lockNanos.isEmpty() || monitorNanos.isEmpty()) {
            return "недостаточно замеров для вывода";
        }

        double lockAverage = average(lockNanos);
        double monitorAverage = average(monitorNanos);
        long lockMin = lockNanos.stream().mapToLong(Long::longValue).min().orElse(0L);
        long lockMax = lockNanos.stream().mapToLong(Long::longValue).max().orElse(0L);
        long monitorMin = monitorNanos.stream().mapToLong(Long::longValue).min().orElse(0L);
        long monitorMax = monitorNanos.stream().mapToLong(Long::longValue).max().orElse(0L);
        boolean overlaps = lockMin <= monitorMax && monitorMin <= lockMax;

        if (overlaps) {
            return String.format(
                    Locale.ROOT,
                    "на этой машине при %d producer'ах и %d consumer'ах разница между механизмами "
                            + "укладывается в разброс повторов (%.3f мс против %.3f мс), "
                            + "то есть по времени они равноценны",
                    comparison.producers(), comparison.consumers(), millis((long) lockAverage),
                    millis((long) monitorAverage));
        }
        String faster = lockAverage < monitorAverage ? BufferBenchmark.LOCK_MECHANISM : BufferBenchmark.MONITOR_MECHANISM;
        double ratio = Math.max(lockAverage, monitorAverage) / Math.min(lockAverage, monitorAverage);
        return String.format(
                Locale.ROOT,
                "на этой машине при %d producer'ах и %d consumer'ах быстрее %s — %.2f× "
                        + "(%.3f мс против %.3f мс)",
                comparison.producers(),
                comparison.consumers(),
                faster,
                ratio,
                millis((long) lockAverage),
                millis((long) monitorAverage));
    }

    private static String marker(boolean ok) {
        return ok ? "да" : "НЕТ";
    }

    private static double average(List<Long> nanos) {
        return nanos.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double millis(double nanos) {
        return nanos / 1_000_000.0;
    }
}
