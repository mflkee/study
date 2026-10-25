package ru.tsu.tpm.vacancyparser.hw10.completable.model;

/**
 * Данные вакансии из первого источника (API вакансий).
 *
 * <p>Первый из двух <b>независимых</b> асинхронных результатов, которые в ДЗ 10 объединяются в один
 * объект: подробности вакансии и сведения о компании приходят из разных источников, поэтому их можно
 * запрашивать одновременно и склеивать через {@code thenCombine}.
 *
 * @param id        идентификатор вакансии — тот самый «ID товара/код валюты» из текста задания
 * @param title     заголовок вакансии
 * @param city      город
 * @param salaryFrom нижняя граница зарплаты, рубли
 * @param salaryTo   верхняя граница зарплаты, рубли
 */
public record VacancyDetails(String id, String title, String city, int salaryFrom, int salaryTo) {

    public VacancyDetails {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("идентификатор вакансии не задан");
        }
        if (salaryFrom < 0 || salaryTo < salaryFrom) {
            throw new IllegalArgumentException(
                    "некорректный диапазон зарплаты: " + salaryFrom + ".." + salaryTo);
        }
    }

    /** Средняя зарплата диапазона — используется при расчёте привлекательности. */
    public int middleSalary() {
        return (salaryFrom + salaryTo) / 2;
    }
}
