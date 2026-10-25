package ru.tsu.tpm.vacancyparser.hw10.completable.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.CompanyInfo;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.FetchFailure;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.FinalResult;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.VacancyDetails;
import ru.tsu.tpm.vacancyparser.hw10.completable.source.CompanyInfoSource;
import ru.tsu.tpm.vacancyparser.hw10.completable.source.VacancyDetailsSource;

/**
 * Асинхронная выборка данных по списку идентификаторов — ядро ДЗ 10.
 *
 * <p><b>Конвейер по каждому идентификатору.</b> Два независимых запроса (подробности вакансии и
 * справочник компаний) запускаются одновременно, объединяются через {@code thenCombine}, затем
 * объединённый объект проходит тяжёлое преобразование в {@code thenApplyAsync(..., executor)} и
 * попадает в приёмник результатов через {@code thenAccept}. Сборка всех конвейеров — {@code allOf}.
 *
 * <p><b>Изоляция сбоев.</b> Сбой справочника компаний перехватывает {@code exceptionally} и подставляет
 * заглушку: вакансия остаётся в результате. Сбой данных вакансии перехватывает {@code handle}: вакансия
 * исключается, но остальные продолжают обрабатываться. Поэтому метод возвращает частичный результат,
 * а не падает целиком.
 *
 * <p><b>Собственный исполнитель.</b> Все асинхронные этапы идут на пуле {@value #POOL_SIZE_HINT}
 * потоков с именами {@code hw10-async-N}. Потоки — <b>демоны</b>: даже при аварийном завершении
 * приложения JVM не будет держать процесс. Пул обязательно останавливается методом {@link #close()}:
 * бин создаётся с {@code destroyMethod = "close"}, поэтому остановка происходит при закрытии
 * Spring-контекста, а не «когда-нибудь».
 */
public final class AsyncVacancyService implements AutoCloseable {

    /** Префикс имён потоков пула. */
    public static final String THREAD_PREFIX = "hw10-async-";

    /** Подсказка о размере пула по умолчанию (используется в javadoc и настройках). */
    public static final int POOL_SIZE_HINT = 6;

    /** Сколько строк журнала хранится в памяти. */
    private static final int MAX_TRACE_LINES = 4_000;

    private final ExecutorService executor;
    private final VacancyDetailsSource detailsSource;
    private final CompanyInfoSource companySource;
    private final int poolSize;
    private final long transformDelayMillis;
    private final long shutdownTimeoutMillis;
    private final Consumer<FinalResult> resultConsumer;
    private final Queue<String> trace = new ConcurrentLinkedQueue<>();
    private final AtomicInteger traceSize = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * @param detailsSource        первый источник (данные вакансии)
     * @param companySource        второй источник (справочник компаний)
     * @param poolSize             размер собственного пула
     * @param transformDelayMillis моделируемая длительность тяжёлого преобразования
     * @param shutdownTimeoutMillis сколько ждать завершения пула при остановке
     * @param resultConsumer       приёмник результатов (этап {@code thenAccept}); может быть {@code null}
     */
    public AsyncVacancyService(
            VacancyDetailsSource detailsSource,
            CompanyInfoSource companySource,
            int poolSize,
            long transformDelayMillis,
            long shutdownTimeoutMillis,
            Consumer<FinalResult> resultConsumer) {
        if (detailsSource == null || companySource == null) {
            throw new IllegalArgumentException("нужны оба источника данных");
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("размер пула должен быть положительным, получено " + poolSize);
        }
        if (transformDelayMillis < 0) {
            throw new IllegalArgumentException("задержка преобразования не может быть отрицательной");
        }
        if (shutdownTimeoutMillis <= 0) {
            throw new IllegalArgumentException("таймаут остановки должен быть положительным");
        }
        this.detailsSource = detailsSource;
        this.companySource = companySource;
        this.poolSize = poolSize;
        this.transformDelayMillis = transformDelayMillis;
        this.shutdownTimeoutMillis = shutdownTimeoutMillis;
        this.resultConsumer = resultConsumer;
        this.executor = Executors.newFixedThreadPool(poolSize, demThreadFactory(THREAD_PREFIX));
    }

