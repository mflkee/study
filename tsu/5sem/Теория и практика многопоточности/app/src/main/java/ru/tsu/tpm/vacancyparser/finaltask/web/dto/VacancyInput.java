package ru.tsu.tpm.vacancyparser.finaltask.web.dto;

import java.time.LocalDate;
import ru.tsu.tpm.vacancyparser.finaltask.model.ParsedVacancy;

/**
 * Готовая вакансия в теле запроса — офлайн-режим без сети.
 *
 * <p>Проверяющий может не поднимать источник и не ждать обход: достаточно прислать уже известные поля
 * вакансии, и они попадут в H2 так же, как если бы были разобраны со страницы.
 *
 * @param url          адрес вакансии (обязателен, ключ дедупликации)
 * @param title        название
 * @param company      компания
 * @param salary       зарплата числом
 * @param salaryText   зарплата текстом
 * @param city         город
 * @param requirements требования
 * @param publishedAt  дата размещения (ISO, например 2026-10-01)
 */
public record VacancyInput(
        String url,
        String title,
        String company,
        Integer salary,
        String salaryText,
        String city,
        String requirements,
        LocalDate publishedAt) {

    /** Преобразовать во внутреннее представление разобранной страницы. */
    public ParsedVacancy toParsed() {
        String effectiveUrl = (url == null || url.isBlank()) ? "offline://manual/" + System.nanoTime() : url;
        return new ParsedVacancy(
                effectiveUrl,
                title,
                company,
                salary,
                salaryText,
                city,
                requirements,
                publishedAt);
    }
}
