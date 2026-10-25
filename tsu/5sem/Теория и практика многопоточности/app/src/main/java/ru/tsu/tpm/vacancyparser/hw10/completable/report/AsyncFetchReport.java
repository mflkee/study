package ru.tsu.tpm.vacancyparser.hw10.completable.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.FetchFailure;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.FinalResult;
import ru.tsu.tpm.vacancyparser.hw10.completable.service.FetchOutcome;

/**
 * Строки отчёта ДЗ 10 — чистые функции от результатов прогона.
 *
 * <p>Вывод вынесен из демонстрации, чтобы его можно было проверить без сети: сравнение времени,
 * пометки об изоляции сбоев и обрезка журнала — это форматирование, а не логика. В отчёт попадают те
 * же строки, что печатаются в консоль.
 */
public final class AsyncFetchReport {

    private AsyncFetchReport() {
    }

    /** Итоговые строки прогона: сводка и список полученных объектов. */
    public static List<String> resultLines(FetchOutcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add(outcome.summaryLine());
        lines.add("потоки этапов: " + outcome.threadNames());
        for (FinalResult result : outcome.results()) {
            lines.add(result.reportLine());
        }
        for (FetchFailure failure : outcome.failures()) {
            lines.add("сбой " + failure.reportLine());
        }
        return lines;
    }

    /** Во сколько раз асинхронная версия быстрее синхронной (0, если измерить нельзя). */
    public static double speedup(FetchOutcome async, FetchOutcome sync) {
        if (async == null || sync == null || async.durationNanos() <= 0) {
            return 0.0;
        }
        return sync.durationNanos() / (double) async.durationNanos();
    }

    /** Строка сравнения времени асинхронной и синхронной версий. */
    public static String comparisonLine(FetchOutcome async, FetchOutcome sync) {
        double speedup = speedup(async, sync);
        if (speedup <= 0) {
            return "сравнение недоступно: нет измерений";
        }
        return String.format(
                Locale.ROOT,
                "синхронно %.3f мс, асинхронно %.3f мс — ускорение %.2f× "
                        + "(в синхронной версии запросы идут друг за другом, в асинхронной перекрываются)",
                sync.durationMillis(), async.durationMillis(), speedup);
    }

    /** Совпадают ли наборы полученных идентификаторов в двух прогонах. */
    public static boolean sameResults(FetchOutcome first, FetchOutcome second) {
        return ids(first).equals(ids(second));
    }

    /** Идентификаторы успешных результатов в порядке прогона. */
    public static List<String> ids(FetchOutcome outcome) {
        return outcome.results().stream().map(FinalResult::id).toList();
    }

    /**
     * Пометки об изоляции сбоев по конкретным идентификаторам.
     *
     * @param outcome       результат частичного прогона
     * @param ids           запрошенные идентификаторы
     * @param detailsFailId идентификатор со сбоем источника вакансий (ожидается исключение из результата)
     * @param companyFailId идентификатор со сбоем справочника (ожидается заглушка компании)
     */
    public static List<String> isolationLines(
            FetchOutcome outcome, List<String> ids, String detailsFailId, String companyFailId) {
        List<String> lines = new ArrayList<>();
        for (String id : ids) {
            FinalResult result = outcome.results().stream()
                    .filter(item -> item.id().equals(id))
                    .findFirst()
                    .orElse(null);
            if (result == null) {
                String mark = id.equals(detailsFailId)
                        ? "исключён из результата: сбой источника вакансий обработан handle"
                        : "исключён из результата (сбой не был ожидаем)";
                lines.add(id + ": " + mark);
                continue;
            }
            if (result.companyFallback()) {
                lines.add(id + ": вакансия сохранена, компания подставлена заглушкой "
                        + "(сбой справочника обработан exceptionally)");
            } else {
                lines.add(id + ": получен полностью, компания " + result.company().name());
            }
        }
        return lines;
    }

    /** Первые {@code limit} строк журнала с сохранением порядка. */
    public static List<String> traceHead(List<String> trace, int limit) {
        if (trace == null || trace.isEmpty() || limit <= 0) {
            return List.of();
        }
        return List.copyOf(trace.subList(0, Math.min(limit, trace.size())));
    }
}
