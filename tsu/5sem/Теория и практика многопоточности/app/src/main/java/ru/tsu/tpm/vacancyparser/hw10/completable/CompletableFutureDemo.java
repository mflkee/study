package ru.tsu.tpm.vacancyparser.hw10.completable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.common.ProcessInfo;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.TimeBoxedExecution;
import ru.tsu.tpm.vacancyparser.hw10.completable.config.Hw10Properties;
import ru.tsu.tpm.vacancyparser.hw10.completable.report.AsyncFetchReport;
import ru.tsu.tpm.vacancyparser.hw10.completable.server.VacancyStubServer;
import ru.tsu.tpm.vacancyparser.hw10.completable.service.AsyncVacancyService;
import ru.tsu.tpm.vacancyparser.hw10.completable.service.FetchOutcome;

/**
 * Демонстрация ДЗ 10 «CompletableFuture».
 *
 * <p>Этапы повторяют текст задания: асинхронная выборка по списку идентификаторов собственным пулом,
 * преобразование и объединение двух независимых результатов, сборка итогового списка, изоляция сбоя
 * одного источника, сравнение с синхронной версией и корректная остановка исполнителя.
 *
 * <p>Запускается только при {@code --app.hw=hw10}. Источник данных — локальная HTTP-заглушка на
 * свободном порту, поэтому сеть не нужна, а задержка ответа делает выигрыш от асинхронности
 * измеримым. Завершается штатно с кодом {@code 0}; страховочный лимит времени снимает дамп потоков,
 * если что-то зависло.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw10")
public class CompletableFutureDemo implements ApplicationRunner {

    /** Предел времени на всю демонстрацию. */
    static final long TIME_LIMIT_MILLIS = 120_000L;

    /** Код выхода, если демонстрация не уложилась в лимит. */
    static final int TIME_LIMIT_EXIT_CODE = 3;

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;
    private final Hw10Properties properties;
    private final AsyncVacancyService service;
    private final VacancyStubServer stub;

    public CompletableFutureDemo(
            ConsoleOutput console,
            ConfigurableApplicationContext context,
            Hw10Properties properties,
            AsyncVacancyService service,
            VacancyStubServer stub) {
        this.console = console;
        this.context = context;
        this.properties = properties;
        this.service = service;
        this.stub = stub;
    }

    @Override
    public void run(ApplicationArguments args) {
        console.section("ДЗ 10. CompletableFuture: асинхронная выборка по списку идентификаторов");
        console.raw("");
        console.raw(ProcessInfo.pidLine());
        printSettings();

        TimeBoxedExecution.Outcome outcome;
        try {
            outcome = TimeBoxedExecution.run(TIME_LIMIT_MILLIS, this::runStages);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            console.warn("демонстрация прервана: " + e.getMessage());
            context.close();
            System.exit(TIME_LIMIT_EXIT_CODE);
            return;
        }

        console.raw("");
        if (outcome.completed() && outcome.failure() == null) {
            console.info("Демонстрация завершена: частичный результат получен, сбой одного источника изолирован, "
                    + "исполнитель остановлен");
        } else if (outcome.failure() != null) {
            console.warn("демонстрация завершилась ошибкой: " + outcome.failure());
        } else {
            console.warn("демонстрация не уложилась в " + TIME_LIMIT_MILLIS + " мс — снимаю дамп потоков");
            console.raw(ThreadDump.capture());
        }

        // Закрытие контекста вызывает close() у бинов: пул, HTTP-клиент и заглушка останавливаются.
        context.close();
        console.raw("");
        console.raw("после остановки контекста: пул остановлен=" + marker(service.isShutdown())
                + ", потоки завершены=" + marker(service.isTerminated()));
        console.raw("живых потоков ДЗ 10 после завершения: "
                + ThreadDump.liveThreadsWithPrefixes(AsyncVacancyService.THREAD_PREFIX, "hw10-stub-worker"));

        if (!(outcome.completed() && outcome.failure() == null)) {
            System.exit(TIME_LIMIT_EXIT_CODE);
        }
    }

    private void printSettings() {
        console.raw("Настройки ДЗ 10:");
        console.item("идентификаторов в списке: " + properties.getIds().size());
        console.item("размер собственного пула: " + properties.getPoolSize());
        console.item("задержка ответа источника: " + properties.getLatencyMillis() + " мс на запрос");
        console.item("моделируемое тяжёлое преобразование: " + properties.getTransformDelayMillis() + " мс на объект");
        console.item("таймаут запроса: " + properties.getRequestTimeoutMillis() + " мс, остановки пула: "
                + properties.getShutdownTimeoutMillis() + " мс");
        console.item("сбой источника вакансий на «" + properties.getDetailsFailId()
                + "», сбой справочника компаний на «" + properties.getCompanyFailId() + "»");
        console.item("источник данных: локальная HTTP-заглушка, порт " + stub.port());
        List<String> missing = VacancyStubServer.missingFailIds(
                properties.getIds(), properties.getDetailsFailId(), properties.getCompanyFailId());
        if (!missing.isEmpty()) {
            console.warn("идентификаторы сбоев отсутствуют в списке и не будут запрошены: " + missing);
        }
    }

    private void runStages() {
        FetchOutcome async = stageAsync();
        stageSync(async);
        stageFailureIsolation();
    }

    /** Этап 1. Асинхронная выборка: thenCombine, thenApplyAsync, thenAccept. */
    private FetchOutcome stageAsync() {
        console.raw("");
        console.raw("Этап 1. Асинхронная выборка через CompletableFuture (два источника объединяются в один объект)");
        service.clearTrace();
        FetchOutcome outcome = service.runAsync(properties.getIds());
        console.raw("    " + outcome.summaryLine());
        console.raw("    потоки этапов: " + outcome.threadNames());
        console.raw("    результаты (в порядке входного списка):");
        outcome.results().forEach(result -> console.raw("      " + result.reportLine()));
        outcome.failures().forEach(failure -> console.raw("      сбой " + failure.reportLine()));
        console.raw("    журнал «поток + ID запроса» (первые " + properties.getTraceLines() + " строк):");
        AsyncFetchReport.traceHead(service.traceLines(), properties.getTraceLines())
                .forEach(line -> console.raw("      " + line));
        return outcome;
    }

    /** Этап 2. Синхронная версия того же сценария и сравнение времени. */
    private void stageSync(FetchOutcome async) {
        console.raw("");
        console.raw("Этап 2. Синхронная версия и сравнение времени");
        FetchOutcome sync = service.runSync(properties.getIds());
        console.raw("    " + sync.summaryLine());
        console.raw("    " + AsyncFetchReport.comparisonLine(async, sync));
        console.raw(String.format(
                Locale.ROOT,
                "    на один идентификатор: синхронно %.2f мс, асинхронно %.2f мс",
                sync.durationMillis() / Math.max(1, sync.requested()),
                async.durationMillis() / Math.max(1, async.requested())));
        console.raw("    наборы результатов совпадают: "
                + marker(AsyncFetchReport.sameResults(async, sync))
                + " (асинхронно " + async.results().size() + ", синхронно " + sync.results().size() + ")");
        console.raw("    обращений к источникам: асинхронно " + async.sourceCalls()
                + ", синхронно " + sync.sourceCalls());
    }

    /** Этап 3. Сбой одного источника: handle отбрасывает вакансию, exceptionally подставляет заглушку. */
    private void stageFailureIsolation() {
        console.raw("");
        console.raw("Этап 3. Сбой одного источника не ломает операцию (handle и exceptionally)");
        List<String> subset = isolationSubset();
        if (subset.isEmpty()) {
            console.warn("нечего проверять: в настройках не заданы идентификаторы со сбоями");
            return;
        }
        service.clearTrace();
        FetchOutcome partial = service.runAsync(subset);
        console.raw("    запрошено " + subset.size() + ": " + subset);
        console.raw("    " + partial.summaryLine());
        AsyncFetchReport.isolationLines(
                        partial, subset, properties.getDetailsFailId(), properties.getCompanyFailId())
                .forEach(line -> console.raw("      " + line));
        console.raw("    журнал этапа:");
        service.traceLines().forEach(line -> console.raw("      " + line));
        console.raw("    вывод: сбой одной задачи не отменил остальные — метод вернул частичный результат");
    }

    private List<String> isolationSubset() {
        LinkedHashSet<String> subset = new LinkedHashSet<>();
        if (properties.getDetailsFailId() != null && !properties.getDetailsFailId().isBlank()) {
            subset.add(properties.getDetailsFailId().trim());
        }
        if (properties.getCompanyFailId() != null && !properties.getCompanyFailId().isBlank()) {
            subset.add(properties.getCompanyFailId().trim());
        }
        properties.getIds().stream()
                .filter(id -> !subset.contains(id))
                .findFirst()
                .ifPresent(subset::add);
        return new ArrayList<>(subset);
    }

    private static String marker(boolean value) {
        return value ? "да" : "нет";
    }
}
