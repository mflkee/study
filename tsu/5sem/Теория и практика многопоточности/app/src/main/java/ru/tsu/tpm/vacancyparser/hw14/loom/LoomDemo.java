package ru.tsu.tpm.vacancyparser.hw14.loom;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.common.ProcessInfo;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.TimeBoxedExecution;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.LoomBenchmarks;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.MemoryProbe;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.SchedulerInfo;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.SleepyTask;
import ru.tsu.tpm.vacancyparser.hw14.loom.config.Hw14Properties;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.BenchmarkResult;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.LoadRun;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.PageLoad;
import ru.tsu.tpm.vacancyparser.hw14.loom.pinning.PinningExperiment;
import ru.tsu.tpm.vacancyparser.hw14.loom.pinning.PinningRun;
import ru.tsu.tpm.vacancyparser.hw14.loom.report.LoomReport;
import ru.tsu.tpm.vacancyparser.hw14.loom.server.VacancyPageServer;
import ru.tsu.tpm.vacancyparser.hw14.loom.structured.ScopeRun;
import ru.tsu.tpm.vacancyparser.hw14.loom.structured.StructuredExperiments;
import ru.tsu.tpm.vacancyparser.hw14.loom.swarm.SwarmRun;
import ru.tsu.tpm.vacancyparser.hw14.loom.swarm.ThreadSwarm;
import ru.tsu.tpm.vacancyparser.hw14.loom.web.PageVacancyClient;

