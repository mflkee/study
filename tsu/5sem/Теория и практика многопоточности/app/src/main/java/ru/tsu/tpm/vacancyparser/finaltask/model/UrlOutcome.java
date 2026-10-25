package ru.tsu.tpm.vacancyparser.finaltask.model;

/**
 * Итог обработки одного адреса в рамках обхода.
 *
 * <p>Поле {@code thread} — имя потока пула, который выполнял запрос и разбор. Именно оно в ответе
 * {@code /final/stats} и в логе доказывает, что страницы обрабатывались параллельно разными потоками,
 * а не по очереди в потоке запроса.
 *
 * @param url           адрес страницы
 * @param ok            страница получена и разобрана
 * @param httpStatus    статус-код ответа ({@code 0}, если ответа не было)
 * @param thread        имя потока-исполнителя
 * @param durationNanos время обработки адреса
 * @param title         извлечённое название вакансии (при успехе)
 * @param error         причина сбоя (при неуспехе)
 */
public record UrlOutcome(
        String url,
        boolean ok,
        int httpStatus,
        String thread,
        long durationNanos,
        String title,
        String error) {

    /** Успешная обработка. */
    public static UrlOutcome success(String url, int status, String thread, long durationNanos, String title) {
        return new UrlOutcome(url, true, status, thread, durationNanos, title, null);
    }

    /** Неуспешная обработка: сеть, HTTP-код или ошибка разбора. */
    public static UrlOutcome failure(String url, String error, String thread, long durationNanos) {
        return new UrlOutcome(url, false, 0, thread, durationNanos, null, error);
    }

    /** Время обработки в миллисекундах — для вывода и ответа API. */
    public double durationMillis() {
        return durationNanos / 1_000_000.0;
    }
}
