package ru.tsu.tpm.vacancyparser.hw03.parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Формирование вывода демонстрации ДЗ 3: условия замера, таблица времён, «холодные» прогоны,
 * разброс и анализ причин различия.
 *
 * <p>Весь вывод собран здесь, а не в сервисе замера, по двум причинам. Первая: печать внутри
 * измеряемого участка испортила бы замер, и анализ обязан идти <b>после</b> цифр. Вторая: строки
 * вывода — чистые функции от результатов, поэтому их можно проверить на искусственных временах,
 * не запуская миллион элементов.
 */
public final class ComparisonReport {

    /** Кто оказался быстрее по итогам замера. */
    public enum Winner {
        /** Быстрее последовательный вариант. */
        SEQUENTIAL,
        /** Быстрее параллельный вариант. */
        PARALLEL,
        /** Разница укладывается в разброс повторов — объявлять победителя нечестно. */
        WITHIN_NOISE
    }

    /**
     * Итог сравнения по одной операции.
     *
     * @param winner кто быстрее
     * @param ratio  во сколько раз быстрее победитель; при {@code WITHIN_NOISE} отношение средних
     *               всё равно возвращается, но как справочное, а не как вывод
     */
    public record Verdict(Winner winner, double ratio) {

        /** Явная фраза о том, кто быстрее, — именно её требует текст задания. */
        public String phrase() {
            return switch (winner) {
                case SEQUENTIAL -> String.format(
                        Locale.ROOT, "последовательный быстрее в %.2f раза", ratio);
                case PARALLEL -> String.format(
                        Locale.ROOT, "параллельный быстрее в %.2f раза", ratio);
                case WITHIN_NOISE -> String.format(
                        Locale.ROOT,
                        "значимой разницы не показано (отношение средних %.2f, разница в пределах разброса повторов)",
                        ratio);
            };
        }
    }

    private ComparisonReport() {
    }

    /**
     * Определить победителя операции.
     *
     * <p>Если интервалы {@code [min, max]} вариантов пересекаются, разница не объявляется: она
     * укладывается в шум планировщика, и любой «победитель» здесь был бы случайным.
     */
    public static Verdict verdict(OperationComparison comparison) {
        double ratio = comparison.parallelToSequentialRatio();

        if (comparison.differenceWithinNoise()) {
            return new Verdict(Winner.WITHIN_NOISE, ratio);
        }
        return ratio > 1.0
                ? new Verdict(Winner.PARALLEL, ratio)
                : new Verdict(Winner.SEQUENTIAL, 1.0 / ratio);
    }

