package ru.tsu.tpm.vacancyparser.hw14.loom;

import java.io.IOException;
import java.util.List;
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
import ru.tsu.tpm.vacancyparser.hw14.loom.web.PageVacancyClient;

/**
 * Демонстрация ДЗ 14 «Введение в проект Loom (Virtual Threads)».
 *
 * <p>Четыре этапа, каждый отвечает на свой вопрос:
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
            console.raw("Этап 5. Итог");
            LoomReport.summaryLines(platformLoad, virtualLoad, pinned, lock, platformScaling, virtualScaling)
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
}
