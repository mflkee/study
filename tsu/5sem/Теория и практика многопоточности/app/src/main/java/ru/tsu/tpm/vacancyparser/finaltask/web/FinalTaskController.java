package ru.tsu.tpm.vacancyparser.finaltask.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.tsu.tpm.vacancyparser.finaltask.config.FinalTaskProperties;
import ru.tsu.tpm.vacancyparser.finaltask.model.CrawlReport;
import ru.tsu.tpm.vacancyparser.finaltask.model.ParsedVacancy;
import ru.tsu.tpm.vacancyparser.finaltask.service.CrawlQueue;
import ru.tsu.tpm.vacancyparser.finaltask.service.HtmlVacancyParser;
import ru.tsu.tpm.vacancyparser.finaltask.service.LocalVacancySource;
import ru.tsu.tpm.vacancyparser.finaltask.service.VacancyCrawlService;
import ru.tsu.tpm.vacancyparser.finaltask.service.VacancyQueryService;
import ru.tsu.tpm.vacancyparser.finaltask.service.VacancyStorageService;
import ru.tsu.tpm.vacancyparser.finaltask.web.dto.AnswerResponse;
import ru.tsu.tpm.vacancyparser.finaltask.web.dto.PostsRequest;
import ru.tsu.tpm.vacancyparser.finaltask.web.dto.PostsResponse;
import ru.tsu.tpm.vacancyparser.finaltask.web.dto.SourceInfo;
import ru.tsu.tpm.vacancyparser.finaltask.web.dto.StatsResponse;
import ru.tsu.tpm.vacancyparser.finaltask.web.dto.VacancyDto;
import ru.tsu.tpm.vacancyparser.finaltask.web.dto.VacancyInput;

/**
 * REST-контроллер Итогового ДЗ (префикс {@code /final}).
 *
 * <p>Эндпоинты:
 * <ul>
 *   <li>{@code POST /final/posts} — принять адреса (и/или HTML, и/или готовые вакансии) и запустить обход;</li>
 *   <li>{@code GET /final/answer} — результаты из H2 с пагинацией, фильтрами и сортировкой;</li>
 *   <li>{@code GET /final/stats} — состояние очереди, пула, счётчики и отчёт последнего обхода;</li>
 *   <li>{@code GET /final/source} — адреса демонстрационного источника;</li>
 *   <li>{@code POST /final/crawl} — принудительный обход очереди;</li>
 *   <li>{@code POST /final/reset} — очистить H2 и очередь перед повторной проверкой.</li>
 * </ul>
 *
 * <p>Контроллер не содержит логики обхода: он только принимает запрос, вызывает сервисы и упаковывает
 * ответ. Поэтому один и тот же сценарий доступен и через HTTP, и из тестов сервисов.
 */
@RestController
@RequestMapping("/final")
public class FinalTaskController {

    private final VacancyCrawlService crawlService;
    private final VacancyQueryService queryService;
    private final VacancyStorageService storage;
    private final CrawlQueue queue;
    private final HtmlVacancyParser parser;
    private final ExecutorService crawlExecutor;
    private final ObjectProvider<LocalVacancySource> sourceProvider;
    private final FinalTaskProperties properties;

    public FinalTaskController(
            VacancyCrawlService crawlService,
            VacancyQueryService queryService,
            VacancyStorageService storage,
            CrawlQueue queue,
            HtmlVacancyParser parser,
            @Qualifier("crawlExecutor") ExecutorService crawlExecutor,
            ObjectProvider<LocalVacancySource> sourceProvider,
            FinalTaskProperties properties) {
        this.crawlService = crawlService;
        this.queryService = queryService;
        this.storage = storage;
        this.queue = queue;
        this.parser = parser;
        this.crawlExecutor = crawlExecutor;
        this.sourceProvider = sourceProvider;
        this.properties = properties;
    }

