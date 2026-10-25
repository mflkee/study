package ru.tsu.tpm.vacancyparser.finaltask.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.tsu.tpm.vacancyparser.finaltask.config.FinalTaskProperties;
import ru.tsu.tpm.vacancyparser.finaltask.model.CrawlReport;
import ru.tsu.tpm.vacancyparser.finaltask.model.ParsedVacancy;
import ru.tsu.tpm.vacancyparser.finaltask.model.UrlOutcome;

/**
 * Многопоточный обход страниц: загрузка в пуле, разбор, пакетное сохранение в H2.
 *
 * <p>Один обход — это набор задач {@link CompletableFuture#supplyAsync}, по одной на адрес. Задачи
 * выполняются собственным пулом {@code crawlExecutor} (см. {@code FinalTaskConfig}), поэтому в логе
 * видно разные имена потоков {@code final-crawl-N} и перекрытие по времени. Общий предел времени
 * страхует от зависшего адреса: ожидание результатов прекращается, а незавершённые задачи отменяются.
 *
 * <p>Потокобезопасные структуры:
 * <ul>
 *   <li>{@link ConcurrentHashMap} — разобранные страницы и итоги по адресам: задачи пишут параллельно,
 *       не мешая друг другу;</li>
 *   <li>{@link CopyOnWriteArrayList} — история обходов: читается редко и целиком, пишется по одному
 *       отчёту, поэтому копирование при записи здесь дешевле блокировок;</li>
 *   <li>{@link AtomicLong} — счётчики и счётчик потока-исполнителя по имени;</li>
 *   <li>{@link ReentrantLock} с {@code tryLock} — запрет двух одновременных обходов: расписание и
 *       HTTP-запрос не должны дублировать работу над одними адресами.</li>
 * </ul>
 *
 * <p>JPA-запись вынесена из параллельных задач в {@link VacancyStorageService}: сессия Hibernate не
 * потокобезопасна, и это осознанное разделение «параллельно грузим и парсим — последовательно пишем».
 */
@Service
public class VacancyCrawlService {

    private static final Logger log = LoggerFactory.getLogger(VacancyCrawlService.class);

    /** Сколько последних отчётов хранить в памяти. */
    private static final int HISTORY_LIMIT = 20;

    private final ExecutorService crawlExecutor;
    private final PageFetcher fetcher;
    private final HtmlVacancyParser parser;
    private final VacancyStorageService storage;
    private final CrawlQueue queue;
    private final FinalTaskProperties properties;

    private final ReentrantLock crawlLock = new ReentrantLock();
    private final AtomicLong runCounter = new AtomicLong();
    private final AtomicLong urlsSubmitted = new AtomicLong();
    private final AtomicLong urlsSucceeded = new AtomicLong();
    private final AtomicLong urlsFailed = new AtomicLong();
    private final AtomicLong savedTotal = new AtomicLong();
    private final AtomicLong updatedTotal = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> perThreadProcessed = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<CrawlReport> history = new CopyOnWriteArrayList<>();
    private volatile CrawlReport lastReport;

    public VacancyCrawlService(
            @Qualifier("crawlExecutor") ExecutorService crawlExecutor,
            PageFetcher fetcher,
            HtmlVacancyParser parser,
            VacancyStorageService storage,
            CrawlQueue queue,
            FinalTaskProperties properties) {
        this.crawlExecutor = crawlExecutor;
        this.fetcher = fetcher;
        this.parser = parser;
        this.storage = storage;
        this.queue = queue;
        this.properties = properties;
    }

    /** Забрать из очереди не более {@code max} адресов и обойти их. */
    public CrawlReport crawlQueue(String trigger, int max) {
        List<String> batch = queue.pollBatch(max);
        if (batch.isEmpty()) {
            return CrawlReport.empty(trigger);
        }
        return crawl(batch, trigger);
    }

