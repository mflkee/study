package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

/**
 * Проверки сценария наблюдения целиком: порядок фаз, полнота набора состояний, безопасное
 * завершение сломанного сценария и пригодность вывода для отчёта.
 */
class LifecycleObservationServiceTest {

    /** Значения по умолчанию, но с короткими таймаутами — тесты не должны ждать по 5 секунд. */
    /**
     * Короткие, но с запасом тайминги.
     *
     * <p>Сон в 1.2 с и предел фазы в 4 с выбраны намеренно с большим запасом: наблюдатель ждёт
     * состояние `TIMED_WAITING` с дедлайном, и при узком запасе (0.4 с сна) в нагруженном прогоне
     * всей сборки поток успевал проснуться раньше, чем наблюдатель его заставал. Тогда набор
     * состояний отличался между запусками, и проверка воспроизводимости падала не из-за дефекта
     * кода, а из-за слишком маленького запаса в самом тесте.
     */
    private static final LifecycleObservationService.Timing FAST =
            new LifecycleObservationService.Timing(1_200L, 4_000L);

    private ByteArrayOutputStream sink;
    private ThreadStateObserver observer;
    private LifecycleObservationService service;

    @BeforeEach
    void setUp() {
        sink = new ByteArrayOutputStream();
        ConsoleOutput console = new ConsoleOutput(new PrintStream(sink, true, StandardCharsets.UTF_8));
        observer = new ThreadStateObserver(console);
        service = new LifecycleObservationService(console, observer, FAST);
    }

    @Test
    @DisplayName("Штатный сценарий фиксирует все шесть состояний и признаётся успешным")
    void happyPathObservesAllSixStates() {
        LifecycleObservationService.Observation observation = service.observe();

        assertThat(observation.missingStates())
                .as("ни одно состояние не должно остаться незафиксированным")
                .isEmpty();
        assertThat(observation.unreached()).isEmpty();
        assertThat(observation.success()).isTrue();
        assertThat(observation.statesByThread()).containsOnlyKeys(
                "LifecycleStartWorker",
                "SleepingWorker",
                "WaitingWorker",
                "BlockedWorker",
                "MonitorHolderThread");
    }

    @Test
    @DisplayName("Каждое состояние зафиксировано у фактически достигнувшего его потока")
    void everyStateComesFromTheThreadThatReachedIt() {
        LifecycleObservationService.Observation observation = service.observe();

        assertThat(observation.statesByThread().get("SleepingWorker"))
                .as("TIMED_WAITING даёт только Thread.sleep()")
                .contains(Thread.State.TIMED_WAITING)
                .doesNotContain(Thread.State.BLOCKED);
        assertThat(observation.statesByThread().get("WaitingWorker"))
                .as("WAITING даёт только Object.wait() без таймаута")
                .contains(Thread.State.WAITING)
                .doesNotContain(Thread.State.TIMED_WAITING);
        assertThat(observation.statesByThread().get("BlockedWorker"))
                .as("BLOCKED даёт только вход в занятый монитор")
                .contains(Thread.State.BLOCKED)
                .doesNotContain(Thread.State.WAITING, Thread.State.TIMED_WAITING);
        assertThat(observation.statesByThread().get("LifecycleStartWorker"))
                .as("поток без фазы ожидания не приобретает лишних состояний")
                .containsExactlyInAnyOrder(
                        Thread.State.NEW, Thread.State.RUNNABLE, Thread.State.TERMINATED);
    }

