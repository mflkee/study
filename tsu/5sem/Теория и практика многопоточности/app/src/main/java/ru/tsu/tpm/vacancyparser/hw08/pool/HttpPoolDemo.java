package ru.tsu.tpm.vacancyparser.hw08.pool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
import ru.tsu.tpm.vacancyparser.hw08.pool.config.Hw08Properties;
import ru.tsu.tpm.vacancyparser.hw08.pool.config.RunConfig;
import ru.tsu.tpm.vacancyparser.hw08.pool.config.WorkMode;
import ru.tsu.tpm.vacancyparser.hw08.pool.report.RunSummary;
import ru.tsu.tpm.vacancyparser.hw08.pool.server.LocalStubServer;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.HttpRequester;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.RequestCollector;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.RunResult;

/**
 * Демонстрация ДЗ 8 «Пул потоков»: асинхронные HTTP-запросы по списку адресов через вручную
 * настроенный {@code ThreadPoolExecutor}.
 *
 * <p>Запускается только при {@code --app.hw=hw08}. В режиме по умолчанию поднимает локальный
 * сервер-заглушку на свободном порту, чтобы прогон был воспроизводимым и не зависел от внешней сети.
 * Все настройки (режим, список адресов, размеры пула, ёмкость очереди, таймауты) берутся из
 * конфигурации {@code app.hw08.*} и печатаются перед прогоном.
 *
 * <p>После сбора результатов пул обязательно останавливается, сводка печатается и сохраняется в файл
 * каталога сборки, а процесс завершается штатно. Ограничение времени на всю демонстрацию страхует от
 * зависания.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw08")
public class HttpPoolDemo implements ApplicationRunner {

    /** Предел времени на всю демонстрацию. */
    static final long TIME_LIMIT_MILLIS = 120_000L;

    /** Код выхода, если демонстрация не уложилась в лимит. */
    static final int TIME_LIMIT_EXIT_CODE = 3;

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;
    private final Hw08Properties properties;

    public HttpPoolDemo(
            ConsoleOutput console, ConfigurableApplicationContext context, Hw08Properties properties) {
        this.console = console;
        this.context = context;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        console.section("ДЗ 8. Пул потоков: асинхронные HTTP-запросы");
        console.raw("");
        console.raw(ProcessInfo.pidLine());

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
            console.info("Демонстрация завершена: запросы выполнены через собственный пул, сводка выведена, "
                    + "пул остановлен");
            console.raw("");
            console.raw("живых потоков пула после завершения: "
                    + ThreadDump.liveThreadsWithPrefixes(NamedThreadFactory.PREFIX));
            context.close();
            return;
        }

        if (outcome.failure() != null) {
            console.warn("демонстрация завершилась ошибкой: " + outcome.failure());
        } else {
            console.warn("демонстрация не уложилась в " + TIME_LIMIT_MILLIS + " мс — снимаю дамп потоков");
            console.raw(ThreadDump.capture());
        }
        context.close();
        System.exit(TIME_LIMIT_EXIT_CODE);
    }

    private void runStages() {
        try {
            execute();
        } catch (IOException e) {
            throw new IllegalStateException("ошибка ввода-вывода демонстрации: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("демонстрация прервана", e);
        }
    }

    /** Полный прогон: подготовка адресов, пул, запросы, сводка, остановка пула. */
    private void execute() throws IOException, InterruptedException {
        RunConfig base = RunConfig.from(properties);
        LocalStubServer stub = null;
        String modeNote;
        List<String> urls;
        try {
            if (base.mode().usesLocalStub()) {
                stub = LocalStubServer.start();
                List<String> local = stub.urls();
                urls = base.mode() == WorkMode.MIXED ? concat(base.urls(), local) : local;
                modeNote = "источник адресов: локальный сервер-заглушка, порт " + stub.port();
            } else {
                urls = base.urls();
                modeNote = "источник адресов: внешние адреса из конфигурации";
            }

            RunConfig config = base.withUrls(urls);
            HttpWorkerPool pool = HttpWorkerPool.create(config);
            try {
                console.raw("");
                console.raw("Настройки прогона:");
                config.settingsLines().forEach(console::item);
                console.raw("");
                console.raw("Настройки пула (фактические):");
                pool.settingsLines(config).forEach(console::item);

                HttpRequester requester =
                        new HttpRequester(config.connectTimeoutMillis(), config.requestTimeoutMillis());
                console.raw("");
                console.raw("Отправка запросов, прогресс по завершении:");
                RequestCollector collector = new RequestCollector(pool, requester, line -> console.raw("    " + line));
                RunResult result = collector.run(config.urls());

                HttpWorkerPool.ShutdownOutcome shutdown = pool.shutdown();
                if (result.rejectionCount() > 0) {
                    console.warn("обработчик отказа сработал " + result.rejectionCount()
                            + " раз: часть задач выполнена вызывающим потоком, суммарное время прогона удлинено");
                }

                console.raw("");
                List<String> summary = RunSummary.build(config, result, modeNote, shutdown.message());
                summary.forEach(console::raw);
                Path file = RunSummary.write(RunSummary.DEFAULT_DIR, summary);
                console.raw("");
                console.raw("файл сводки: " + file.toAbsolutePath());
                if (!shutdown.terminated()) {
                    console.warn(shutdown.message());
                }
            } finally {
                pool.close();
            }
        } finally {
            if (stub != null) {
                stub.close();
            }
        }
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first.size() + second.size());
        all.addAll(first);
        all.addAll(second);
        return all;
    }
}
