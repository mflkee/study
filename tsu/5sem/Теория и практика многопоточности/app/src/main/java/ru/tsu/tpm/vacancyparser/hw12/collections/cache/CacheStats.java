package ru.tsu.tpm.vacancyparser.hw12.collections.cache;

import java.util.Locale;

/**
 * Снимок счётчиков кэша.
 *
 * @param hits        попадания (данные взяты без загрузки)
 * @param misses      промахи (данных не было)
 * @param loads       сколько раз вызывался загрузчик
 * @param evictions   вытеснений по ёмкости
 * @param expirations удалений по сроку жизни
 * @param size        текущее число записей
 */
public record CacheStats(long hits, long misses, long loads, long evictions, long expirations, int size) {

    /** Доля попаданий; 0, если обращений не было. */
    public double hitRatio() {
        long total = hits + misses;
        return total == 0 ? 0.0 : hits / (double) total;
    }

    /** Строка для консоли и отчёта. */
    public String reportLine() {
        return String.format(
                Locale.ROOT,
                "попаданий %d, промахов %d, загрузок %d, вытеснений %d, истечений %d, записей %d, "
                        + "доля попаданий %.1f%%",
                hits, misses, loads, evictions, expirations, size, hitRatio() * 100.0);
    }
}