    @Test
    @DisplayName("Наборы состояний демо-потоков попарно различаются")
    void stateSetsArePairwiseDifferent() {
        LifecycleObservationService.Observation observation = service.observe();

        List<Map.Entry<String, Set<Thread.State>>> demoThreads = observation.statesByThread().entrySet().stream()
                .filter(entry -> !entry.getKey().equals("MonitorHolderThread"))
                .toList();

        assertThat(demoThreads).as("четыре демо-потока, каждый со своим набором").hasSize(4);
        for (Map.Entry<String, Set<Thread.State>> entry : demoThreads) {
            assertThat(entry.getValue()).as("набор непуст: %s", entry.getKey()).isNotEmpty();
            assertThat(entry.getValue()).as("у потока %s должно быть NEW и TERMINATED", entry.getKey())
                    .contains(Thread.State.NEW, Thread.State.TERMINATED);
        }

        Set<List<String>> signatures = new HashSet<>();
        for (Map.Entry<String, Set<Thread.State>> entry : demoThreads) {
            List<String> signature = entry.getValue().stream().map(Enum::name).sorted().toList();
            assertThat(signatures.add(signature))
                    .as("набор состояний повторился у %s: %s", entry.getKey(), signature)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Все демо-потоки завершаются в TERMINATED")
    void allThreadsTerminate() {
        LifecycleObservationService.Observation observation = service.observe();

        assertThat(observation.transitions())
                .as("каждый поток обязан дойти до TERMINATED")
                .filteredOn(transition -> transition.current() == Thread.State.TERMINATED)
                .extracting(ThreadStateObserver.Transition::threadName)
            .containsExactlyInAnyOrder(
                    "LifecycleStartWorker",
                    "SleepingWorker",
                    "WaitingWorker",
                    "BlockedWorker",
                    "MonitorHolderThread");
    }

    @Test
    @DisplayName("NEW всех потоков зафиксирован раньше любого RUNNABLE")
    void allNewStatesPrecedeAnyRunnable() {
        List<ThreadStateObserver.Transition> transitions = service.observe().transitions();

        int lastNew = lastIndexOf(transitions, Thread.State.NEW);
        int firstRunnable = firstIndexOf(transitions, Thread.State.RUNNABLE);

        assertThat(lastNew).isNotNegative();
        assertThat(firstRunnable).isNotNegative();
        assertThat(lastNew)
                .as("start() нельзя вызывать до фиксации NEW, иначе состояние нечем наблюдать")
                .isLessThan(firstRunnable);
    }

    @Test
    @DisplayName("Прерванный сценарий завершается за конечное время и печатает предупреждение")
    void interruptedScenarioFinishesWithWarningInsteadOfHanging() {
        ConsoleOutput console = new ConsoleOutput(new PrintStream(sink, true, StandardCharsets.UTF_8));
        LifecycleObservationService service =
                new LifecycleObservationService(console, new ThreadStateObserver(console), FAST);

        // Прерываем наблюдателя до старта: ни одна фаза не дойдёт до конца, но демонстрация
        // обязана всё равно освободить потоки и завершиться, а не зависнуть.
        Thread.currentThread().interrupt();
        long startedAt = System.nanoTime();
        LifecycleObservationService.Observation observation;
        try {
            observation = service.observe();
        } finally {
            Thread.interrupted();
        }
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(elapsedMillis)
                .as("прерванный сценарий не должен ни зависать, ни ждать полные таймауты фаз")
                .isLessThan(FAST.phaseTimeoutMillis());
        assertThat(observation.unreached())
                .as("о прерывании должно быть сказано прямо, а не молча проглочено")
                .anySatisfy(warning -> assertThat(warning).contains("прервано"));
        assertThat(console.captured()).contains("ВНИМАНИЕ");
    }

    @Test
    @DisplayName("Прерванный сценарий освобождает наблюдателя от флагов прерывания")
    void interruptedScenarioStillReleasesEverything() {
        ConsoleOutput console = new ConsoleOutput(new PrintStream(sink, true, StandardCharsets.UTF_8));
        LifecycleObservationService service =
                new LifecycleObservationService(console, new ThreadStateObserver(console), FAST);

        Thread.currentThread().interrupt();
        try {
            service.observe();
            assertThat(Thread.currentThread().isInterrupted())
                    .as("прерывание обязано быть возвращено, а не проглочено")
                    .isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("Неполный набор состояний даёт неуспех и перечисляет недостающие")
    void incompleteSetSignalsFailureWithMissingStates() {
        ConsoleOutput console = new ConsoleOutput(new PrintStream(sink, true, StandardCharsets.UTF_8));
        ThreadStateObserver observer = new ThreadStateObserver(console);
        LifecycleObservationService service =
                new LifecycleObservationService(console, observer, FAST);

        Thread.currentThread().interrupt();
        LifecycleObservationService.Observation observation;
        try {
            observation = service.observe();
        } finally {
            Thread.interrupted();
        }

        assertThat(observation.success())
                .as("неполный набор — это неуспех демонстрации, а не допустимый результат")
                .isFalse();
        assertThat(observation.missingStates())
                .as("достигнуты только состояния, зафиксированные до прерывания")
                .isNotEmpty();
        assertThat(console.captured())
                .contains("Проверка полноты: не зафиксированы состояния");
    }

    /**
     * Строка перехода в выводе.
     *
     * @param threadName   имя потока
     * @param previous     предыдущее состояние либо {@code null} при первом наблюдении
     * @param current      новое состояние
     * @param millis       метка времени от начала наблюдения
     */
    private record PrintedTransition(String threadName, String previous, String current, String millis) {

        static PrintedTransition parse(String line) {
            Matcher matcher = LINE.matcher(line);
            if (!matcher.matches()) {
                throw new AssertionError("строка вывода не разобрана как переход: " + line);
            }
            // Группы регулярки идут в порядке «время, поток, предыдущее, новое», а поля записи —
            // в порядке «поток, предыдущее, новое, время»; соответствие задаётся явно.
            return new PrintedTransition(matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(1));
        }
    }

    /**
     * Разбор строки перехода: метка времени, имя потока, предыдущее состояние, новое состояние.
     *
     * <p>Время выровнено по правому краю пробелами, поэтому перед числом допускаются пробелы.
     * Предыдущее состояние — последняя группа до стрелки: у первого наблюдения это текст
     * «(не наблюдалось)» с пробелом внутри, поэтому группа не может быть «одним словом».
     */
    private static final Pattern LINE =
            Pattern.compile("^\\[ДЗ 2]\\s+\\+\\s*(\\S+) мс\\s+(\\S+)\\s+(.+?)\\s+→\\s+(\\S+)$");

    @Test
    @DisplayName("Все переходы напечатаны в хронологическом порядке и ни один не потерян")
    void allTransitionsArePrintedInOrder() {
        LifecycleObservationService.Observation observation = service.observe();

        List<PrintedTransition> printed = consoleText().lines()
                .filter(line -> line.startsWith("[ДЗ 2]"))
                .map(PrintedTransition::parse)
                .toList();

        assertThat(printed)
                .as("в выводе должно быть столько же строк перехода, сколько записей в журнале")
                .hasSameSizeAs(observation.transitions());

        for (int i = 0; i < printed.size(); i++) {
            PrintedTransition line = printed.get(i);
            ThreadStateObserver.Transition expected = observation.transitions().get(i);

            assertThat(line.threadName()).isEqualTo(expected.threadName());
            assertThat(line.current()).isEqualTo(expected.current().name());
            assertThat(line.previous())
                    .as("предыдущее состояние перехода %d должно совпадать с журналом", i)
                    .isEqualTo(expected.previousLabel());
            assertThat(line.millis())
                    .as("метка времени обязана печататься — по ней переходы читаются как хронология")
                    .matches("\\d+\\.\\d+");
        }
    }

    @Test
    @DisplayName("Метки времени в переходах не убывают")
    void printedTimestampsAreMonotonic() {
        service.observe();

        List<Double> times = consoleText().lines()
                .filter(line -> line.startsWith("[ДЗ 2]"))
                .map(line -> Double.parseDouble(PrintedTransition.parse(line).millis()))
                .toList();

        assertThat(times).isNotEmpty();
        for (int i = 1; i < times.size(); i++) {
            assertThat(times.get(i))
                    .as("время перехода %d не должно откатываться назад", i)
                    .isGreaterThanOrEqualTo(times.get(i - 1));
        }
    }

    @Test
    @DisplayName("Фазы демонстрации помечены в выводе и идут в заявленном порядке")
    void phasesAreAnnouncedInOrder() {
        service.observe();
        String output = consoleText();

        List<String> phases = List.of(
                "Фаза 1. Создание потоков и фиксация NEW",
                "Фаза 2. Запуск потоков и фиксация RUNNABLE",
                "Фаза 3a. Фиксация TIMED_WAITING",
                "Фаза 3b. Фиксация WAITING",
                "Фаза 3c. Фиксация BLOCKED",
                "Фаза 4. Освобождение ожидающих, фиксация RUNNABLE и завершение по join()",
                "Сводка наблюдённых состояний по потокам:");

        int previousIndex = -1;
        for (String phase : phases) {
            int index = output.indexOf(phase);
            assertThat(index).as("в выводе нет заголовка фазы: %s", phase).isGreaterThanOrEqualTo(0);
            assertThat(index).as("фаза %s напечатана не в своём порядке", phase).isGreaterThan(previousIndex);
            previousIndex = index;
        }
    }

    @Test
    @DisplayName("Сводка печатает набор каждого потока и итоговое объединение")
    void summaryPrintsPerThreadSetsAndUnion() {
        LifecycleObservationService.Observation observation = service.observe();
        String output = consoleText();

        for (Map.Entry<String, Set<Thread.State>> entry : observation.statesByThread().entrySet()) {
            assertThat(output).as("в сводке нет набора состояний потока %s", entry.getKey())
                    .contains(entry.getKey());
        }
        assertThat(output)
                .contains("Объединение по всем потокам: NEW, RUNNABLE, WAITING, BLOCKED, TIMED_WAITING, TERMINATED")
                .contains("Проверка полноты: зафиксированы все 6 состояний");
    }

    @Test
    @DisplayName("Каждый поток проходит через разные состояния — набор содержит не менее трёх")
    void everyThreadPassesThroughAtLeastThreeStates() {
        LifecycleObservationService.Observation observation = service.observe();

        observation.statesByThread().forEach((name, states) ->
                assertThat(states)
                        .as("поток %s должен пройти через несколько разных состояний, а не одно", name)
                        .hasSizeGreaterThanOrEqualTo(3));
    }

    @Test
    @DisplayName("Повторные запуски дают одинаковые наборы состояний")
    void repeatedRunsProduceIdenticalStateSets() {
        Map<String, Set<Thread.State>> first = signature(service.observe());
        Map<String, Set<Thread.State>> second = signature(
                newService().observe());
        Map<String, Set<Thread.State>> third = signature(newService().observe());

        assertThat(second).as("второй запуск должен дать тот же результат").isEqualTo(first);
        assertThat(third).as("третий запуск должен дать тот же результат").isEqualTo(first);
    }

    private LifecycleObservationService newService() {
        ConsoleOutput console = new ConsoleOutput(new PrintStream(sink, true, StandardCharsets.UTF_8));
        return new LifecycleObservationService(console, new ThreadStateObserver(console), FAST);
    }

    private static Map<String, Set<Thread.State>> signature(LifecycleObservationService.Observation observation) {
        return observation.statesByThread();
    }

    private String consoleText() {
        return sink.toString(StandardCharsets.UTF_8);
    }

    private static int firstIndexOf(List<ThreadStateObserver.Transition> transitions, Thread.State state) {
        for (int i = 0; i < transitions.size(); i++) {
            if (transitions.get(i).current() == state) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(List<ThreadStateObserver.Transition> transitions, Thread.State state) {
        for (int i = transitions.size() - 1; i >= 0; i--) {
            if (transitions.get(i).current() == state) {
                return i;
            }
        }
        return -1;
    }
}
