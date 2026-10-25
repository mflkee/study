package ru.tsu.tpm.vacancyparser.finaltask.web.dto;

import java.util.List;
import java.util.Map;
import ru.tsu.tpm.vacancyparser.finaltask.model.CrawlReport;

/**
 * Ответ {@code GET /final/stats}: состояние приложения и доказательства многопоточности.
 *
 * @param vacanciesInDb     сколько вакансий в H2
 * @param queueSize         сколько адресов ждёт обхода
 * @param crawlRuns         сколько обходов запущено
 * @param urlsSubmitted     сколько адресов поставлено в обход за всё время
 * @param urlsSucceeded     сколько успешно разобрано
 * @param urlsFailed        сколько завершилось сбоем
 * @param saved             сколько записей добавлено
 * @param updated           сколько записей обновлено
 * @param poolSize          фактическое число потоков пула
 * @param activeThreads     сколько потоков пула сейчас работает
 * @param poolQueueSize     сколько задач ждёт в очереди пула
 * @param workerThreads     имена потоков, обрабатывавших страницы
 * @param perThreadProcessed сколько страниц обработал каждый поток
 * @param lastRun           отчёт последнего обхода
 * @param source            демонстрационный источник
 */
public record StatsResponse(
        long vacanciesInDb,
        int queueSize,
        long crawlRuns,
        long urlsSubmitted,
        long urlsSucceeded,
        long urlsFailed,
        long saved,
        long updated,
        int poolSize,
        int activeThreads,
        int poolQueueSize,
        List<String> workerThreads,
        Map<String, Long> perThreadProcessed,
        CrawlReport lastRun,
        SourceInfo source) {
}
