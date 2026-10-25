package ru.tsu.tpm.vacancyparser.hw11.forkjoin.count;

import java.util.List;
import java.util.Set;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.AggregatedVacancy;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountCriteria;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountResult;

/**
 * Последовательный подсчёт — база сравнения для Fork/Join.
 *
 * <p>Тот же критерий, тот же набор, тот же агрегат: отличаться должны только способ выполнения и время.
 * Любое расхождение {@link CountResult} означает ошибку в разбиении или сложении, поэтому результаты
 * двух вариантов сравниваются напрямую (record сравнивается по значению).
 */
public final class SequentialCounter {

    private SequentialCounter() {
    }

    /** Посчитать набор в текущем потоке одним проходом. */
    public static CountSummary count(List<AggregatedVacancy> data, CountCriteria criteria) {
        if (data == null || criteria == null) {
            throw new IllegalArgumentException("набор и критерий обязательны");
        }
        long matched = 0;
        long remote = 0;
        long salarySum = 0;
        int maxSalary = 0;

        long start = System.nanoTime();
        for (AggregatedVacancy vacancy : data) {
            if (!criteria.matches(vacancy)) {
                continue;
            }
            matched++;
            if (vacancy.remote()) {
                remote++;
            }
            salarySum += vacancy.salary();
            maxSalary = Math.max(maxSalary, vacancy.salary());
        }
        long durationNanos = System.nanoTime() - start;

        CountResult result = new CountResult(data.size(), matched, remote, salarySum, maxSalary);
        return new CountSummary(
                "последовательно",
                result,
                durationNanos,
                1,
                1,
                0L,
                1,
                Set.of(Thread.currentThread().getName()),
                0,
                1);
    }
}
