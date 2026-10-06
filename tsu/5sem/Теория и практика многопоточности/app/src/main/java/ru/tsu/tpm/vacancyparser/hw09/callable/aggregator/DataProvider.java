package ru.tsu.tpm.vacancyparser.hw09.callable.aggregator;

/**
 * Источник актуальных данных по одной сущности.
 *
 * <p>Имитирует внешний источник: метод может выбросить исключение. Сбой по одной сущности не должен
 * влиять на остальные — агрегатор изолирует его на уровне одной сущности.
 */
@FunctionalInterface
public interface DataProvider {

    /** Получить данные по идентификатору. */
    EntityData fetch(String id) throws Exception;
}
