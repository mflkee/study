package ru.tsu.tpm.vacancyparser.hw09.callable.aggregator;

import java.util.List;

/**
 * Отчёт об одном цикле агрегации.
 *
 * <p>Результаты цикла разделены на успехи и сбои: успешные обрабатываются, сбойные фиксируются
 * отдельно. Отдельное поле {@code sourceError} есть на случай, когда недоступен сам источник списка
 * идентификаторов — тогда цикл пуст, но сервис не падает.
 *
 * @param cycle          номер цикла
 * @param requestedIds   идентификаторы, запрошенные в этом цикле
 * @param successes      успешно полученные данные
 * @param failures       сбои по отдельным сущностям
 * @param sourceError    причина недоступности источника списка (или {@code null})
 * @param durationNanos  длительность цикла
 */
public record CycleReport(
        long cycle,
        List<String> requestedIds,
        List<EntityData> successes,
        List<Failure> failures,
        String sourceError,
        long durationNanos) {

    public CycleReport {
        requestedIds = List.copyOf(requestedIds);
        successes = List.copyOf(successes);
        failures = List.copyOf(failures);
    }

    /** Успешен ли цикл полностью (источник доступен и ни одного сбоя). */
    public boolean fullySuccessful() {
        return sourceError == null && failures.isEmpty();
    }

    /** Длительность цикла в миллисекундах. */
    public double durationMillis() {
        return durationNanos / 1_000_000.0;
    }
}
