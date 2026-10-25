package ru.tsu.tpm.vacancyparser.finaltask.model;

import java.util.List;

/**
 * Отчёт об одном обходе списка адресов.
 *
 * <p>Отчёт возвращается эндпоинтами {@code POST /final/posts} и {@code POST /final/crawl}, а также
 * доступен в {@code GET /final/stats} как {@code lastRun}. Он содержит измеримые доказательства
 * многопоточности: суммарное время обхода, время по каждому адресу и имена потоков.
 *
 * @param runId         номер обхода (сквозной счётчик)
 * @param trigger       что запустило обход: старт приложения, расписание или HTTP-запрос
 * @param requested     сколько уникальных адресов было в обходе
 * @param succeeded     сколько страниц успешно получено и разобрано
 * @param failed        сколько адресов завершилось сбоем
 * @param saved         сколько новых записей добавлено в H2
 * @param updated       сколько существующих записей обновлено
 * @param durationNanos суммарное время обхода
 * @param skipped       обход пропущен, потому что предыдущий ещё выполняется
 * @param note          пояснение (причина пропуска или таймаута)
 * @param workerThreads имена потоков пула, участвовавших в обходе
 * @param outcomes      результат по каждому адресу (в порядке адресов)
 */
public record CrawlReport(
        long runId,
        String trigger,
        int requested,
        int succeeded,
        int failed,
        int saved,
        int updated,
        long durationNanos,
        boolean skipped,
        String note,
        List<String> workerThreads,
        List<UrlOutcome> outcomes) {

    public CrawlReport {
        outcomes = List.copyOf(outcomes);
        workerThreads = List.copyOf(workerThreads);
    }

    /** Пустой обход: очередь была пуста, делать нечего. */
    public static CrawlReport empty(String trigger) {
        return new CrawlReport(0L, trigger, 0, 0, 0, 0, 0, 0L, false,
                "очередь пуста — обходить нечего", List.of(), List.of());
    }

    /** Обход пропущен: параллельный запуск запрещён, чтобы не дублировать работу. */
    public static CrawlReport skipped(String trigger, int requested) {
        return new CrawlReport(0L, trigger, requested, 0, 0, 0, 0, 0L, true,
                "предыдущий обход ещё выполняется — запуск пропущен", List.of(), List.of());
    }

    /** Суммарное время обхода в миллисекундах. */
    public double durationMillis() {
        return durationNanos / 1_000_000.0;
    }
}
