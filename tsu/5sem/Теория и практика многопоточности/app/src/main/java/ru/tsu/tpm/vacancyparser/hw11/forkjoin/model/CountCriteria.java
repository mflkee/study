package ru.tsu.tpm.vacancyparser.hw11.forkjoin.model;

import java.util.Locale;
import ru.tsu.tpm.vacancyparser.hw10.completable.service.VacancyScoring;

/**
 * Критерий подсчёта: какие агрегированные записи считать «подходящими».
 *
 * <p>Проверка включает нормализацию заголовка — ту же операцию, что делал конвейер ДЗ 10. Это не
 * украшение: именно она делает листовую работу партии заметной, иначе подсчёт миллиона записей
 * занимает меньше времени, чем накладные расходы на разбиение задач, и Fork/Join нечего показать.
 *
 * @param city      город (или {@code null} — любой)
 * @param minSalary нижняя граница зарплаты включительно
 * @param keyword   слово, которое должно встречаться в нормализованном заголовке
 *                  (или {@code null}/пусто — не проверяется)
 */
public record CountCriteria(String city, int minSalary, String keyword) {

    public CountCriteria {
        if (minSalary < 0) {
            throw new IllegalArgumentException("нижняя граница зарплаты не может быть отрицательной");
        }
        city = city == null || city.isBlank() ? null : city.trim();
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
    }

    /** Подходит ли запись под критерий. */
    public boolean matches(AggregatedVacancy vacancy) {
        if (city != null && !city.equals(vacancy.city())) {
            return false;
        }
        if (vacancy.salary() < minSalary) {
            return false;
        }
        if (keyword != null) {
            String normalized = VacancyScoring.normalizeTitle(vacancy.title());
            if (!normalized.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    /** Человекочитаемое описание критерия для отчёта. */
    public String label() {
        StringBuilder text = new StringBuilder();
        text.append("город: ").append(city == null ? "любой" : city);
        text.append(", зарплата от ").append(minSalary).append(" ₽");
        text.append(", слово в заголовке: ").append(keyword == null ? "не проверяется" : keyword);
        return text.toString();
    }
}
