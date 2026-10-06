package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.io.IOException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.TimeBoxedExecution;

/**
 * Демонстрация ДЗ 6 «Volatile и atomic переменные».
 *
 * <p>Запускается только при {@code --app.hw=hw06} и идёт по этапам задания: остановка потока по
 * {@code volatile}-признаку, счётчик на {@code AtomicInteger}, singleton-кэш на {@code AtomicReference},
 * воспроизведение дефекта видимости и сравнительные замеры производительности.
 *
 * <p>Демонстрация целиком выполняется в ограниченном по времени окне (как в ДЗ 4): если сценарий
 * зависнет, процесс не станет ждать его вечно — печатается дамп потоков и работа завершается с
 * ненулевым кодом. Дефект видимости вынесен в отдельный процесс ({@link VisibilityDefectProbe}) на
 * то время, пока дочерний процесс живёт; в текущей JVM «зомби»-потоков не остаётся.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw06")
public class VolatileAtomicsDemo implements ApplicationRunner {

    /** Предел времени на всю демонстрацию. */
    static final long TIME_LIMIT_MILLIS = 120_000L;

    /** Код выхода, если демонстрация не уложилась в лимит. */
    static final int TIME_LIMIT_EXIT_CODE = 3;

    private static final int THREADS = AtomicsBenchmark.DEFAULT_THREADS;
    private static final int OPERATIONS_PER_THREAD = AtomicsBenchmark.DEFAULT_OPERATIONS;
    private static final int RUNS = AtomicsBenchmark.DEFAULT_RUNS;
    private static final int CACHE_THREADS = 16;

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;

    public VolatileAtomicsDemo(ConsoleOutput console, ConfigurableApplicationContext context) {
        this.console = console;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        console.section("ДЗ 6. Volatile и atomic переменные");
        console.raw("");
        console.raw("Предел времени на демонстрацию: " + TIME_LIMIT_MILLIS + " мс");
        console.raw("Процесс: pid " + ProcessHandle.current().pid() + ", " + ThreadDump.summary());

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
            console.info("Демонстрация завершена: поток остановлен volatile-флагом, счётчик точен, "
                    + "кэш создан один раз, дефект volatile воспроизведён, замеры выведены");
            console.raw("");
            console.raw(ThreadDump.summary());
            console.raw("живых потоков демонстрации после завершения: "
                    + ThreadDump.liveThreadsWithPrefixes("hw06-"));
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
            stageFlagStop();
            stageAtomicCounter();
            stageSingletonCache();
            stageVolatileVisibilityDefect();
            stageLoadAndPerformance();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("демонстрация прервана", e);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось запустить процесс демонстрации дефекта", e);
        }
    }

    /** Этап 1. Остановка потока по volatile-признаку завершения. */
    private void stageFlagStop() throws InterruptedException {
        console.raw("");
        console.raw("Этап 1. Остановка потока через volatile-флаг");
        console.raw("    конвенция флага: finished == false — поток работает, finished == true — завершиться "
                + "(обратная к привычному running)");
        FlagStopScenario.StopOutcome outcome = FlagStopScenario.runStoppable(FlagStopScenario.DEFAULT_TIMEOUT_MILLIS);
        AtomicsReport.flagStopBlock(outcome).forEach(console::raw);
    }

    /** Этап 2. Потокобезопасный счётчик на AtomicInteger. */
    private void stageAtomicCounter() throws InterruptedException {
        console.raw("");
        console.raw("Этап 2. Потокобезопасный счётчик на AtomicInteger");

        AtomicCounter counter = new AtomicCounter();
        long expected = (long) THREADS * OPERATIONS_PER_THREAD;
        long value = AtomicsBenchmark.runCounterInvariant(counter, THREADS, OPERATIONS_PER_THREAD);
        AtomicsReport.counterInvariantBlock(THREADS, OPERATIONS_PER_THREAD, value, expected).forEach(console::raw);
    }

    /** Этап 3. Singleton-кэш на AtomicReference: корректная реализация и учебный дефект. */
    private void stageSingletonCache() throws InterruptedException {
        console.raw("");
        console.raw("Этап 3. Singleton-кэш на AtomicReference");

        SingletonCache<Object> correct = new SingletonCache<>();
        CacheRaceRunner.RaceOutcome correctRace = CacheRaceRunner.run(correct, CACHE_THREADS);

        BrokenSingletonCache<Object> broken = new BrokenSingletonCache<>();
        CacheRaceRunner.RaceOutcome brokenRace = CacheRaceRunner.run(broken, CACHE_THREADS);

        AtomicsReport.cacheBlock(correctRace, brokenRace).forEach(console::raw);
    }

    /** Этап 4. Дефект видимости: признак без volatile в отдельном процессе. */
    private void stageVolatileVisibilityDefect() throws IOException, InterruptedException {
        console.raw("");
        console.raw("Этап 4. Воспроизведение дефекта: volatile даёт видимость, но не атомарность");
        VisibilityDefectProbe.DefectOutcome outcome = VisibilityDefectProbe.run();
        AtomicsReport.volatileDefectBlock(outcome).forEach(console::raw);
    }

    /** Этап 5. Нагрузочное тестирование по трём механизмам и сравнение с блокировкой. */
    private void stageLoadAndPerformance() throws InterruptedException {
        console.raw("");
        console.raw("Этап 5. Тестирование и производительность");

        AtomicsBenchmark.MechanismsOutcome mechanisms =
                AtomicsBenchmark.runMechanismsLoad(THREADS, OPERATIONS_PER_THREAD);
        AtomicsReport.mechanismsBlock(mechanisms).forEach(console::raw);

        AtomicsBenchmark.CounterComparison comparison =
                AtomicsBenchmark.compareCounters(THREADS, OPERATIONS_PER_THREAD, RUNS);
        AtomicsReport.counterComparisonBlock(comparison).forEach(console::raw);
        console.raw("");
        console.raw("    " + AtomicsReport.conclusion(comparison));
        console.raw("    корректные варианты (монитор и AtomicInteger) точны во всех прогонах: "
                + comparison.correctVariantsExact());
        console.raw("    " + AtomicsReport.volatileVerdict(comparison));
    }
}
