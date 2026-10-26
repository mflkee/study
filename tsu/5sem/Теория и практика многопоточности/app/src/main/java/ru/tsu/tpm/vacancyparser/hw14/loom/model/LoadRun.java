package ru.tsu.tpm.vacancyparser.hw14.loom.model;

import java.util.List;
import java.util.Objects;

/**
 * Прогон загрузки страниц: итоговые метрики и сами результаты.
 *
 * <p>Результаты лежат в порядке номеров страниц независимо от того, в каком порядке их выполнили
 * потоки: при подаче задачи индекс известен, а сборка идёт после ожидания всех задач. Так отчёт
 * получается детерминированным, хотя внутри царит планировщик.
 *
 * @param metrics итоговые метрики прогона
 * @param pages   результаты загрузки страниц в порядке номеров
 */
public record LoadRun(BenchmarkResult metrics, List<PageLoad> pages) {

    public LoadRun {
        Objects.requireNonNull(metrics, "metrics");
        pages = List.copyOf(pages);
    }

    /** Все поданные страницы дали ровно один результат, и все загрузки успешны. */
    public boolean lossless() {
        if (pages.size() != metrics.tasks() || metrics.failed() > 0 || metrics.cancelled() > 0) {
            return false;
        }
        long distinct = pages.stream().map(PageLoad::pageNumber).distinct().count();
        return distinct == pages.size() && pages.stream().allMatch(PageLoad::ok);
    }

    /** Сколько страниц не загрузилось. */
    public int failedPages() {
        return (int) pages.stream().filter(page -> !page.ok()).count();
    }

    /** Заголовки успешно загруженных страниц в порядке номеров. */
    public List<String> titles() {
        return pages.stream().filter(PageLoad::ok).map(PageLoad::title).toList();
    }

    /** Первые {@code limit} результатов для журнала. */
    public List<PageLoad> sample(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return pages.stream().limit(limit).toList();
    }
}
