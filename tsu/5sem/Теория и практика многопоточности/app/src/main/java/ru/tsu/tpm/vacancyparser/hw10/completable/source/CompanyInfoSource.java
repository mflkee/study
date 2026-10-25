package ru.tsu.tpm.vacancyparser.hw10.completable.source;

import ru.tsu.tpm.vacancyparser.hw10.completable.model.CompanyInfo;

/**
 * Второй внешний источник: сведения о компании, разместившей вакансию.
 *
 * <p>Независим от {@link VacancyDetailsSource}: именно поэтому два запроса можно выполнять
 * одновременно и объединять их результаты через {@code thenCombine}.
 */
@FunctionalInterface
public interface CompanyInfoSource {

    /** Получить данные компании по идентификатору вакансии. */
    CompanyInfo fetchCompany(String id) throws Exception;
}
