package ru.tsu.tpm.vacancyparser.hw13.queue.model;

import java.util.Locale;

/**
 * Результат обработки одной страницы потребителем.
 *
 * <p>Хранит и успех, и сбой: страница с кодом {@code 404} — это не потерянная задача, а обработанная
 * с известным исходом. Отчёт по {@link #ok()} отделяет одно от другого, а {@link #consumer()}
 * показывает, какой поток взял задачу, — по этому видно, что очередь действительно распределяла работу.
 *
 * @param url        адрес страницы
 * @param pageNumber номер страницы
 * @param status     HTTP-код (0, если ответа не было)
 * @param title      извлечённый заголовок (пусто при сбое)
 * @param ok         успешно ли обработана страница
 * @param error      описание сбоя (пусто при успехе)
 * @param durationMillis сколько заняла обработка
 * @param consumer   имя потока-потребителя
 */
public record PageResult(
        String url,
        int pageNumber,
        int status,
        String title,
        boolean ok,
        String error,
        double durationMillis,
        String consumer) {

    public PageResult {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("адрес результата не задан");
        }
    }

    /** Успешный результат с заголовком. */
    public static PageResult success(
            UrlTask task, int status, String title, long durationNanos, String consumer) {
        return new PageResult(
                task.url(), task.pageNumber(), status, title, true, "",
                durationNanos / 1_000_000.0, consumer);
    }

    /** Неуспешный результат: страница обработана, но ожидаемого заголовка нет. */
    public static PageResult failure(
            UrlTask task, int status, String error, long durationNanos, String consumer) {
        return new PageResult(
                task.url(), task.pageNumber(), status, "", false, error,
                durationNanos / 1_000_000.0, consumer);
    }

    /** Строка для консоли и отчёта. */
    public String reportLine() {
        if (ok) {
            return String.format(
                    Locale.ROOT,
                    "№%d %s | %d | «%s» | %.2f мс | поток %s",
                    pageNumber, url, status, title, durationMillis, consumer);
        }
        return String.format(
                Locale.ROOT,
                "№%d %s | %d | сбой: %s | %.2f мс | поток %s",
                pageNumber, url, status, error, durationMillis, consumer);
    }
}
