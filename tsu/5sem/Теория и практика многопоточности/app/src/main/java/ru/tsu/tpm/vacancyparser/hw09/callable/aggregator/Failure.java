package ru.tsu.tpm.vacancyparser.hw09.callable.aggregator;

/**
 * Сбой при получении данных по одной сущности.
 *
 * @param id     идентификатор сущности
 * @param reason текст причины сбоя
 */
public record Failure(String id, String reason) {
}
