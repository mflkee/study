package ru.tsu.tpm.vacancyparser.hw10.completable.model;

import java.util.Locale;

/**
 * Итоговый объект ДЗ 10: два независимых асинхронных результата, объединённые в один.
 *
 * <p>Собирается функцией {@link #combine} в {@code thenCombine}, затем проходит «тяжёлое»
 * преобразование ({@link #withAttractiveness}) в {@code thenApplyAsync} на собственном исполнителе.
 * Поля-источники остаются видимыми: по ним в отчёте видно, что именно дал каждый из двух запросов.
 *
 * @param details         данные вакансии
 * @param company         данные компании (может быть заглушкой)
 * @param normalizedTitle нормализованный заголовок — результат преобразования
 * @param attractiveness  оценка привлекательности вакансии — результат преобразования
 */
public record FinalResult(
        VacancyDetails details,
        CompanyInfo company,
        String normalizedTitle,
        double attractiveness) {

    /** Объединить два независимых результата в один объект (для {@code thenCombine}). */
    public static FinalResult combine(VacancyDetails details, CompanyInfo company) {
        if (details == null) {
            throw new IllegalArgumentException("данные вакансии не получены");
        }
        if (company == null) {
            throw new IllegalArgumentException("данные компании не получены");
        }
        if (!details.id().equals(company.id())) {
            throw new IllegalArgumentException("идентификаторы не совпадают: "
                    + details.id() + " и " + company.id());
        }
        return new FinalResult(details, company, details.title(), 0.0);
    }

    /** Копия с результатами преобразования (выполняется на собственном исполнителе). */
    public FinalResult withAttractiveness(String normalized, double score) {
        return new FinalResult(details, company, normalized, score);
    }

    /** Идентификатор вакансии. */
    public String id() {
        return details.id();
    }

    /** Заголовок вакансии. */
    public String title() {
        return details.title();
    }

    /** Город вакансии. */
    public String city() {
        return details.city();
    }

    /** Зарплата «в середине» диапазона. */
    public int middleSalary() {
        return details.middleSalary();
    }

    /** Подставлена ли заглушка компании вместо реальных данных. */
    public boolean companyFallback() {
        return company.fallback();
    }

    /** Строка результата для консоли и отчёта. */
    public String reportLine() {
        return String.format(
                Locale.ROOT,
                "%s | %s | %s | %d–%d ₽ | компания: %s (рейтинг %.1f%s) | оценка %.2f",
                id(),
                title(),
                city(),
                details.salaryFrom(),
                details.salaryTo(),
                company.name(),
                company.rating(),
                companyFallback() ? ", заглушка" : "",
                attractiveness);
    }
}
