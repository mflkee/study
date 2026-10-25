package ru.tsu.tpm.vacancyparser.finaltask.service;

/**
 * Результат HTTP-запроса за одной страницей.
 *
 * <p>{@code status == 0} означает, что ответа не было вовсе (таймаут, отказ соединения, кривой адрес) —
 * это принципиально отличается от ответа {@code 404} или {@code 500}. Ошибка транспорта не должна
 * выглядеть как страница с кодом.
 *
 * @param url           адрес запроса
 * @param status        статус-код ответа или {@code 0}
 * @param body          тело ответа (при успехе)
 * @param error         причина сбоя транспорта ({@code null} при полученном ответе)
 * @param durationNanos время запроса
 */
public record FetchResult(String url, int status, String body, String error, long durationNanos) {

    /** Получен успешный ответ с непустым телом. */
    public boolean ok() {
        return status >= 200 && status < 300 && body != null && !body.isBlank();
    }

    /** Страница получена, но разбор ещё не выполнялся. */
    public static FetchResult success(String url, int status, String body, long durationNanos) {
        return new FetchResult(url, status, body, null, durationNanos);
    }

    /** Ответ с неуспешным кодом либо сбой транспорта. */
    public static FetchResult failure(String url, int status, String body, String error, long durationNanos) {
        return new FetchResult(url, status, body, error, durationNanos);
    }

    /** Время запроса в миллисекундах. */
    public double durationMillis() {
        return durationNanos / 1_000_000.0;
    }
}
