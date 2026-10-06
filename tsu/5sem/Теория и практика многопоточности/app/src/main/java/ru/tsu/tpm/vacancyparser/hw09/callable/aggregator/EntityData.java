package ru.tsu.tpm.vacancyparser.hw09.callable.aggregator;

/**
 * Успешно полученные данные по одной сущности.
 *
 * @param id    идентификатор сущности
 * @param value полученное значение
 */
public record EntityData(String id, String value) {
}
