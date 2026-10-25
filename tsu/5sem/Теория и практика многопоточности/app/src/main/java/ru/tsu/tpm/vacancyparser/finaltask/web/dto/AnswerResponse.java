package ru.tsu.tpm.vacancyparser.finaltask.web.dto;

import java.util.List;

/**
 * Ответ {@code GET /final/answer}.
 *
 * @param items      вакансии текущей страницы
 * @param total      всего записей после фильтрации
 * @param page       номер страницы с нуля
 * @param size       размер страницы
 * @param totalPages число страниц
 * @param sort       применённое поле сортировки
 * @param direction  направление сортировки
 * @param city       фильтр по городу
 * @param query      текстовый фильтр
 * @param minSalary  фильтр по минимальной зарплате
 */
public record AnswerResponse(
        List<VacancyDto> items,
        long total,
        int page,
        int size,
        int totalPages,
        String sort,
        String direction,
        String city,
        String query,
        Integer minSalary) {
}
