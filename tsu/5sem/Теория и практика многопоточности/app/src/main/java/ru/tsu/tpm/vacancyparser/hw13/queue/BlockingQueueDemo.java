package ru.tsu.tpm.vacancyparser.hw13.queue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.common.ProcessInfo;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.TimeBoxedExecution;
import ru.tsu.tpm.vacancyparser.hw13.queue.config.Hw13Properties;
import ru.tsu.tpm.vacancyparser.hw13.queue.report.CrawlReport;
import ru.tsu.tpm.vacancyparser.hw13.queue.report.QueueReport;
import ru.tsu.tpm.vacancyparser.hw13.queue.server.CrawlServer;
import ru.tsu.tpm.vacancyparser.hw13.queue.server.VacancySiteServer;
import ru.tsu.tpm.vacancyparser.hw13.queue.web.PageFetcher;

/**
 * Демонстрация ДЗ 13 «BlockingQueue и производитель—потребитель».
 *
 * <p>Поднимает локальный сайт вакансий (без интернета), запускает несколько производителей с адресами
 * и несколько потребителей, которые скачивают страницы и извлекают заголовки. Первый прогон идёт с
 * маленькой очередью — видно обратное давление; второй с большой — видно, что производители больше не
 * ждут, а задачи копятся.
 *
 * <p>Запускается только при {@code --app.hw=hw13}. Все потоки именованные и не демоны; после прогона
 * конвейер полностью останавливается, а демонстрация печатает, что живых потоков не осталось.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw13")
public class BlockingQueueDemo implements ApplicationRunner {

    /** Предел времени на всю демонстрацию. */
    static final long TIME_LIMIT_MILLIS = 180_000L;

    /** Код выхода, если демонстрация не уложилась в лимит. */
    static final int TIME_LIMIT_EXIT_CODE = 3;

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;
    private final Hw13Properties properties;

    public BlockingQueueDemo(
            ConsoleOutput console, ConfigurableApplicationContext context, Hw13Properties properties) {
        this.console = console;
        this.context = context;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        console.section("ДЗ 13. BlockingQueue: производители и потребители в конвейере обхода");
        console.raw("");
        console.raw(ProcessInfo.pidLine());
        console.raw("Настройки ДЗ 13:");
        console.item("страниц: " + properties.getPages() + ", производителей: " + properties.getProducers()
                + ", потребителей: " + properties.getConsumers());
        console.item("ёмкость очереди: " + properties.getQueueCapacity()
                + ", для сравнения: " + properties.getComparisonCapacity());
        console.item("задержка страницы: " + properties.getPageDelayMillis() + " мс, таймаут запроса: "
                + properties.getRequestTimeoutMillis() + " мс");
        console.item("предел ожидания завершения потоков: " + properties.getShutdownTimeoutMillis() + " мс");

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
            console.info("Демонстрация завершена: все задачи обработаны, потерь и повторов нет, "
                    + "производители и потребители остановлены");
        } else if (outcome.failure() != null) {
            console.warn("демонстрация завершилась ошибкой: " + outcome.failure());
        } else {
            console.warn("демонстрация не уложилась в " + TIME_LIMIT_MILLIS + " мс — снимаю дамп потоков");
            console.raw(ThreadDump.capture());
        }

        context.close();
        console.raw("");
        console.raw("живых потоков ДЗ 13 после завершения: " + ThreadDump.liveThreadsWithPrefixes(
                CrawlServer.PRODUCER_PREFIX, CrawlServer.CONSUMER_PREFIX, "hw13-site-worker"));
        if (!(outcome.completed() && outcome.failure() == null)) {
            System.exit(TIME_LIMIT_EXIT_CODE);
        }
    }

    private void runStages() {
        try (VacancySiteServer site = VacancySiteServer.start(properties.getPageDelayMillis());
                PageFetcher fetcher = new PageFetcher(
                        properties.getConnectTimeoutMillis(), properties.getRequestTimeoutMillis())) {

            List<String> urls = site.urls(properties.getPages());
            console.raw("");
            console.raw("Этап 1. Локальный сайт вакансий");
            console.raw("    порт " + site.port() + ", страниц " + urls.size()
                    + ", из них с кодом 404: " + countNotFound(urls.size()));
            console.raw("    пример адреса: " + urls.get(0));

            console.raw("");
            console.raw("Этап 2. Маленькая очередь (" + properties.getQueueCapacity()
                    + "): производители ждут потребителей");
            ConcurrentLinkedQueue<String> journal = new ConcurrentLinkedQueue<>();
            CrawlReport small = CrawlServer.run(
                    "очередь на " + properties.getQueueCapacity(),
                    urls,
                    properties.getProducers(),
                    properties.getConsumers(),
                    properties.getQueueCapacity(),
                    fetcher,
                    journal::add,
                    properties.getShutdownTimeoutMillis());
            QueueReport.summaryLines(small).forEach(line -> console.raw("    " + line));
            console.raw("    сбои по кодам: " + QueueReport.failuresByStatus(small));
            console.raw("    примеры заголовков:");
            QueueReport.titleLines(small, properties.getSampleTitles())
                    .forEach(line -> console.raw("      " + line));
            console.raw("    журнал «кто что делал» (первые " + properties.getTraceLines() + " строк):");
            journal.stream().limit(Math.max(0, properties.getTraceLines()))
                    .forEach(line -> console.raw("      " + line));
            console.raw("    ожидание производителей в put: " + small.blockedPuts() + " раз, "
                    + String.format(java.util.Locale.ROOT, "%.1f мс", small.blockedPutMillis()));

            console.raw("");
            console.raw("Этап 3. Большая очередь (" + properties.getComparisonCapacity()
                    + "): задачи копятся, производители не ждут");
            CrawlReport large = CrawlServer.run(
                    "очередь на " + properties.getComparisonCapacity(),
                    urls,
                    properties.getProducers(),
                    properties.getConsumers(),
                    properties.getComparisonCapacity(),
                    fetcher,
                    null,
                    properties.getShutdownTimeoutMillis());
            QueueReport.summaryLines(large).forEach(line -> console.raw("    " + line));
            console.raw("");
            console.raw("Этап 4. Сравнение ёмкостей");
            QueueReport.comparisonLines(small, large).forEach(line -> console.raw("    " + line));

            console.raw("");
            console.raw("Этап 5. Итог конвейера");
            console.raw("    все адреса обработаны: " + marker(small.lossless() && large.lossless()));
            console.raw("    страниц с 404 обработано как сбой, а не потеряно: "
                    + marker(small.failed() == countNotFound(urls.size())));
            console.raw("    потребителей, реально бравших задачи: " + QueueReport.consumerCount(small)
                    + " из " + properties.getConsumers());
            console.raw("    запросов к сайту: " + fetcher.requestCount());
        } catch (IOException e) {
            throw new IllegalStateException("не удалось поднять локальный сайт: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("демонстрация прервана", e);
        }
    }

    private static int countNotFound(int pages) {
        int count = 0;
        for (int index = 0; index < pages; index++) {
            if (VacancySiteServer.expectedStatus(index) == 404) {
                count++;
            }
        }
        return count;
    }

    private static String marker(boolean value) {
        return value ? "да" : "нет";
    }
}
