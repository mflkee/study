package ru.tsu.tpm.vacancyparser.hw09.callable.aggregator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import ru.tsu.tpm.vacancyparser.hw09.callable.WorkerThreads;

/**
 * Сервис периодической агрегации данных.
 *
 * <p>Каждые N секунд сервис выполняет **цикл**: получает список идентификаторов сущностей из источника,
 * для каждой сущности асинхронно запрашивает данные у провайдера (который может выбросить исключение) и
 * обрабатывает успешные результаты. Сбой по одной сущности изолирован: остальные всё равно получены и
 * обработаны, а сервис продолжает работу. Отдельно переживается недоступность самого источника списка —
 * цикл будет пустым, но следующий выполнится.
 *
 * <p>Расписание ведёт {@link ScheduledExecutorService}, запросы по сущностям выполняет пул
 * ({@link ExecutorService}); по завершении оба обязательно останавливаются. Детерминированный
 * {@link #runCycle()} доступен отдельно — на нём проверяется логика без ожидания расписания.
 */
public final class PeriodicDataAggregator implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final ExecutorService workers;
    private final EntitySource source;
    private final DataProvider provider;
    private final Consumer<CycleReport> cycleHandler;
    private final long periodMillis;
    private final AtomicLong cycleCounter = new AtomicLong();
    private final List<CycleReport> reports = Collections.synchronizedList(new ArrayList<>());
    private volatile CycleReport lastReport;

    /**
     * @param source        источник списка идентификаторов (может выбрасывать исключение)
     * @param provider      источник данных по сущности (может выбрасывать исключение)
     * @param cycleHandler  обработчик успешных результатов цикла (может быть {@code null})
     * @param periodMillis  период циклов
     * @param workerThreads число потоков для асинхронных запросов внутри цикла
     * @param namePrefix    префикс имён потоков
     */
    public PeriodicDataAggregator(
            EntitySource source,
            DataProvider provider,
            Consumer<CycleReport> cycleHandler,
            long periodMillis,
            int workerThreads,
            String namePrefix) {
        if (periodMillis <= 0) {
            throw new IllegalArgumentException("период агрегации должен быть положительным");
        }
        if (workerThreads < 1) {
            throw new IllegalArgumentException("нужен хотя бы один рабочий поток агрегации");
        }
        this.source = source;
        this.provider = provider;
        this.cycleHandler = cycleHandler;
        this.periodMillis = periodMillis;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(WorkerThreads.factory(namePrefix + "scheduler-"));
        this.workers = Executors.newFixedThreadPool(workerThreads, WorkerThreads.factory(namePrefix + "worker-"));
    }

    /** Выполнить один цикл агрегации синхронно (для демонстрации и тестов). */
    public CycleReport runCycle() {
        long cycle = cycleCounter.incrementAndGet();
        long start = System.nanoTime();

        List<String> ids;
        try {
            List<String> fromSource = source.entities();
            ids = fromSource == null ? List.of() : fromSource;
        } catch (Exception e) {
            return record(new CycleReport(
                    cycle, List.of(), List.of(), List.of(), describe(e), System.nanoTime() - start));
        }

        List<Callable<EntityData>> tasks = new ArrayList<>(ids.size());
        for (String id : ids) {
            tasks.add(() -> provider.fetch(id));
        }

        List<EntityData> successes = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        List<Future<EntityData>> futures;
        try {
            futures = workers.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            for (String id : ids) {
                failures.add(new Failure(id, "цикл прерван"));
            }
            return record(new CycleReport(
                    cycle, ids, successes, failures, "цикл прерван", System.nanoTime() - start));
        }

        for (int i = 0; i < ids.size(); i++) {
            try {
                successes.add(futures.get(i).get());
            } catch (ExecutionException e) {
                failures.add(new Failure(ids.get(i), describe(e.getCause())));
            } catch (CancellationException e) {
                failures.add(new Failure(ids.get(i), "запрос отменён"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.add(new Failure(ids.get(i), "запрос прерван"));
            }
        }

        return record(new CycleReport(cycle, ids, successes, failures, null, System.nanoTime() - start));
    }

    /** Запустить периодическое выполнение циклов. */
    public void start() {
        scheduler.scheduleAtFixedRate(this::runCycleQuietly, 0L, periodMillis, TimeUnit.MILLISECONDS);
    }

    private void runCycleQuietly() {
        try {
            runCycle();
        } catch (RuntimeException e) {
            // Внутри runCycle сбои уже изолированы; сюда попадать не должны, но расписание не роняем.
        }
    }

    /** Сколько циклов было запущено. */
    public long cycles() {
        return cycleCounter.get();
    }

    /** Отчёты по всем выполненным циклам. */
    public List<CycleReport> reports() {
        synchronized (reports) {
            return List.copyOf(reports);
        }
    }

    /** Отчёт по последнему циклу ({@code null}, если циклов ещё не было). */
    public CycleReport lastReport() {
        return lastReport;
    }

    /** Дождаться не менее {@code count} циклов. */
    public boolean awaitCycles(long count, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (cycleCounter.get() < count) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(5L);
        }
        return true;
    }

    /** Остановить сервис: сначала расписание, затем рабочие потоки, с ожиданием и принудительным завершением. */
    public void stop(long timeoutMillis) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        workers.shutdown();
        try {
            if (!workers.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
    }

    /** Завершены ли оба исполнителя. */
    public boolean isTerminated() {
        return scheduler.isTerminated() && workers.isTerminated();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        workers.shutdownNow();
    }

    private CycleReport record(CycleReport report) {
        lastReport = report;
        reports.add(report);
        if (cycleHandler != null) {
            cycleHandler.accept(report);
        }
        return report;
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "неизвестная ошибка";
        }
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