    /** Краткая карта эндпоинтов — чтобы проверяющему не искать README. */
    @GetMapping
    public Map<String, String> endpoints() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("POST /final/posts", "принять {\"urls\":[...]} и запустить обход; либо {\"url\":...,\"html\":...} для офлайн-разбора");
        map.put("GET /final/answer", "результаты из H2: page, size, sort, direction, city, q, minSalary");
        map.put("GET /final/stats", "состояние очереди и пула, счётчики, отчёт последнего обхода");
        map.put("GET /final/source", "адреса локального демонстрационного источника");
        map.put("POST /final/crawl", "принудительный обход очереди (limit — сколько адресов)");
        map.put("POST /final/reset", "очистить H2 и очередь");
        return map;
    }

    /**
     * Принять адреса и/или данные без сети.
     *
     * <p>Если в теле есть {@code urls}, они ставятся в очередь, и по умолчанию сразу выполняется обход
     * (в ответе — отчёт с именами потоков и временем). {@code "crawl": false} оставляет адреса в очереди
     * для расписания.
     */
    @PostMapping("/posts")
    public PostsResponse posts(@RequestBody(required = false) PostsRequest request) {
        PostsRequest req = request == null
                ? new PostsRequest(null, null, null, null, null)
                : request;

        List<String> urls = req.urls() == null ? List.of() : req.urls();
        int accepted = queue.offerAll(urls);
        int duplicates = urls.size() - accepted;
        int savedFromBody = 0;

        if (req.vacancies() != null && !req.vacancies().isEmpty()) {
            List<ParsedVacancy> parsed = req.vacancies().stream()
                    .map(VacancyInput::toParsed)
                    .toList();
            savedFromBody += storage.upsertAll(parsed).total();
        }

        if (req.html() != null && !req.html().isBlank()) {
            String sourceUrl = req.url() == null || req.url().isBlank()
                    ? "offline://manual/" + System.nanoTime()
                    : req.url().trim();
            ParsedVacancy parsed = parser.parse(sourceUrl, req.html());
            savedFromBody += storage.upsertAll(List.of(parsed)).total();
        }

        CrawlReport crawl = null;
        if (accepted > 0 && !Boolean.FALSE.equals(req.crawl())) {
            crawl = crawlService.crawlQueue("POST /final/posts", Math.max(accepted, queue.size()));
        }

        String message = buildMessage(accepted, duplicates, savedFromBody, crawl);
        return new PostsResponse(accepted, duplicates, savedFromBody, queue.size(), crawl, message);
    }

    /** Результаты из H2 с фильтрацией, сортировкой и пагинацией. */
    @GetMapping("/answer")
    public AnswerResponse answer(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "publishedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String city,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) Integer minSalary) {
        try {
            VacancyQueryService.AnswerPage result =
                    queryService.answer(page, size, sort, direction, city, query, minSalary);
            List<VacancyDto> items = result.items().stream().map(VacancyDto::from).toList();
            return new AnswerResponse(
                    items,
                    result.total(),
                    result.page(),
                    result.size(),
                    result.totalPages(),
                    result.sort(),
                    result.direction(),
                    result.city(),
                    result.query(),
                    result.minSalary());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** Состояние приложения: данные, очередь, пул, счётчики, последний обход. */
    @GetMapping("/stats")
    public StatsResponse stats() {
        int poolSize = -1;
        int activeThreads = -1;
        int poolQueueSize = -1;
        if (crawlExecutor instanceof ThreadPoolExecutor executor) {
            poolSize = executor.getPoolSize();
            activeThreads = executor.getActiveCount();
            poolQueueSize = executor.getQueue().size();
        }
        return new StatsResponse(
                storage.count(),
                queue.size(),
                crawlService.crawlRuns(),
                crawlService.urlsSubmitted(),
                crawlService.urlsSucceeded(),
                crawlService.urlsFailed(),
                crawlService.savedTotal(),
                crawlService.updatedTotal(),
                poolSize,
                activeThreads,
                poolQueueSize,
                crawlService.knownWorkerThreads(),
                crawlService.perThreadProcessed(),
                crawlService.lastReport(),
                sourceInfo());
    }

    /** Адреса демонстрационного источника страниц. */
    @GetMapping("/source")
    public SourceInfo source() {
        return sourceInfo();
    }

    /** Принудительный обход очереди; при пустой очереди — обход демонстрационного источника. */
    @PostMapping("/crawl")
    public CrawlReport crawl(@RequestParam(required = false) Integer limit) {
        int max = limit == null ? properties.getSchedule().getBatchSize() : Math.max(1, limit);
        CrawlReport report = crawlService.crawlQueue("POST /final/crawl", max);
        if (report.requested() == 0 && !report.skipped()) {
            LocalVacancySource source = sourceProvider.getIfAvailable();
            if (source != null && source.baseUrl() != null) {
                report = crawlService.crawl(source.urls(), "POST /final/crawl (источник)");
            }
        }
        return report;
    }

    /** Очистить H2 и очередь — удобно для повторного прогона проверки. */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        queue.clear();
        long deleted = storage.deleteAll();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deletedVacancies", deleted);
        response.put("queueCleared", true);
        response.put("note", "расписание снова наполнит очередь демонстрационными адресами на следующем цикле");
        return response;
    }

    private SourceInfo sourceInfo() {
        LocalVacancySource source = sourceProvider.getIfAvailable();
        if (source == null || source.baseUrl() == null) {
            return new SourceInfo(null, 0, List.of(),
                    "демонстрационный источник отключён; передайте адреса в POST /final/posts вручную");
        }
        return new SourceInfo(
                source.baseUrl(),
                source.pageCount(),
                source.urls(),
                "локальный HTTP-сервер-заглушка: работает без интернета, страницы размечены как на hh.ru");
    }

    private static String buildMessage(int accepted, int duplicates, int savedFromBody, CrawlReport crawl) {
        StringBuilder message = new StringBuilder();
        message.append("принято в очередь: ").append(accepted);
        if (duplicates > 0) {
            message.append(", пропущено (дубликаты или не HTTP(S)): ").append(duplicates);
        }
        if (savedFromBody > 0) {
            message.append(", сохранено из тела запроса: ").append(savedFromBody);
        }
        if (crawl == null) {
            message.append("; обход не запускался");
        } else if (crawl.skipped()) {
            message.append("; обход пропущен: ").append(crawl.note());
        } else {
            message.append("; обход #").append(crawl.runId())
                    .append(": успешно ").append(crawl.succeeded())
                    .append(" из ").append(crawl.requested())
                    .append(", сохранено ").append(crawl.saved())
                    .append(", обновлено ").append(crawl.updated())
                    .append(", время ").append(String.format(java.util.Locale.ROOT, "%.1f", crawl.durationMillis()))
                    .append(" мс");
        }
        return message.toString();
    }
}
