package ru.tsu.tpm.vacancyparser.hw03.parallel;

import java.util.List;

/**
 * Полный итог сравнения: результаты обеих ветвей и статистика по каждой операции.
 *
 * @param sequentialResults результаты, полученные последовательной ветвью
 * @param parallelResults   результаты, полученные параллельной ветвью
 * @param comparisons       статистика по операциям в порядке «фильтрация → преобразование → агрегация»
 * @param warmupRuns        фактическое число прогревочных прогонов
 * @param repeats           фактическое число повторов
 * @param config            фактически применённые параметры демонстрации
 */
public record BenchmarkOutcome(
        OperationResults sequentialResults,
        OperationResults parallelResults,
        List<OperationComparison> comparisons,
        int warmupRuns,
        int repeats,
        BenchmarkConfig config) {

    public BenchmarkOutcome {
        comparisons = List.copyOf(comparisons);
    }

    /** Все ли варианты дали одинаковый результат: расхождение означает негодный замер. */
    public boolean resultsMatch() {
        return comparisons.stream().allMatch(OperationComparison::resultsMatch);
    }
}