    /**
     * Получить данные по всем идентификаторам асинхронно — метод из текста задания.
     *
     * <p>Возвращает итоговые объекты в порядке входного списка; сбойные идентификаторы в результат не
     * попадают (их список доступен в {@link #runAsync(List)} и в журнале).
     */
    public List<FinalResult> fetchAllDataAsync(List<String> ids) {
        return runAsync(ids).results();
    }

    /** Асинхронный прогон с полным итогом: результаты, сбои, время, потоки. */
    public FetchOutcome runAsync(List<String> ids) {
        List<String> requested = validateIds(ids);
        long start = System.nanoTime();
        if (requested.isEmpty()) {
            return new FetchOutcome("async", 0, List.of(), List.of(), System.nanoTime() - start, Set.of(), 0);
        }

        Map<String, FinalResult> byId = new ConcurrentHashMap<>();
        List<FetchFailure> failures = Collections.synchronizedList(new ArrayList<>());
        Set<String> threadsUsed = ConcurrentHashMap.newKeySet();
        AtomicInteger sourceCalls = new AtomicInteger();
        List<CompletableFuture<Void>> pipelines = new ArrayList<>(requested.size());

        for (String id : requested) {
            CompletableFuture<VacancyDetails> detailsFuture = CompletableFuture
                    .supplyAsync(() -> fetchDetails(id, threadsUsed, sourceCalls), executor);

            CompletableFuture<CompanyInfo> companyFuture = CompletableFuture
                    .supplyAsync(() -> fetchCompany(id, threadsUsed, sourceCalls), executor)
                    .exceptionally(error -> {
                        trace(id, "справочник компаний недоступен, подставлена заглушка: " + describe(error));
                        return CompanyInfo.unknown(id);
                    });

            CompletableFuture<Void> pipeline = detailsFuture
                    .thenCombine(companyFuture, FinalResult::combine)
                    .thenApplyAsync(result -> {
                        noteThread(threadsUsed);
                        FinalResult transformed = VacancyScoring.transform(result, transformDelayMillis);
                        trace(id, "преобразование выполнено: оценка "
                                + String.format(Locale.ROOT, "%.2f", transformed.attractiveness()));
                        return transformed;
                    }, executor)
                    .handle((result, error) -> {
                        if (error != null) {
                            failures.add(new FetchFailure(id, "данные вакансии", describe(error)));
                            trace(id, "вакансия исключена из результата: " + describe(error));
                            return Optional.<FinalResult>empty();
                        }
                        return Optional.of(result);
                    })
                    .thenAccept(optional -> optional.ifPresent(result -> {
                        byId.put(id, result);
                        trace(id, "результат принят: " + result.title() + ", компания " + result.company().name());
                        if (resultConsumer != null) {
                            resultConsumer.accept(result);
                        }
                    }));

            pipelines.add(pipeline);
        }

        CompletableFuture.allOf(pipelines.toArray(CompletableFuture[]::new)).join();

        List<FinalResult> ordered = requested.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return new FetchOutcome(
                "async", requested.size(), ordered, List.copyOf(failures),
                System.nanoTime() - start, threadsUsed, sourceCalls.get());
    }

