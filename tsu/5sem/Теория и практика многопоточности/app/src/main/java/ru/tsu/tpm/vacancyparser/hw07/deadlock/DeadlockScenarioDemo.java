package ru.tsu.tpm.vacancyparser.hw07.deadlock;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.common.ProcessInfo;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.TimeBoxedExecution;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.deadlock.DeadlockScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.livelock.LivelockConfig;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.livelock.LivelockScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.prevention.OrderedLockingScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.prevention.TimeoutLockingScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.starvation.FairStarvationScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.starvation.StarvationScenario;

/**
 * Демонстрация ДЗ 7 «Deadlock, livelock, starvation».
 *
 * <p>Запускается только при {@code --app.hw=hw07}. Выполняет сценарии по порядку: взаимная блокировка
 * с дампом и разбором, livelock, starvation и предотвращение. Можно выбрать один сценарий свойством
 * {@code app.hw07.scenario} ({@code deadlock|livelock|starvation|prevention|all}) и задать окно
 * наблюдения свойством {@code app.hw07.observation-seconds} (0 — значения по умолчанию).
 *
 * <p>Ни один сценарий не висит бесконечно: взаимная блокировка ограничена окном и затем снимается,
 * livelock и starvation завершаются по окну наблюдения. Вся демонстрация дополнительно обёрнута в
 * ограничение времени — как в ДЗ 4 и ДЗ 6.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw07")
public class DeadlockScenarioDemo implements ApplicationRunner {

    /** Предел времени на всю демонстрацию. */
    static final long TIME_LIMIT_MILLIS = 180_000L;

    /** Код выхода, если демонстрация не уложилась в лимит. */
    static final int TIME_LIMIT_EXIT_CODE = 3;

    /** Значение свойства «выполнить все сценарии». */
    static final String ALL = "all";

    /** Значения по умолчанию для окон наблюдения, мс. */
    static final long DEFAULT_DEADLOCK_HOLD_MILLIS = 10_000L;
    static final long DEFAULT_LIVELOCK_WINDOW_MILLIS = 3_000L;
    static final long DEFAULT_STARVATION_WINDOW_MILLIS = 3_000L;

    private static final int STARVATION_WORKERS = 4;
    private static final int PREVENTION_RUNS = 100;
    private static final int PREVENTION_THREADS = 2;
    private static final int PREVENTION_ITERATIONS = 200;

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;
    private final String scenario;
    private final long observationSeconds;

    public DeadlockScenarioDemo(
            ConsoleOutput console,
            ConfigurableApplicationContext context,
            @Value("${app.hw07.scenario:all}") String scenario,
            @Value("${app.hw07.observation-seconds:0}") long observationSeconds) {
        this.console = console;
        this.context = context;
        this.scenario = scenario == null || scenario.isBlank() ? ALL : scenario.trim();
        this.observationSeconds = observationSeconds;
    }

    @Override
    public void run(ApplicationArguments args) {
        console.section("ДЗ 7. Deadlock, livelock, starvation");
        console.raw("");
        console.raw("Выбранный сценарий: " + scenario);
        console.raw("Предел времени на демонстрацию: " + TIME_LIMIT_MILLIS + " мс");
        console.raw(ProcessInfo.pidLine() + ", " + ThreadDump.summary());

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
            console.info("Демонстрация завершена: сценарии выполнены, взаимная блокировка снята, "
                    + "потоки освобождены");
            console.raw("");
            console.raw(ThreadDump.summary());
            console.raw("живых потоков демонстрации после завершения: "
                    + ThreadDump.liveThreadsWithPrefixes(
                            "Deadlock-", "Livelock-", "Starvation-", "Prevention-"));
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
            if (selected(DeadlockScenario.name())) {
                stageDeadlock();
            }
            if (selected(LivelockScenario.NAME)) {
                stageLivelock();
            }
            if (selected(StarvationScenario.NAME)) {
                stageStarvation();
            }
            if (selected(OrderedLockingScenario.NAME)) {
                stagePrevention();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("демонстрация прервана", e);
        }
    }

    private boolean selected(String name) {
        return ALL.equals(scenario) || name.equals(scenario);
    }

    /** Этап 1. Взаимная блокировка, дамп и разбор. */
    private void stageDeadlock() throws InterruptedException {
        console.raw("");
        console.raw("Этап 1. Deadlock: два потока, два ресурса, противоположный порядок захвата");
        long pid = ProcessInfo.currentPid();
        long holdMillis = observationSeconds > 0 ? observationSeconds * 1_000L : DEFAULT_DEADLOCK_HOLD_MILLIS;

        DeadlockScenario.Outcome outcome = DeadlockScenario.run(holdMillis);
        Hw07Report.deadlockBlock(outcome, pid).forEach(console::raw);

        Set<String> fromDump = Set.copyOf(outcome.report().threadNames());
        Set<String> fromJvm = Set.of(outcome.jvmThreadNames().split(",\\s*"));
        console.raw("    имена потоков в разборе дампа совпадают с сценарием: "
                + (fromDump.equals(fromJvm) && fromDump.size() == 2));
        console.raw("    " + Hw07Report.nameConsistencyHint());
    }

    /** Этап 2. Livelock: симметричное уступание против детерминированного порядка. */
    private void stageLivelock() throws InterruptedException {
        console.raw("");
        console.raw("Этап 2. Livelock: активные попытки без продвижения");
        long stageWindow = observationSeconds > 0 ? observationSeconds * 1_000L : DEFAULT_LIVELOCK_WINDOW_MILLIS;
        long window = perVariant(stageWindow);

        console.raw("");
        console.raw("  Livelock-конфигурация:");
        LivelockScenario.Outcome livelock = LivelockScenario.run(LivelockConfig.LIVELOCK, window);
        Hw07Report.livelockBlock(livelock).forEach(console::raw);
        console.raw("    отличие от deadlock: потоки не заблокированы и блокировок не удерживают");

        console.raw("");
        console.raw("  Нормальная конфигурация (предотвращение — детерминированный порядок):");
        LivelockScenario.Outcome normal = LivelockScenario.run(LivelockConfig.NORMAL, window);
        Hw07Report.livelockBlock(normal).forEach(console::raw);
    }

    /** Этап 3. Starvation: несправедливый захват против честной очереди. */
    private void stageStarvation() throws InterruptedException {
        console.raw("");
        console.raw("Этап 3. Starvation: неравный доступ и его устранение");
        long stageWindow = observationSeconds > 0 ? observationSeconds * 1_000L : DEFAULT_STARVATION_WINDOW_MILLIS;
        long window = perVariant(stageWindow);

        console.raw("");
        console.raw("  Некорректно: несправедливый захват, приоритеты подняты, Thread.yield():");
        Hw07Report.starvationBlock(StarvationScenario.runUnfair(STARVATION_WORKERS, window))
                .forEach(console::raw);

        console.raw("");
        console.raw("  Корректно: честная блокировка, одинаковые приоритеты:");
        Hw07Report.starvationBlock(FairStarvationScenario.run(STARVATION_WORKERS, window))
                .forEach(console::raw);
    }

    /**
     * Окно на один вариант внутри этапа с двумя вариантами.
     *
     * <p>{@code observation-seconds} задаёт длительность этапа, а не каждого из двух его вариантов:
     * иначе полный быстрый прогон складывался бы из суммы окон всех сценариев и выходил за разумный
     * предел. Минимум 200 мс нужен, чтобы числа захватов и попыток оставались осмысленными.
     */
    static long perVariant(long stageWindowMillis) {
        return Math.max(200L, stageWindowMillis / 2);
    }

    /** Этап 4. Предотвращение: единый порядок захвата и захват с таймаутом. */
    private void stagePrevention() throws InterruptedException {
        console.raw("");
        console.raw("Этап 4. Предотвращение deadlock: единый порядок захвата и таймауты");
        OrderedLockingScenario.Outcome ordered =
                OrderedLockingScenario.run(PREVENTION_RUNS, PREVENTION_THREADS, PREVENTION_ITERATIONS);
        TimeoutLockingScenario.Outcome timeout = TimeoutLockingScenario.run();
        Hw07Report.preventionBlock(ordered, timeout).forEach(console::raw);
        console.raw("    живых потоков сценариев предотвращения: "
                + ThreadDump.liveThreadsWithPrefixes(OrderedLockingScenario.THREAD_PREFIX));
    }

    /** Демонстрация не должна менять список сценариев на лету: значения только для чтения тестами. */
    List<String> knownScenarios() {
        return List.of(ALL, DeadlockScenario.name(), LivelockScenario.NAME, StarvationScenario.NAME,
                OrderedLockingScenario.NAME);
    }
}
