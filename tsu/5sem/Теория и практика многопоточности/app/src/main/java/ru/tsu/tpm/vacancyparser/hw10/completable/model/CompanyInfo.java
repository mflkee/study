package ru.tsu.tpm.vacancyparser.hw10.completable.model;

/**
 * Сведения о компании из второго источника (справочник компаний).
 *
 * <p>Второй независимый асинхронный результат ДЗ 10. Если справочник недоступен, {@code exceptionally}
 * подставляет заглушку ({@link #unknown(String)}): вакансия остаётся в результате, но помечается флагом
 * {@link #fallback()}. Так сбой одного источника не выбрасывает вакансию целиком — это и есть
 * «частичный результат» из второй части задания.
 *
 * @param id            идентификатор вакансии, к которой относится компания
 * @param name          название компании
 * @param rating        рейтинг компании (0..5)
 * @param openVacancies число открытых вакансий
 * @param fallback      {@code true}, если данные подставлены заглушкой после сбоя источника
 */
public record CompanyInfo(String id, String name, double rating, int openVacancies, boolean fallback) {

    /** Название компании в заглушке. */
    public static final String UNKNOWN_NAME = "нет данных";

    public CompanyInfo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("идентификатор компании не задан");
        }
        if (rating < 0 || rating > 5) {
            throw new IllegalArgumentException("рейтинг компании вне диапазона 0..5: " + rating);
        }
        if (openVacancies < 0) {
            throw new IllegalArgumentException("число вакансий не может быть отрицательным");
        }
    }

    /** Заглушка на случай недоступности справочника. */
    public static CompanyInfo unknown(String id) {
        return new CompanyInfo(id, UNKNOWN_NAME, 0.0, 0, true);
    }

    /** Подставлена ли заглушка вместо реальных данных. */
    public boolean isFallback() {
        return fallback;
    }
}