/**
 * Демонстрация ДЗ 14 «Введение в проект Loom (Virtual Threads)».
 *
 * <p>Семь этапов, каждый отвечает на свой вопрос:
 *
 * <ol>
 *   <li><b>Виртуальные потоки базово.</b> Двести страниц вакансий с локального сайта грузятся
 *       синхронным HTTP, но каждая — в своём виртуальном потоке. В журнале видно и имя потока, и
 *       carrier, на котором он смонтирован.</li>
 *   <li><b>Сравнение с платформенным пулом.</b> Тот же объём работы через {@code newFixedThreadPool(16)}
 *       и через виртуальные потоки: время, пропускная способность, пик одновременных задач.</li>
 *   <li><b>Pinning.</b> Одинаковые блокирующие операции под {@code synchronized} и под
 *       {@code ReentrantLock}: в первом случае поток пришпилен к carrier, во втором — снимается.</li>
 *   <li><b>Масштабирование.</b> Десять тысяч задач с ожиданием: виртуальные потоки проходят их
 *       почти одновременно, платформенный пул на 200 потоков — порциями.</li>
 *   <li><b>«Поток на задачу».</b> Те же 10 000 задач, каждая печатает имя своего потока и ждёт 1 мс,
 *       но запущенные напрямую — {@code Thread.ofVirtual().start()} против {@code new Thread().start()}.
 *       Этап показывает цену самого потока ОС: запуск, память, число потоков.</li>
 *   <li><b>StructuredTaskScope.</b> Группа виртуальных потоков под управлением скоупа: успех,
 *       падение одной подзадачи с отменой остальных, отмена по таймауту и «первый успешный ответ».</li>
 *   <li><b>Итог.</b> Сводная таблица по всем этапам.</li>
 * </ol>
 *
 * <p>Всё офлайн: источник — локальный сайт на свободном порту. Запускается только при
 * {@code --app.hw=hw14}, по завершении печатает итоговую таблицу и закрывает контекст.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw14")
public class LoomDemo implements ApplicationRunner {

    /** Предел времени на всю демонстрацию: реально она укладывается в несколько секунд. */
    static final long TIME_LIMIT_MILLIS = 120_000L;

    /** Код выхода, если демонстрация не уложилась в лимит. */
    static final int TIME_LIMIT_EXIT_CODE = 3;

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;
    private final Hw14Properties properties;

    public LoomDemo(
            ConsoleOutput console, ConfigurableApplicationContext context, Hw14Properties properties) {
        this.console = console;
        this.context = context;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        console.section("ДЗ 14. Проект Loom: виртуальные потоки на блокирующем IO");
        console.raw("");
        console.raw(ProcessInfo.pidLine());
        console.raw("Настройки ДЗ 14:");
        console.item("страниц: " + properties.getPages() + ", задержка страницы: "
                + properties.getPageDelayMillis() + " мс");
        console.item("платформенный пул для сравнения: " + properties.getFixedPoolSize() + " потоков");
        console.item("масштабирование: " + properties.getScalingTasks() + " задач по "
                + properties.getScalingDelayMillis() + " мс, пул " + properties.getPlatformPoolSize());
        console.item("pinning: " + properties.getPinningTasks() + " задач по "
                + properties.getPinningBlockMillis() + " мс");
        console.item("поток на задачу: " + properties.getSwarmTasks() + " задач по "
                + properties.getSwarmSleepMillis() + " мс, предел платформенных потоков "
                + (properties.getSwarmMaxPlatformThreads() <= 0
                        ? "без ограничения"
                        : properties.getSwarmMaxPlatformThreads()));
        console.item("StructuredTaskScope: " + properties.getStructuredTasks() + " подзадач по "
                + properties.getStructuredDelayMillis() + " мс, падение через "
                + properties.getStructuredFailAfterMillis() + " мс, срок " + properties.getStructuredTimeoutMillis() + " мс");
        console.raw("Планировщик виртуальных потоков: " + SchedulerInfo.describe());

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
            console.info("Демонстрация завершена: все этапы пройдены, потерь задач нет, "
                    + "исполнители остановлены");
        } else if (outcome.failure() != null) {
            console.warn("демонстрация завершилась ошибкой: " + outcome.failure());
        } else {
            console.warn("демонстрация не уложилась в " + TIME_LIMIT_MILLIS + " мс — снимаю дамп потоков");
            console.raw(ThreadDump.capture());
        }

        context.close();
        console.raw("");
        console.raw("живых платформенных потоков ДЗ 14 после завершения: "
                + ThreadDump.liveThreadsWithPrefixes(LoomBenchmarks.PLATFORM_PREFIX));
        console.raw("виртуальные потоки в дамп не попадают: планировщик держит их вне Thread.getAllStackTraces(), "
                + "поэтому отсутствие потерь проверяется счётчиками задач, а не списком потоков");
        if (!(outcome.completed() && outcome.failure() == null)) {
            System.exit(TIME_LIMIT_EXIT_CODE);
        }
    }

    private void runStages() {
        try (VacancyPageServer site = VacancyPageServer.start(properties.getPageDelayMillis());
                PageVacancyClient client = new PageVacancyClient(
                        properties.getConnectTimeoutMillis(), properties.getRequestTimeoutMillis())) {

            List<String> urls = site.urls(properties.getPages());
            console.raw("");
            console.raw("Этап 1. Виртуальные потоки: загрузка страниц вакансий");
            console.raw("    источник: локальный сайт, " + site.baseUrl() + ", страниц " + urls.size());
            console.raw("    исполнитель: Thread.ofVirtual().name(\"" + LoomBenchmarks.VIRTUAL_PREFIX
                    + "\", 0) + newThreadPerTaskExecutor (по виртуальному потоку на страницу)");
            console.raw("    пример адреса: " + urls.get(0));

            int warmupPages = Math.min(32, urls.size());
            if (warmupPages > 0) {
                load(urls.subList(0, warmupPages), client, true);
                console.raw("    прогрев: " + warmupPages + " страниц (в замеры не входят — "
                        + "первый запрос подгружает классы HTTP-клиента)");
            }
            LoadRun basics = load(urls, client, true);
            printLoad(basics, "    ");
            console.raw("    проверка: исполнений на виртуальных потоках " + basics.metrics().virtualExecutions()
                    + ", на платформенных " + basics.metrics().platformExecutions()
                    + " → все запросы сделаны виртуальными потоками: " + marker(basics.metrics().virtualOnly()));
            console.raw("    паспорта потоков (первые " + Math.min(properties.getSampleTitles(), 3) + "):");
            LoomReport.probeLines(basics, Math.min(properties.getSampleTitles(), 3))
                    .forEach(line -> console.raw("      " + line));

            console.raw("");
            console.raw("Этап 2. Сравнение с платформенным пулом: тот же объём работы");
            console.raw("    сначала newFixedThreadPool(" + properties.getFixedPoolSize()
                    + "), затем Executors.newVirtualThreadPerTaskExecutor()");
            LoadRun platformLoad = load(urls, client, false);
            LoadRun virtualLoad = load(urls, client, true);
            LoomReport.loadComparisonLines(platformLoad, virtualLoad)
                    .forEach(line -> console.raw("    " + line));
            console.raw("    целостность: платформенный пул — " + integrity(platformLoad)
                    + "; виртуальные потоки — " + integrity(virtualLoad));
            console.raw("    память (приблизительно, без System.gc()): пул "
                    + MemoryProbe.describeDelta(platformLoad.metrics().heapDeltaBytes())
                    + ", виртуальные " + MemoryProbe.describeDelta(virtualLoad.metrics().heapDeltaBytes()));

            console.raw("");
            console.raw("Этап 3. Pinning: synchronized против ReentrantLock");
            console.raw("    у каждой задачи свой монитор/замок, внутри секции — ожидание "
                    + properties.getPinningBlockMillis() + " мс");
            PinningRun pinned = PinningExperiment.runSynchronized(
                    properties.getPinningTasks(), properties.getPinningBlockMillis());
            PinningRun lock = PinningExperiment.runReentrantLock(
                    properties.getPinningTasks(), properties.getPinningBlockMillis());
            LoomReport.pinningLines(pinned, lock).forEach(line -> console.raw("    " + line));
            console.raw("    задачи завершены: synchronized " + pinned.completed() + " из " + pinned.tasks()
                    + ", ReentrantLock " + lock.completed() + " из " + lock.tasks());
            console.raw("    как это читать: при pinning виртуальный поток не отдаёт carrier, "
                    + "поэтому одновременно ждут не больше " + pinned.parallelism() + " задач; "
                    + "ReentrantLock позволяет снять поток с carrier и ждать всем сразу");

            console.raw("");
            console.raw("Этап 4. Масштабирование: " + properties.getScalingTasks()
                    + " задач с ожиданием " + properties.getScalingDelayMillis() + " мс");
            SleepyTask sleepy = new SleepyTask(properties.getScalingDelayMillis());
            BenchmarkResult platformScaling = LoomBenchmarks
                    .runPlatform("платформенный пул " + properties.getPlatformPoolSize(),
                            properties.getScalingTasks(),
                            properties.getPlatformPoolSize(),
                            properties.getTaskTimeoutMillis(),
                            sleepy)
                    .metrics();
            BenchmarkResult virtualScaling = LoomBenchmarks
                    .runVirtual("виртуальные потоки",
                            properties.getScalingTasks(),
                            properties.getTaskTimeoutMillis(),
                            sleepy)
                    .metrics();
            LoomReport.scalingLines(platformScaling, virtualScaling)
                    .forEach(line -> console.raw("    " + line));
            console.raw("    завершено задач: пул " + platformScaling.succeeded() + " из " + platformScaling.tasks()
                    + ", виртуальные " + virtualScaling.succeeded() + " из " + virtualScaling.tasks());
            console.raw("    память (приблизительно): пул "
                    + MemoryProbe.describeDelta(platformScaling.heapDeltaBytes())
                    + ", виртуальные " + MemoryProbe.describeDelta(virtualScaling.heapDeltaBytes()));

            console.raw("");
            console.raw("Этап 5. " + properties.getSwarmTasks()
                    + " задач с печатью имени потока: Thread.ofVirtual().start() против new Thread().start()");
            console.raw("    каждая задача пишет имя своего потока и ждёт "
                    + properties.getSwarmSleepMillis() + " мс (имитация неблокирующего IO)");
            int printLimit = Math.max(0, properties.getSwarmPrintNames());
            AtomicInteger virtualPrinted = new AtomicInteger();
            AtomicInteger platformPrinted = new AtomicInteger();
            SwarmRun virtualSwarm = ThreadSwarm.runVirtual(
                    properties.getSwarmTasks(),
                    properties.getSwarmSleepMillis(),
                    nameEcho(virtualPrinted, printLimit));
            console.raw("    " + virtualSwarm.summaryLine());
            console.raw("    имён собрано " + virtualSwarm.names().size() + ", первых: "
                    + String.join(", ", virtualSwarm.sample(3)));

            SwarmRun platformSwarm = ThreadSwarm.runPlatform(
                    properties.getSwarmTasks(),
                    properties.getSwarmSleepMillis(),
                    properties.getSwarmMaxPlatformThreads(),
                    nameEcho(platformPrinted, printLimit));
            console.raw("    " + platformSwarm.summaryLine());
            console.raw("    имён собрано " + platformSwarm.names().size() + ", первых: "
                    + String.join(", ", platformSwarm.sample(3)));
            LoomReport.swarmLines(virtualSwarm, platformSwarm)
                    .forEach(line -> console.raw("    " + line));
            console.raw("    целостность: виртуальные — " + markers(virtualSwarm)
                    + "; платформенные — " + markers(platformSwarm));
            console.raw("    память (приблизительно, без System.gc()): виртуальные "
                    + MemoryProbe.describeDelta(virtualSwarm.heapDeltaBytes())
                    + ", платформенные " + MemoryProbe.describeDelta(platformSwarm.heapDeltaBytes()));
            console.raw("    цена: 10 000 потоков ОС — это ~10 ГиБ зарезервированного адресного "
                    + "пространства под стеки и десятки тысяч операций планировщика; "
                    + "виртуальные потоки — объекты в куче, стек растёт по мере надобности");
            console.raw("    почему пик одновременных задач у платформенных меньше " + platformSwarm.tasks()
                    + ": пауза всего " + properties.getSwarmSleepMillis() + " мс, потоки не успевают "
                    + "накопиться — JVM создаёт их примерно " + Math.round(platformSwarm.startMillis() > 0
                            ? platformSwarm.tasks() / platformSwarm.startMillis() : 0)
                    + " в миллисекунду и столько же завершается; цена видна в колонке «старт»");
            console.raw("    если машина не даёт столько потоков ОС: "
                    + "--app.hw14.swarm-max-platform-threads=N запускает пачками по N");

            console.raw("");
            console.raw("Этап 6. StructuredTaskScope: группа виртуальных потоков под управлением скоупа");
            console.raw("    подзадач " + properties.getStructuredTasks()
                    + ", обычная задержка " + properties.getStructuredDelayMillis()
                    + " мс, срок " + properties.getStructuredTimeoutMillis() + " мс");
            ScopeRun allDone = StructuredExperiments.runAll(
                    properties.getStructuredTasks(), properties.getStructuredDelayMillis());
            console.raw("    1) join() ждёт всю группу — " + allDone.summaryLine());
            console.raw("       результатов после ожидания: " + allDone.results().size()
                    + ", все успешны: " + marker(allDone.allSucceeded())
                    + ", все подзадачи виртуальные: " + marker(allDone.virtualExecutions() == allDone.tasks()));

            ScopeRun failed = StructuredExperiments.runWithFailure(
                    properties.getStructuredTasks(),
                    properties.getStructuredDelayMillis(),
                    properties.getStructuredFailingTask(),
                    properties.getStructuredFailAfterMillis());
            console.raw("    2) одна падает через " + properties.getStructuredFailAfterMillis()
                    + " мс — " + failed.summaryLine());

            ScopeRun timedOut = StructuredExperiments.runWithTimeout(
                    properties.getStructuredTasks(),
                    properties.getStructuredSlowDelayMillis(),
                    properties.getStructuredTimeoutMillis());
            console.raw("    3) срок истёк — " + timedOut.summaryLine());

            ScopeRun firstSuccess = StructuredExperiments.runFirstSuccess(
                    properties.getStructuredReplicaDelaysMillis());
            console.raw("    4) первый успешный источник — " + firstSuccess.summaryLine()
                    + ", ответ: " + firstSuccess.results().stream().findFirst().orElse("(нет)"));

            List<ScopeRun> scopes = List.of(allDone, failed, timedOut, firstSuccess);
            console.raw("    сводка:");
            LoomReport.structuredLines(scopes).forEach(line -> console.raw("    " + line));
            console.raw("    обработка ошибок и отмены:");
            LoomReport.structuredExplanationLines(scopes).forEach(line -> console.raw("    " + line));
            console.raw("    как это читать: скоуп — это блок, а не пул. Пока блок не закрыт, "
                    + "наружу выйти нельзя; отмена прерывает подзадачи и close() дожидается их "
                    + "завершения, поэтому «повисших» потоков не остаётся");
            console.raw("    без отмены группа шла бы не меньше "
                    + (properties.getStructuredFailAfterMillis() + properties.getStructuredDelayMillis())
                    + " мс (" + properties.getStructuredFailAfterMillis() + " мс до сбоя + "
                    + properties.getStructuredDelayMillis() + " мс паузы остальных), "
                    + "с отменой — " + Math.round(failed.wallMillis()) + " мс: прерывание "
                    + failed.cancelled() + " подзадач тоже стоит времени, но до конца они не дорабатывали");

            console.raw("");
            console.raw("Этап 7. Итог");
            LoomReport.summaryLines(platformLoad, virtualLoad, pinned, lock,
                    platformScaling, virtualScaling, virtualSwarm, platformSwarm, scopes)
                    .forEach(line -> console.raw("    " + line));
            console.raw("");
            console.raw("    когда виртуальные потоки полезны: тысячи одновременных блокирующих операций "
                    + "(HTTP, БД, файлы) — потоков ОС остаётся столько же, сколько ядер");
            console.raw("    когда нет: CPU-нагрузка (сортировка, сжатие, вычисления) — виртуальный поток "
                    + "всё равно занимает carrier на всё время работы");
            console.raw("    чего избегать: synchronized вокруг блокирующих вызовов (pinning), "
                    + "пулы виртуальных потоков фиксированного размера, ThreadLocal на миллион задач");
            console.raw("    запросов к сайту за демонстрацию: " + client.requestCount()
                    + ", активных обработчиков сайта после этапов: " + site.activeRequests());
        } catch (IOException e) {
            throw new IllegalStateException("не удалось поднять локальный сайт: " + e.getMessage(), e);
        }
    }

    private LoadRun load(List<String> urls, PageVacancyClient client, boolean virtual) {
        if (virtual) {
            LoomBenchmarks.Run<PageLoad> run = LoomBenchmarks.runNamedVirtual(
                    "виртуальные потоки",
                    urls.size(),
                    properties.getTaskTimeoutMillis(),
                    index -> client.fetch(urls.get(index), index));
            return new LoadRun(run.metrics(), run.results());
        }
        LoomBenchmarks.Run<PageLoad> run = LoomBenchmarks.runPlatform(
                "платформенный пул",
                urls.size(),
                properties.getFixedPoolSize(),
                properties.getTaskTimeoutMillis(),
                index -> client.fetch(urls.get(index), index));
        return new LoadRun(run.metrics(), run.results());
    }

    private void printLoad(LoadRun run, String indent) {
        console.raw(indent + run.metrics().summaryLine());
        console.raw(indent + "без потерь: " + marker(run.lossless())
                + ", разных страниц " + run.pages().stream().map(PageLoad::pageNumber).distinct().count()
                + " из " + run.metrics().tasks()
                + ", сбоев " + run.failedPages());
        List<String> titles = LoomReport.titleLines(run, properties.getSampleTitles());
        if (!titles.isEmpty()) {
            console.raw(indent + "примеры заголовков:");
            titles.forEach(line -> console.raw(indent + "  " + line));
        }
    }

    private static String integrity(LoadRun run) {
        return "без потерь " + marker(run.lossless()) + ", задач с результатом " + run.metrics().succeeded()
                + " из " + run.metrics().tasks() + ", страниц со сбоем " + run.failedPages();
    }

    private static String marker(boolean value) {
        return value ? "да" : "нет";
    }

    /**
     * Приёмник имён потоков: каждая задача печатает своё имя, но в журнал попадают только первые
     * {@code limit} строк — десять тысяч подряд отчёт не украшают. Полный список остаётся в
     * {@link SwarmRun#names()} и проверяется тестами.
     */
    private Consumer<String> nameEcho(AtomicInteger counter, int limit) {
        return name -> {
            synchronized (counter) {
                int number = counter.incrementAndGet();
                if (limit > 0 && number <= limit) {
                    console.raw("      " + number + ") поток " + name);
                }
            }
        };
    }

    private static String markers(SwarmRun run) {
        return "завершено " + run.completed() + " из " + run.tasks()
                + ", разных имён " + run.distinctNames()
                + ", исполнений на виртуальных " + run.virtualExecutions()
                + ", на платформенных " + run.platformExecutions();
    }
}
