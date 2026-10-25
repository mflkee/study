package ru.tsu.tpm.vacancyparser.hw11.forkjoin.model;

/**
 * Агрегированная запись о вакансии — единица подсчёта ДЗ 11.
 *
 * <p>Это та самая «агрегированная сущность из прошлых домашних заданий»: результат разбора страницы
 * (вакансия + компания + оценка), ужатый до полей, которые нужны счётчику. Список таких записей
 * накапливается парсером, а ДЗ 11 отвечает на вопрос «сколько их и какие они» — рекурсивно, партиями.
 *
 * @param id     идентификатор вакансии
 * @param city   город
 * @param title  заголовок (по нему проверяется профиль)
 * @param salary зарплата, рубли
 * @param remote удалённая работа
 */
public record AggregatedVacancy(String id, String city, String title, int salary, boolean remote) {

    public AggregatedVacancy {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("идентификатор вакансии не задан");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("город не задан: " + id);
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("заголовок не задан: " + id);
        }
        if (salary < 0) {
            throw new IllegalArgumentException("зарплата не может быть отрицательной: " + salary);
        }
    }
}