    /**
     * Обойти заданный список адресов.
     *
     * <p>Если предыдущий обход ещё выполняется, возвращается отчёт {@code skipped}: лучше пропустить
     * цикл расписания, чем грузить одни и те же страницы дважды.
     */
    public CrawlReport crawl(List<String> urls, String trigger) {
        List<String> unique = urls == null
                ? List.of()
                : urls.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(text -> !text.isEmpty())
                        .distinct()
                        .toList();
        if (unique.isEmpty()) {
            return CrawlReport.empty(trigger);
        }
        if (!crawlLock.tryLock()) {
            log.warn("обход «{}» пропущен: предыдущий обход ещё выполняется", trigger);
            return CrawlReport.skipped(trigger, unique.size());
        }

        long runId = runCounter.incrementAndGet();
        long start = System.nanoTime();
        urlsSubmitted.addAndGet(unique.size());

        ConcurrentHashMap<String, ParsedVacancy> parsed = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, UrlOutcome> outcomes = new ConcurrentHashMap<>();
        try {
            List<CompletableFuture<UrlOutcome>> futures = unique.stream()
                    .map(url -> CompletableFuture
                            .supplyAsync(() -> {
                                UrlOutcome outcome = processOne(runId, url, parsed);
                                outcomes.put(url, outcome);
                                return outcome;
                            }, crawlExecutor)
                            .exceptionally(error -> {
                                UrlOutcome outcome = UrlOutcome.failure(
                                        url, "сбой задачи: " + describe(error),
                                        Thread.currentThread().getName(), 0L);
                                outcomes.put(url, outcome);
                                return outcome;
                            }))
                    .toList();

            boolean timedOut = false;
            try {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .get(properties.getCrawl().getTotalTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                timedOut = true;
                futures.forEach(future -> future.cancel(true));
                log.warn("обход #{}: превышен общий таймаут {} мс — незавершённые задачи отменены",
                        runId, properties.getCrawl().getTotalTimeoutMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                futures.forEach(future -> future.cancel(true));
                log.warn("обход #{}: ожидание результатов прервано", runId);
            } catch (ExecutionException e) {
                log.warn("обход #{}: сбой ожидания результатов: {}", runId, describe(e));
            }

            List<UrlOutcome> result = new ArrayList<>(unique.size());
            for (String url : unique) {
                UrlOutcome outcome = outcomes.get(url);
                if (outcome == null) {
                    outcome = UrlOutcome.failure(
                            url,
                            timedOut ? "результат не получен: общий таймаут" : "результат не получен",
                            "-",
                            0L);
                }
                result.add(outcome);
            }
            result.sort(Comparator.comparing(UrlOutcome::url));

            int succeeded = (int) result.stream().filter(UrlOutcome::ok).count();
            int failed = result.size() - succeeded;
            urlsSucceeded.addAndGet(succeeded);
            urlsFailed.addAndGet(failed);

            VacancyStorageService.StorageResult stored = storage.upsertAll(List.copyOf(parsed.values()));
            savedTotal.addAndGet(stored.saved());
            updatedTotal.addAndGet(stored.updated());

            List<String> workers = result.stream()
                    .map(UrlOutcome::thread)
                    .filter(thread -> thread != null && !thread.isBlank() && !"-".equals(thread))
                    .distinct()
                    .sorted()
                    .toList();

            CrawlReport report = new CrawlReport(
                    runId,
                    trigger,
                    unique.size(),
                    succeeded,
                    failed,
                    stored.saved(),
                    stored.updated(),
                    System.nanoTime() - start,
                    false,
                    timedOut ? "обход не уложился в общий таймаут" : null,
                    workers,
                    result);
            lastReport = report;
            remember(report);

            log.info(String.format(
                    Locale.ROOT,
                    "обход #%d (%s): адресов %d, успешно %d, сбоев %d, сохранено %d, обновлено %d, "
                            + "время %.1f мс, потоки: %s",
                    runId, trigger, unique.size(), succeeded, failed,
                    stored.saved(), stored.updated(), report.durationMillis(), workers));
            return report;
        } finally {
            crawlLock.unlock();
        }
    }

    /** Загрузить и разобрать одну страницу. Вызывается в потоке пула. */
    private UrlOutcome processOne(long runId, String url, ConcurrentHashMap<String, ParsedVacancy> parsed) {
        String thread = Thread.currentThread().getName();
        long start = System.nanoTime();
        perThreadProcessed.computeIfAbsent(thread, key -> new AtomicLong()).incrementAndGet();
        try {
            FetchResult fetched = fetcher.fetch(url);
            if (!fetched.ok()) {
                String error = fetched.error() != null
                        ? fetched.error()
                        : "пустой ответ HTTP " + fetched.status();
                long duration = System.nanoTime() - start;
                log.warn("обход #{}: {} — {}, поток {}, {} мс", runId, url, error, thread, millis(duration));
                return UrlOutcome.failure(url, error, thread, duration);
            }

            ParsedVacancy vacancy = parser.parse(url, fetched.body());
            parsed.put(url, vacancy);
            long duration = System.nanoTime() - start;
            log.info("обход #{}: {} — «{}», поток {}, {} мс",
                    runId, url, vacancy.title(), thread, millis(duration));
            return UrlOutcome.success(url, fetched.status(), thread, duration, vacancy.title());
        } catch (RuntimeException e) {
            long duration = System.nanoTime() - start;
            log.warn("обход #{}: {} — ошибка разбора, поток {}, {} мс: {}",
                    runId, url, thread, millis(duration), describe(e));
            return UrlOutcome.failure(url, "ошибка разбора: " + describe(e), thread, duration);
        }
    }

    private void remember(CrawlReport report) {
        history.add(report);
        while (history.size() > HISTORY_LIMIT) {
            history.remove(0);
        }
    }

    /** Отчёт последнего обхода ({@code null}, если обходов ещё не было). */
    public CrawlReport lastReport() {
        return lastReport;
    }

    /** История последних обходов (копия, безопасная для чтения из другого потока). */
    public List<CrawlReport> history() {
        return List.copyOf(history);
    }

    /** Сколько обходов было запущено. */
    public long crawlRuns() {
        return runCounter.get();
    }

    public long urlsSubmitted() {
        return urlsSubmitted.get();
    }

    public long urlsSucceeded() {
        return urlsSucceeded.get();
    }

    public long urlsFailed() {
        return urlsFailed.get();
    }

    public long savedTotal() {
        return savedTotal.get();
    }

    public long updatedTotal() {
        return updatedTotal.get();
    }

    /** Сколько адресов обработал каждый поток пула — видно распределение работы. */
    public ConcurrentHashMap<String, Long> perThreadProcessed() {
        ConcurrentHashMap<String, Long> snapshot = new ConcurrentHashMap<>();
        perThreadProcessed.forEach((thread, count) -> snapshot.put(thread, count.get()));
        return snapshot;
    }

    /** Имена потоков пула, которые уже обрабатывали страницы. */
    public List<String> knownWorkerThreads() {
        return perThreadProcessed.keySet().stream().sorted().toList();
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "неизвестная ошибка";
        }
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause
                && (cause instanceof java.util.concurrent.CompletionException
                        || cause instanceof ExecutionException)) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
