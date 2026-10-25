package ru.tsu.tpm.vacancyparser.hw10.completable.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.FetchFailure;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.FinalResult;

/**
 * Итог одного прогона выборки: что получено, что сломалось, сколько это заняло и на каких потоках.
 *
 * <p>Отдельный объект нужен, чтобы сравнение асинхронной и синхронной версий опиралось на одни и те же
 * измерения, а не на два разных способа печати. {@link #threadsUsed()} доказывает, что асинхронные
 * этапы действительно шли на разных потоках собственного пула.
 *
 * @param mode         режим прогона: {@code async} или {@code sync}
 * @param requested    сколько идентификаторов запрошено
 * @param results      успешно собранные объекты (в порядке входного списка)
 * @param failures     изолированные сбои
 * @param durationNanos длительность прогона
 * @param threadsUsed  имена потоков, на которых выполнялись этапы
 * @param sourceCalls  сколько обращений к внешним источникам сделано
 */
public record FetchOutcome(
        String mode,
        int requested,
        List<FinalResult> results,
        List<FetchFailure> failures,
        long durationNanos,
        Set<String> threadsUsed,
        int sourceCalls) {

    public FetchOutcome {
        results = List.copyOf(results);
        failures = List.copyOf(failures);
        threadsUsed = Set.copyOf(threadsUsed);
    }

    /** Длительность прогона в миллисекундах. */
    public double durationMillis() {
        return durationNanos / 1_000_000.0;
    }

    /** Сколько потоков участвовало в прогоне. */
    public int threadCount() {
        return threadsUsed.size();
    }

    /** Имена потоков одной строкой (для отчёта). */
    public String threadNames() {
        return threadsUsed.stream().sorted().reduce((left, right) -> left + ", " + right).orElse("—");
    }

    /** Строка итога для консоли. */
    public String summaryLine() {
        return String.format(
                Locale.ROOT,
                "%s: запрошено %d, получено %d, сбоев %d, обращений к источникам %d, "
                        + "длительность %.3f мс, потоков %d",
                mode, requested, results.size(), failures.size(), sourceCalls, durationMillis(), threadCount());
    }
}
