package ru.tsu.tpm.vacancyparser.hw10.completable.model;

/**
 * Сбой по одному идентификатору, изолированный от остальных.
 *
 * <p>Появляется там, где {@code handle} поймал исключение конвейера: сбой одной задачи не отменяет
 * остальные, но должен быть виден в отчёте. {@code stage} говорит, на каком шаге всё сломалось
 * (детали, компания, преобразование).
 *
 * @param id      идентификатор запроса
 * @param stage   этап, на котором произошёл сбой
 * @param message описание ошибки
 */
public record FetchFailure(String id, String stage, String message) {

    /** Строка для консоли и отчёта. */
    public String reportLine() {
        return id + " (" + stage + "): " + message;
    }
}
