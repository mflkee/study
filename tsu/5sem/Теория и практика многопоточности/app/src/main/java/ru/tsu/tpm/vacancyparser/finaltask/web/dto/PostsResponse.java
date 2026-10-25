package ru.tsu.tpm.vacancyparser.finaltask.web.dto;

import ru.tsu.tpm.vacancyparser.finaltask.model.CrawlReport;

/**
 * Ответ {@code POST /final/posts}.
 *
 * @param acceptedUrls   сколько адресов принято в очередь
 * @param duplicateUrls  сколько адресов оказались дубликатами, пустыми или не HTTP(S)
 * @param savedFromBody  сколько вакансий сохранено из тела запроса без сети
 * @param queueSize      сколько адресов осталось в очереди после обработки
 * @param crawl          отчёт об обходе ({@code null}, если обход не запрашивался)
 * @param message        человекочитаемое пояснение
 */
public record PostsResponse(
        int acceptedUrls,
        int duplicateUrls,
        int savedFromBody,
        int queueSize,
        CrawlReport crawl,
        String message) {
}