    /** Блок условий замера. Имена потоков сюда не попадают — только их число (см. требование). */
    public static List<String> conditionsBlock(MeasurementConditions conditions) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Условия замера:");
        lines.add(row("версия JVM", conditions.jvmVersion()));
        lines.add(row("доступно процессоров", String.valueOf(conditions.availableProcessors())));
        lines.add(row(
                "параллелизм общего пула",
                conditions.commonPoolParallelism()
                        + (conditions.commonPoolMatchesProcessors() ? "  (= процессоров - 1)" : "")));
        lines.add(row(
                "наблюдаемое число потоков пула",
                conditions.observedThreads() + "  (снимок потоков во время параллельных проходов)"));
        lines.add(row("рабочих потоков создал пул", String.valueOf(conditions.commonPoolSize())));
        lines.add(row("размер списка", String.valueOf(conditions.listSize())));
        lines.add(row("диапазон значений", conditions.range()));
        lines.add(row(
                "повторов на вариант",
                conditions.repeats() + "  (плюс " + conditions.warmupRuns() + " прогрев)"));
        lines.add("");
        lines.add("    Используемый пул: ForkJoinPool.commonPool() — общий на весь процесс; он разделяется");
        lines.add("    с потоками приложения (Tomcat, планировщик), поэтому фактическое число рабочих");
        lines.add("    зависит от числа процессоров, доступных JVM.");
        return lines;
    }

    /**
     * Таблица результатов: по каждой операции — средние времена обоих вариантов, их отношение и
     * минимум/максимум по повторам.
     */
    public static List<String> resultsTable(List<OperationComparison> comparisons) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Результаты замера (среднее по повторам):");
        lines.add(String.format(
                Locale.ROOT,
                "    %-15s | %11s | %18s | %14s | %15s | %15s",
                "операция", "stream(), мс", "parallelStream(), мс", "parallel/stream",
                "stream min..max", "parallel min..max"));

        for (OperationComparison comparison : comparisons) {
            OperationComparison.TimingStats sequential = comparison.sequential();
            OperationComparison.TimingStats parallel = comparison.parallel();
            lines.add(String.format(
                    Locale.ROOT,
                    "    %-15s | %11.3f | %18.3f | %13.2f× | %15s | %15s",
                    comparison.name(),
                    OperationComparison.millis(sequential.average()),
                    OperationComparison.millis(parallel.average()),
                    comparison.parallelToSequentialRatio(),
                    range(sequential),
                    range(parallel)));
        }
        return lines;
    }

    /**
     * Отдельная строка о разбросе повторов по каждой операции.
     *
     * <p>Без неё нельзя судить, значима ли разница: если разброс сопоставим с разницей между
     * вариантами, вывод «быстрее в 1.05 раза» ничего не значит.
     */
    public static List<String> spreadBlock(List<OperationComparison> comparisons) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Разброс повторов (max - min), мс:");
        for (OperationComparison comparison : comparisons) {
            lines.add(String.format(
                    Locale.ROOT,
                    "    %-15s | stream() %8.3f | parallelStream() %8.3f",
                    comparison.name(),
                    OperationComparison.millis(comparison.sequential().spread()),
                    OperationComparison.millis(comparison.parallel().spread())));
        }
        return lines;
    }

    /**
     * «Холодные» первые прогоны — отдельно от статистики.
     *
     * <p>Печатаются как наглядное объяснение того, почему одиночный замер недействителен: первый
     * прогон идёт по интерпретатору, и его время обычно заметно больше прогретых повторов.
     */
    public static List<String> coldRunBlock(List<OperationComparison> comparisons) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Холодный (первый) прогон — в статистику не входит, показывает эффект JIT:");
        for (OperationComparison comparison : comparisons) {
            lines.add(String.format(
                    Locale.ROOT,
                    "    %-15s | stream() %9.3f мс | parallelStream() %9.3f мс",
                    comparison.name(),
                    OperationComparison.millis(comparison.sequential().cold()),
                    OperationComparison.millis(comparison.parallel().cold())));
        }
        return lines;
    }

    /**
     * Анализ причин различия по каждой операции.
     *
     * <p>Это и есть содержательная часть задания: мало сказать «параллельный быстрее», нужно
     * назвать, почему, и привязать объяснение к числу процессоров и накладным расходам.
     */
    public static List<String> analysisBlock(
            List<OperationComparison> comparisons, MeasurementConditions conditions) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Анализ причин различия по операциям:");

        for (OperationComparison comparison : comparisons) {
            lines.add("    " + comparison.name() + ": " + verdict(comparison).phrase());
            lines.add("        " + explanation(comparison, conditions));
            spreadNote(comparison).ifPresent(note -> lines.add("        " + note));
        }
        return lines;
    }

    /**
     * Замечание о том, что разброс повторов сопоставим с разницей между вариантами.
     *
     * <p>Нужно, чтобы вывод «значимой разницы не показано» не выглядел ошибкой: читатель должен
     * видеть, что замер именно неустойчив, и по каким числам это видно. Диагноз не выдумывается —
     * перечисляются возможные причины и указывается, что разброс можно уменьшить повторами.
     */
    public static Optional<String> spreadNote(OperationComparison comparison) {
        if (verdict(comparison).winner() != Winner.WITHIN_NOISE) {
            return Optional.empty();
        }
        OperationComparison.TimingStats wider = comparison.sequential().spread()
                        .compareTo(comparison.parallel().spread()) >= 0
                ? comparison.sequential()
                : comparison.parallel();
        double difference = Math.abs(
                OperationComparison.millis(comparison.sequential().average())
                        - OperationComparison.millis(comparison.parallel().average()));

        return Optional.of(String.format(
                Locale.ROOT,
                "разброс повторов (%s: %.3f мс) сравним с разницей средних (%.3f мс), поэтому "
                        + "различие не подтверждено: возможные причины — внешние помехи, паузы сборки "
                        + "мусора, стадия компиляции JIT; для вывода нужны повторы с меньшим разбросом",
                wider == comparison.sequential() ? "stream()" : "parallelStream()",
                OperationComparison.millis(wider.spread()),
                difference));
    }

    /**
     * Объяснение причины различия для одной операции.
     *
     * <p>Текст различается по исходу: при проигрыше параллельного варианта называются накладные
     * расходы, которые не покрыл выигрыш от разделения работы; при выигрыше — что работа была
     * разделена между потоками общего пула. В обоих случаях присутствует число процессоров, иначе
     * объяснение было бы оторвано от конкретной ситуации, в которой сделан замер.
     */
    public static String explanation(OperationComparison comparison, MeasurementConditions conditions) {
        Verdict verdict = verdict(comparison);
        String hardware = String.format(
                Locale.ROOT,
                "доступно процессоров: %d, параллелизм общего пула: %d",
                conditions.availableProcessors(),
                conditions.commonPoolParallelism());

        return switch (verdict.winner()) {
            case PARALLEL -> String.format(
                    Locale.ROOT,
                    "работа разделена между потоками общего пула (%s), "
                            + "накладные расходы на разбиение окупились объёмом работы",
                    hardware);
            case SEQUENTIAL -> String.format(
                    Locale.ROOT,
                    "выигрыш от разделения работы не покрыл накладных расходов на разбиение, "
                            + "синхронизацию счётчиков и обратную сборку (%s), "
                            + "при таком объёме данных и такой цене операции на элемент",
                    hardware);
            case WITHIN_NOISE -> String.format(
                    Locale.ROOT,
                    "разница лежит внутри разброса повторов, поэтому разделение работы не дало "
                            + "измеримого выигрыша и не внесло измеримого проигрыша (%s)",
                    hardware);
        };
    }

    /**
     * Полный вывод отчёта в том порядке, в котором он печатается: условия замера → таблица времён
     * → разброс → «холодные» прогоны → сверка результатов → анализ причин.
     *
     * <p>Порядок собран в одном месте намеренно: анализ обязан идти <b>после</b> цифр, и это
     * свойство проверяется тестом, а не остаётся на совести вызывающего кода.
     */
    public static List<String> fullReport(BenchmarkOutcome outcome, MeasurementConditions conditions) {
        List<String> lines = new ArrayList<>();
        lines.addAll(conditionsBlock(conditions));
        lines.addAll(resultsTable(outcome.comparisons()));
        lines.addAll(spreadBlock(outcome.comparisons()));
        lines.addAll(coldRunBlock(outcome.comparisons()));
        lines.addAll(resultsCheckBlock(outcome));
        lines.addAll(analysisBlock(outcome.comparisons(), conditions));
        return lines;
    }

    /**
     * Сверка результатов обоих вариантов.
     *
     * <p>Расхождение — не деталь оформления, а признак негодного замера, поэтому о нём говорится
     * прямо, а не умалчивается.
     */
    public static List<String> resultsCheckBlock(BenchmarkOutcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Сверка результатов обоих вариантов:");
        lines.add(String.format(
                Locale.ROOT,
                "    %-15s | stream(): %-22s | parallelStream(): %s",
                "фильтрация",
                "чётных " + outcome.sequentialResults().evenCount(),
                "чётных " + outcome.parallelResults().evenCount()));
        lines.add(String.format(
                Locale.ROOT,
                "    %-15s | stream(): %-22s | parallelStream(): %s",
                "преобразование",
                outcome.sequentialResults().doubled().equals(outcome.parallelResults().doubled())
                        ? "списки совпали"
                        : "списки РАЗОШЛИСЬ",
                outcome.sequentialResults().doubled().equals(outcome.parallelResults().doubled())
                        ? "списки совпали"
                        : "списки РАЗОШЛИСЬ"));
        lines.add(String.format(
                Locale.ROOT,
                "    %-15s | stream(): %-22s | parallelStream(): %s",
                "агрегация",
                "сумма " + outcome.sequentialResults().sum(),
                "сумма " + outcome.parallelResults().sum()));

        if (!outcome.resultsMatch()) {
            lines.add("ВНИМАНИЕ: результаты вариантов разошлись — замер недействителен");
        }
        return lines;
    }

    private static String range(OperationComparison.TimingStats stats) {
        return String.format(
                Locale.ROOT,
                "%.3f..%.3f",
                OperationComparison.millis(stats.min()),
                OperationComparison.millis(stats.max()));
    }

    private static String row(String label, String value) {
        return String.format(Locale.ROOT, "    %-32s %s", label, value);
    }
}
