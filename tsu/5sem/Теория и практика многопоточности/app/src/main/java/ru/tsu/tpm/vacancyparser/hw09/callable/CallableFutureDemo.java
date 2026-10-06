package ru.tsu.tpm.vacancyparser.hw09.callable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.common.ProcessInfo;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.TimeBoxedExecution;
import ru.tsu.tpm.vacancyparser.hw09.callable.aggregator.CycleReport;
import ru.tsu.tpm.vacancyparser.hw09.callable.aggregator.DataProvider;
import ru.tsu.tpm.vacancyparser.hw09.callable.aggregator.EntityData;
import ru.tsu.tpm.vacancyparser.hw09.callable.aggregator.PeriodicDataAggregator;
import ru.tsu.tpm.vacancyparser.hw09.callable.batch.BatchScenario;
import ru.tsu.tpm.vacancyparser.hw09.callable.cancel.CancellationScenario;
import ru.tsu.tpm.vacancyparser.hw09.callable.config.Hw09Properties;
import ru.tsu.tpm.vacancyparser.hw09.callable.scheduled.BackgroundMonitor;

/**
 * Демонстрация ДЗ 9 «Callable и Future».
 *
 * <p>Запускается только при {@code --app.hw=hw09} и идёт по этапам задания: отмена длительной задачи
 * через {@code Future}/{@code FutureTask}, одновременный запуск набора задач через {@code invokeAll},
 * периодическая фоновая задача на {@code ScheduledExecutorService} и сервис периодической агрегации
 * {@code PeriodicDataAggregator} с устойчивостью к частичным сбоям источника.
 *
 * <p>Все сценарии ограничены по времени, а все исполнители останавливаются по завершении. Период и
 * число циклов агрегатора задаются свойствами {@code app.hw09.*}.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw09")
public class CallableFutureDemo implements ApplicationRunner {

    /** Предел времени на всю демонстрацию. */
    static final long TIME_LIMIT_MILLIS = 120_000L;

    /** Код выхода, если демонстрация не уложилась в лимит. */
    static final int TIME_LIMIT_EXIT_CODE = 3;

    private static final long LONG_TASK_MILLIS = 6_000L;
    private static final long CANCEL_AFTER_MILLIS = 2_000L;
    private static final int BATCH_TASKS = 5;
    private static final long BATCH_MIN_MILLIS = 500L;
    private static final long BATCH_MAX_MILLIS = 1_500L;

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;
    private final Hw09Properties properties;

    public CallableFutureDemo(
            ConsoleOutput console, ConfigurableApplicationContext context, Hw09Properties properties) {
        this.console = console;
        this.context = context;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        console.section("ДЗ 9. Callable и Future");
        console.raw("");
        console.raw(ProcessInfo.pidLine());
        console.raw("Настройки агрегатора: период " + properties.getPeriodSeconds() + " с, циклов "
                + properties.getCycles());

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
            console.info("Демонстрация завершена: задача отменена, набор выполнен одновременно, "
                    + "периодическая задача и агрегатор отработали, исполнители остановлены");
            console.raw("");
            console.raw("живых потоков ДЗ 9 после завершения: "
                    + ThreadDump.liveThreadsWithPrefixes("hw09-"));
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
            stageCancellation();
            stageBatch();
            stageScheduled();
            stageAggregation();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("демонстрация прервана", e);
        } catch (Exception e) {
            throw new IllegalStateException("ошибка демонстрации: " + e.getMessage(), e);
        }
    }

    /** Этап 1. Отмена длительной задачи через FutureTask. */
    private void stageCancellation() throws InterruptedException {
        console.raw("");
        console.raw("Этап 1. Отмена длительной задачи (FutureTask + cancel)");
        CancellationScenario.CancelOutcome cancelled =
                CancellationScenario.runCancelled(LONG_TASK_MILLIS, CANCEL_AFTER_MILLIS, 100L);
        console.raw(String.format(
                Locale.ROOT,
                "    задача %d мс, отмена через %d мс: начата=%s, отменена=%s, результат недоступен=%s",
                LONG_TASK_MILLIS, CANCEL_AFTER_MILLIS,
                marker(cancelled.started()), marker(cancelled.cancelled()), marker(cancelled.resultUnavailable())));
        console.raw(String.format(
                Locale.ROOT,
                "    задача прекратила работу по прерыванию=%s, прогресс %d из %d шагов, исполнитель завершён=%s",
                marker(cancelled.interrupted()), cancelled.progressAtCancel(), cancelled.totalSteps(),
                marker(cancelled.executorTerminated())));

        CancellationScenario.CancelOutcome completed = CancellationScenario.runCompleted(800L, 100L);
        console.raw(String.format(
                Locale.ROOT,
                "    если задача успела завершиться: результат=%s, отменена=%s, завершилась сама=%s",
                completed.result(), marker(completed.cancelled()), marker(completed.completedNaturally())));
    }

    /** Этап 2. Набор задач и invokeAll. */
    private void stageBatch() throws InterruptedException, java.util.concurrent.ExecutionException {
        console.raw("");
        console.raw("Этап 2. Набор задач и invokeAll");
        ExecutorService pool = Executors.newFixedThreadPool(BATCH_TASKS, WorkerThreads.factory("hw09-batch-"));
        try {
            BatchScenario.BatchOutcome batch =
                    BatchScenario.run(pool, BATCH_TASKS, BATCH_MIN_MILLIS, BATCH_MAX_MILLIS, 2026L);
            console.raw("    результаты по порядку задач: " + batch.results());
            console.raw("    длительности задач, мс: " + batch.delaysMillis());
            console.raw(String.format(
                    Locale.ROOT,
                    "    суммарное время набора: %.3f мс, самая долгая задача: %d мс, сумма длительностей: %d мс",
                    batch.totalMillis(), batch.maxDelayMillis(), batch.sumDelayMillis()));
            long maxOffset = batch.startOffsetsNanos().stream().mapToLong(Long::longValue).max().orElse(0L) / 1_000_000L;
            console.raw("    разброс старта задач: " + maxOffset + " мс (задачи стартовали почти одновременно)");
        } finally {
            shutdown(pool, "набор задач");
        }
    }

    /** Этап 3. Периодическая фоновая задача. */
    private void stageScheduled() throws InterruptedException {
        console.raw("");
        console.raw("Этап 3. Периодическая фоновая задача (ScheduledExecutorService)");
        BackgroundMonitor monitor = new BackgroundMonitor("hw09-monitor-");
        AtomicLong tick = new AtomicLong();
        monitor.setOperation(() -> {
            long current = tick.incrementAndGet();
            if (current == 2L) {
                throw new IllegalStateException("сбой фоновой проверки №2");
            }
        });
        try {
            monitor.start(400L);
            monitor.awaitCycles(4, 5_000L);
            console.raw("    имитация фоновой операции «мониторинг очереди»: циклов " + monitor.cycles()
                    + ", из них со сбоем " + monitor.failures()
                    + " (сбой одного выполнения не остановил расписание)");
        } finally {
            monitor.stop(2_000L);
        }
        console.raw("    после остановки: циклов " + monitor.cycles() + ", исполнитель завершён="
                + marker(monitor.isTerminated()));
    }

    /** Этап 4. Сервис периодической агрегации с частичными сбоями источника. */
    private void stageAggregation() throws InterruptedException {
        console.raw("");
        console.raw("Этап 4. PeriodicDataAggregator: частичный сбой источника не роняет сервис");

        List<String> entities = List.of("EUR", "USD", "GBP", "JPY", "CHF", "CNY");
        DataProvider provider = id -> {
            Thread.sleep(100L);
            if (id.equals("JPY") || id.equals("CHF")) {
                throw new IllegalStateException("источник недоступен: " + id);
            }
            return new EntityData(id, "курс-" + id);
        };
        long periodMillis = properties.getPeriodSeconds() * 1_000L;

        PeriodicDataAggregator aggregator = new PeriodicDataAggregator(
                () -> entities,
                provider,
                report -> console.raw(String.format(
                        Locale.ROOT,
                        "    цикл %d: успешно %d, сбоев %d, длительность %.3f мс",
                        report.cycle(), report.successes().size(), report.failures().size(),
                        report.durationMillis())),
                periodMillis,
                entities.size(),
                "hw09-agg-");
        try {
            aggregator.start();
            boolean reached = aggregator.awaitCycles(properties.getCycles(), periodMillis * properties.getCycles() + 5_000L);
            // Дожидаемся, пока завершится и обработается последний цикл, чтобы итоговые строки шли после построчных.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (aggregator.reports().size() < properties.getCycles() && System.nanoTime() < deadline) {
                Thread.sleep(5L);
            }
            console.raw("    достигнуто циклов: " + aggregator.cycles() + " (ожидалось " + properties.getCycles()
                    + ", успех=" + marker(reached) + ")");
            CycleReport last = aggregator.lastReport();
            if (last != null) {
                console.raw("    последний цикл: за успешные результаты обработано " + last.successes().size()
                        + ", сбойных " + last.failures().size() + " (" + describeFailures(last) + ")");
            }
        } finally {
            aggregator.stop(3_000L);
        }
        console.raw("    после остановки: циклов " + aggregator.cycles() + ", исполнители завершены="
                + marker(aggregator.isTerminated()));
    }

    private static String describeFailures(CycleReport report) {
        if (report.failures().isEmpty()) {
            return "сбоев нет";
        }
        StringBuilder text = new StringBuilder();
        report.failures().forEach(failure -> {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append(failure.id());
        });
        return text.toString();
    }

    private void shutdown(ExecutorService executor, String what) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            console.warn("исполнитель «" + what + "» не завершился в срок — принудительное завершение");
        }
    }

    private static String marker(boolean value) {
        return value ? "да" : "нет";
    }
}