    /**
     * Синхронная версия того же сценария — база для сравнения.
     *
     * <p>Семантика совпадает с асинхронной: сбой справочника даёт заглушку, сбой данных вакансии
     * исключает вакансию. Отличается только способ выполнения — последовательный, в вызывающем потоке.
     */
    public FetchOutcome runSync(List<String> ids) {
        List<String> requested = validateIds(ids);
        long start = System.nanoTime();
        List<FinalResult> results = new ArrayList<>(requested.size());
        List<FetchFailure> failures = new ArrayList<>();
        Set<String> threadsUsed = Set.of(Thread.currentThread().getName());
        int sourceCalls = 0;

        for (String id : requested) {
            VacancyDetails details;
            try {
                sourceCalls++;
                trace(id, "синхронный запрос деталей");
                details = detailsSource.fetchDetails(id);
            } catch (Exception e) {
                failures.add(new FetchFailure(id, "данные вакансии", describe(e)));
                trace(id, "вакансия исключена из результата: " + describe(e));
                continue;
            }

            CompanyInfo company;
            try {
                sourceCalls++;
                trace(id, "синхронный запрос компании");
                company = companySource.fetchCompany(id);
            } catch (Exception e) {
                trace(id, "справочник компаний недоступен, подставлена заглушка: " + describe(e));
                company = CompanyInfo.unknown(id);
            }

            FinalResult combined = FinalResult.combine(details, company);
            FinalResult transformed = VacancyScoring.transform(combined, transformDelayMillis);
            results.add(transformed);
            trace(id, "синхронный результат готов: оценка "
                    + String.format(Locale.ROOT, "%.2f", transformed.attractiveness()));
        }

        return new FetchOutcome(
                "sync", requested.size(), results, failures,
                System.nanoTime() - start, threadsUsed, sourceCalls);
    }

    /** Сколько потоков в пуле. */
    public int poolSize() {
        return poolSize;
    }

    /** Накопленный журнал с именами потоков и идентификаторами запросов. */
    public List<String> traceLines() {
        return List.copyOf(trace);
    }

    /** Очистить журнал (перед новым этапом демонстрации). */
    public void clearTrace() {
        trace.clear();
        traceSize.set(0);
    }

    /** Остановлен ли пул. */
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /** Завершились ли все потоки пула. */
    public boolean isTerminated() {
        return executor.isTerminated();
    }

    /** Остановить пул: инициация, ожидание, при необходимости принудительное завершение. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeoutMillis, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private VacancyDetails fetchDetails(String id, Set<String> threadsUsed, AtomicInteger sourceCalls) {
        noteThread(threadsUsed);
        trace(id, "запрос данных вакансии отправлен");
        try {
            sourceCalls.incrementAndGet();
            VacancyDetails details = detailsSource.fetchDetails(id);
            trace(id, "данные вакансии получены: " + details.title());
            return details;
        } catch (Exception e) {
            trace(id, "сбой источника вакансий: " + describe(e));
            throw new CompletionException(e);
        }
    }

    private CompanyInfo fetchCompany(String id, Set<String> threadsUsed, AtomicInteger sourceCalls) {
        noteThread(threadsUsed);
        trace(id, "запрос компании отправлен");
        try {
            sourceCalls.incrementAndGet();
            CompanyInfo company = companySource.fetchCompany(id);
            trace(id, "данные компании получены: " + company.name());
            return company;
        } catch (Exception e) {
            trace(id, "сбой справочника компаний: " + describe(e));
            throw new CompletionException(e);
        }
    }

    private void noteThread(Set<String> threadsUsed) {
        threadsUsed.add(Thread.currentThread().getName());
    }

    private void trace(String id, String message) {
        String line = "[" + Thread.currentThread().getName() + "] " + id + ": " + message;
        trace.add(line);
        if (traceSize.incrementAndGet() > MAX_TRACE_LINES) {
            if (trace.poll() != null) {
                traceSize.decrementAndGet();
            }
        }
    }

    private static List<String> validateIds(List<String> ids) {
        if (ids == null) {
            throw new IllegalArgumentException("список идентификаторов не задан");
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("идентификатор не может быть пустым");
            }
        }
        return List.copyOf(ids);
    }

    private static String describe(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause == null) {
            return "неизвестная ошибка";
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    /** Фабрика именованных демон-потоков: имя видно в журнале и в дампе потоков. */
    public static ThreadFactory demThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
