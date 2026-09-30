package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

/**
 * Наблюдатель состояний потоков.
 *
 * <p>Делает три вещи:
 *
 * <ol>
 *   <li>запоминает последнее зафиксированное состояние каждого потока;</li>
 *   <li>печатает строку перехода <b>только при смене</b> состояния — повторные чтения того же
 *       состояния не засоряют хронологию;</li>
 *   <li>ведёт упорядоченный по времени журнал переходов и в финале печатает сводку
 *       «поток → множество наблюдённых состояний».</li>
 * </ol>
 *
 * <p><b>Единственная точка записи — {@link #observe(Thread)}.</b> Она читает фактическое
 * {@link Thread#getState()} и передаёт его в {@link #record(String, Thread.State)}. Сервис
 * наблюдения вызывает только её, поэтому в журнал попадают реально прочитанные значения.
 * Проверить это можно методом {@link #verifyAgainstActualStates()}, который сверяет
 * зафиксированное состояние с текущим {@code getState()} и перечисляет расхождения.
 */
@Component
public class ThreadStateObserver {

    /** Полный набор состояний потока, который демонстрация обязана закрыть. */
    public static final List<Thread.State> ALL_STATES = List.of(
            Thread.State.NEW,
            Thread.State.RUNNABLE,
            Thread.State.WAITING,
            Thread.State.BLOCKED,
            Thread.State.TIMED_WAITING,
            Thread.State.TERMINATED);

    private static final String NO_PREVIOUS = "— (не наблюдалось)";

    /**
     * Зафиксированный переход потока в новое состояние.
     *
     * @param threadName имя потока
     * @param previous   предыдущее состояние, {@code null} при первом наблюдении
     * @param current    новое состояние
     * @param at         момент перехода от начала наблюдения
     */
    public record Transition(String threadName, Thread.State previous, Thread.State current, Duration at) {

        public String previousLabel() {
            return previous == null ? NO_PREVIOUS : previous.name();
        }
    }

    private final ConsoleOutput console;
    private final long startNanos = System.nanoTime();

    private final Map<String, Thread> threadsByName = new LinkedHashMap<>();
    private final Map<String, Thread.State> lastStateByThread = new LinkedHashMap<>();
    private final Map<String, Set<Thread.State>> statesByThread = new LinkedHashMap<>();
    private final List<Transition> transitions = new ArrayList<>();

    public ThreadStateObserver(ConsoleOutput console) {
        this.console = console;
    }

    /** Запомнить поток, чтобы по нему можно было читать фактическое {@code getState()}. */
    public synchronized void register(Thread thread) {
        threadsByName.put(thread.getName(), thread);
    }

    /**
     * Наблюдение состояния потока — единственный путь, которым пользуется сервис.
     *
     * <p>Значение всегда берётся из {@link Thread#getState()} в момент вызова.
     */
    public synchronized void observe(Thread thread) {
        record(thread.getName(), thread.getState());
    }

    /**
     * Примитив записи состояния по имени потока.
     *
     * <p>Служит точкой сборки для {@link #observe(Thread)}. Значение обязано приходить из
     * {@code getState()}; записать произвольное состояние — значит внести в журнал то, чего
     * поток не достигал. Такой подменённый переход отлавливается методом
     * {@link #verifyAgainstActualStates()}.
     *
     * @return {@code true}, если состояние изменилось и переход попал в журнал
     */
    public synchronized boolean record(String threadName, Thread.State state) {
        Thread.State previous = lastStateByThread.get(threadName);
        if (previous == state) {
            return false;
        }

        Duration at = Duration.ofNanos(System.nanoTime() - startNanos);
        transitions.add(new Transition(threadName, previous, state, at));
        statesByThread.computeIfAbsent(threadName, key -> new LinkedHashSet<>()).add(state);
        lastStateByThread.put(threadName, state);
        console.raw(formatTransition(at, threadName, previous, state));
        return true;
    }

    /** Хронология зафиксированных переходов в порядке возникновения. */
    public synchronized List<Transition> transitions() {
        return List.copyOf(transitions);
    }

    /** Набор состояний, зафиксированных у конкретного потока. */
    public synchronized Set<Thread.State> statesOf(String threadName) {
        return Set.copyOf(statesByThread.getOrDefault(threadName, Set.of()));
    }

    /** Наборы состояний по всем наблюдавшимся потокам. */
    public synchronized Map<String, Set<Thread.State>> statesByThread() {
        Map<String, Set<Thread.State>> copy = new LinkedHashMap<>();
        statesByThread.forEach((name, states) -> copy.put(name, Set.copyOf(states)));
        return copy;
    }

    /** Объединение всех зафиксированных состояний по всем потокам. */
    public synchronized Set<Thread.State> allObservedStates() {
        Set<Thread.State> all = new LinkedHashSet<>();
        statesByThread.values().forEach(all::addAll);
        return all;
    }

    /** Состояния из полного набора шести, которых не зафиксировано ни у одного потока. */
    public synchronized List<Thread.State> missingStates() {
        Set<Thread.State> observed = allObservedStates();
        return ALL_STATES.stream().filter(state -> !observed.contains(state)).toList();
    }

    /**
     * Сверить зафиксированные состояния с фактическим {@code getState()} зарегистрированных потоков.
     *
     * @return описания расхождений; пустой список означает, что журнал не соврёт
     */
    public synchronized List<String> verifyAgainstActualStates() {
        List<String> mismatches = new ArrayList<>();
        lastStateByThread.forEach((name, recorded) -> {
            Thread thread = threadsByName.get(name);
            if (thread == null) {
                mismatches.add("поток " + name + " не зарегистрирован у наблюдателя");
                return;
            }
            Thread.State actual = thread.getState();
            if (actual != recorded) {
                mismatches.add("поток " + name + ": в журнале " + recorded + ", фактически " + actual);
            }
        });
        return List.copyOf(mismatches);
    }

    /** Напечатать сводку «поток → множество наблюдённых состояний». */
    public synchronized void printSummary() {
        console.raw("");
        console.raw("Сводка наблюдённых состояний по потокам:");
        statesByThread.forEach((name, states) -> console.raw("    " + name + " → " + formatStates(states)));
        console.raw("");
        console.raw("Объединение по всем потокам: " + formatStates(allObservedStates()));

        List<Thread.State> missing = missingStates();
        if (missing.isEmpty()) {
            console.info("Проверка полноты: зафиксированы все " + ALL_STATES.size() + " состояний");
        } else {
            console.warn("Проверка полноты: не зафиксированы состояния " + formatStates(new LinkedHashSet<>(missing)));
        }
    }

    /**
     * Перечислить состояния в каноническом порядке из текста задания
     * ({@code NEW} → {@code RUNNABLE} → {@code WAITING} → {@code BLOCKED} → {@code TIMED_WAITING}
     * → {@code TERMINATED}), а не по алфавиту: в отчёте порядок должен читаться как путь потока.
     */
    private static String formatStates(Set<Thread.State> states) {
        return ALL_STATES.stream()
                .filter(states::contains)
                .map(Enum::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("(пусто)");
    }

    private static String formatTransition(Duration at, String threadName, Thread.State previous, Thread.State current) {
        String previousLabel = previous == null ? NO_PREVIOUS : previous.name();
        return String.format(Locale.ROOT, "[ДЗ 2] +%9.3f мс  %-20s %-18s → %s",
                at.toNanos() / 1_000_000.0, threadName, previousLabel, current.name());
    }
}
