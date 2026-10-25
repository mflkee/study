package ru.tsu.tpm.vacancyparser.hw12.collections.model;

/**
 * Карточка вакансии — значение, которое кэшируется в ДЗ 12.
 *
 * <p>Неизменяемый объект: кэш отдаёт одну и ту же карточку многим потокам, и менять её «на месте»
 * нельзя. Все поля — то, что нужно для показа и для рейтинга по зарплате.
 *
 * @param id            идентификатор вакансии
 * @param title         заголовок
 * @param city          город
 * @param salary        зарплата, рубли
 * @param companyRating рейтинг компании
 */
public record VacancyCard(String id, String title, String city, int salary, double companyRating) {

    public VacancyCard {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("идентификатор вакансии не задан");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("заголовок не задан: " + id);
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("город не задан: " + id);
        }
        if (salary < 0) {
            throw new IllegalArgumentException("зарплата не может быть отрицательной: " + id);
        }
        if (companyRating < 0 || companyRating > 5) {
            throw new IllegalArgumentException("рейтинг компании вне диапазона 0..5: " + companyRating);
        }
    }

    /** Строка для консоли и отчёта. */
    public String reportLine() {
        return id + " | " + title + " | " + city + " | " + salary + " ₽ | рейтинг " + companyRating;
    }
}
