package ru.tsu.tpm.vacancyparser.common;

import java.time.Duration;
import java.util.Locale;

/**
 * Замер длительности через {@link System#nanoTime()}.
 *
 * <p>Используется бенчмарками ДЗ 3, сравнениями производительности в ДЗ 4–6 и
 * замерами HTTP-запросов в ДЗ 8. {@code nanoTime()} монотонен, в отличие от
 * {@code Instant.now()}, и не зависит от настройки системных часов.
 */
public final class Timings {

    private Timings() {
    }

    /** Выполнить действие и вернуть его длительность. */
    public static Duration measure(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return Duration.ofNanos(System.nanoTime() - start);
    }

    /** Выполнить действие и вернуть длительность в наносекундах. */
    public static long measureNanos(Runnable action) {
        return measure(action).toNanos();
    }

    /**
     * Привести длительность к виду, пригодному для отчёта: «123.456 мс» или «1.234 с».
     * Точности {@link Duration#toString()} на стендах с грубыми часами не хватает.
     */
    public static String human(Duration duration) {
        long nanos = duration.toNanos();
        if (nanos < 1_000_000L) {
            return String.format(Locale.ROOT, "%d мкс", nanos);
        }
        if (nanos < 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.3f мс", nanos / 1_000_000.0);
        }
        return String.format(Locale.ROOT, "%.3f с", nanos / 1_000_000_000.0);
    }
}
