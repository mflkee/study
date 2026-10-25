package ru.tsu.tpm.vacancyparser.finaltask.model;

import java.time.LocalDate;

/**
 * Результат разбора одной страницы вакансии — ещё не сохранённая запись.
 *
 * <p>Отдельный тип нужен, чтобы парсер не зависел от JPA: его можно проверить юнит-тестом на строке
 * HTML, без базы и Spring-контекста.
 *
 * @param url          адрес страницы (он же ключ дедупликации)
 * @param title        название вакансии
 * @param company      компания ({@code null}, если не найдена)
 * @param salary       зарплата числом ({@code null} для «по договорённости»)
 * @param salaryText   исходная формулировка зарплаты
 * @param city         город
 * @param requirements требования (текст без HTML-разметки)
 * @param publishedAt  дата размещения ({@code null}, если не найдена)
 */
public record ParsedVacancy(
        String url,
        String title,
        String company,
        Integer salary,
        String salaryText,
        String city,
        String requirements,
        LocalDate publishedAt) {

    public ParsedVacancy {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("адрес вакансии не может быть пустым");
        }
        if (title == null || title.isBlank()) {
            title = "Без названия";
        }
    }
}
