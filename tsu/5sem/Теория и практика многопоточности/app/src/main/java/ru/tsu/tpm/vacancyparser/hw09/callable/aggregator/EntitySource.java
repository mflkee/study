package ru.tsu.tpm.vacancyparser.hw09.callable.aggregator;

import java.util.List;

/**
 * Источник списка идентификаторов сущностей для очередного цикла агрегации.
 *
 * <p>Метод может выбросить исключение (источник недоступен) — сервис обязан это пережить и выполнить
 * следующий цикл.
 */
@FunctionalInterface
public interface EntitySource {

    /** Список идентификаторов на текущий момент. */
    List<String> entities() throws Exception;
}
