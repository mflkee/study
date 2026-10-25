package ru.tsu.tpm.vacancyparser.finaltask.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import ru.tsu.tpm.vacancyparser.finaltask.model.Vacancy;

/**
 * Вакансия в ответе API.
 *
 * <p>Отдаётся отдельный record, а не JPA-сущность: ответ API не должен зависеть от схемы БД и от того,
 * какие поля Hibernate решит подгрузить.
 */
public record VacancyDto(
        Long id,
        String url,
        String title,
        String company,
        Integer salary,
        String salaryText,
        String city,
        String requirements,
        LocalDate publishedAt,
        Instant fetchedAt) {

    public static VacancyDto from(Vacancy vacancy) {
        return new VacancyDto(
                vacancy.getId(),
                vacancy.getUrl(),
                vacancy.getTitle(),
                vacancy.getCompany(),
                vacancy.getSalary(),
                vacancy.getSalaryText(),
                vacancy.getCity(),
                vacancy.getRequirements(),
                vacancy.getPublishedAt(),
                vacancy.getFetchedAt());
    }
}
