package ru.tsu.tpm.vacancyparser.hw08.pool.web;

import java.util.Locale;

/**
 * Результат запроса к одному адресу.
 *
 * <p>Для каждого адреса фиксируются категория исхода, статус-код (только если ответ действительно
 * получен), время ответа и текстовое пояснение. Код не выдумывается: при таймауте, сетевой ошибке,
 * прерывании и отказе {@link #httpStatus()} равен {@code null}, а не «нулю» или «-1».
 *
 * @param url           адрес запроса
 * @param category      категория исхода
 * @param httpStatus    статус-код ответа или {@code null}, если ответа не было
 * @param responseNanos время ответа в наносекундах
 * @param reason        пояснение (причина ошибки, текст ответа и т.п.)
 */
public record RequestResult(
        String url, OutcomeCategory category, Integer httpStatus, long responseNanos, String reason) {

    /** Получен успешный или неуспешный ответ сервера. */
    public static RequestResult response(String url, int status, long responseNanos) {
        return new RequestResult(url, OutcomeCategory.RESPONSE, status, responseNanos, "HTTP " + status);
    }

    /** Истёк таймаут: время ответа приравнивается к границе таймаута. */
    public static RequestResult timeout(String url, long timeoutNanos, String reason) {
        return new RequestResult(url, OutcomeCategory.TIMEOUT, null, timeoutNanos, reason);
    }

    /** Сетевая ошибка: соединение не установлено или имя не разрешено. */
    public static RequestResult networkError(String url, long responseNanos, String reason) {
        return new RequestResult(url, OutcomeCategory.NETWORK_ERROR, null, responseNanos, reason);
    }

    /** Задача прервана. */
    public static RequestResult interrupted(String url, long responseNanos, String reason) {
        return new RequestResult(url, OutcomeCategory.INTERRUPTED, null, responseNanos, reason);
    }

    /** Задача не выполнена (отказ). */
    public static RequestResult rejected(String url, String reason) {
        return new RequestResult(url, OutcomeCategory.REJECTED, null, 0L, reason);
    }

    /** Был ли получен ответ с кодом. */
    public boolean hasStatus() {
        return httpStatus != null;
    }

    /** Время ответа в миллисекундах с дробной частью — для вывода. */
    public double responseMillis() {
        return responseNanos / 1_000_000.0;
    }

    /** Код ответа словом: код либо прочерк, если ответа не было. */
    public String statusText() {
        return httpStatus == null ? "—" : httpStatus.toString();
    }

    /** Короткая строка для отчёта: адрес, категория, код, время. */
    public String reportLine() {
        return String.format(
                Locale.ROOT, "%-42s %-16s %-5s %9.3f мс  %s",
                url, category.label(), statusText(), responseMillis(), reason == null ? "" : reason);
    }
}
