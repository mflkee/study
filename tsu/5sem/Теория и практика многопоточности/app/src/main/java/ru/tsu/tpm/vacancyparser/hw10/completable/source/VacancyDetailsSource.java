package ru.tsu.tpm.vacancyparser.hw10.completable.source;

import ru.tsu.tpm.vacancyparser.hw10.completable.model.VacancyDetails;

/**
 * Первый внешний источник: подробности вакансии по идентификатору.
 *
 * <p>Функциональный интерфейс, чтобы в тестах источник подменялся лямбдой: реальный HTTP-клиент и
 * детерминированная заглушка должны быть взаимозаменяемы. Метод может выбросить исключение — это
 * штатная ситуация для внешнего API, и ДЗ 10 обязано её пережить.
 */
@FunctionalInterface
public interface VacancyDetailsSource {

    /** Получить данные вакансии. */
    VacancyDetails fetchDetails(String id) throws Exception;
}
