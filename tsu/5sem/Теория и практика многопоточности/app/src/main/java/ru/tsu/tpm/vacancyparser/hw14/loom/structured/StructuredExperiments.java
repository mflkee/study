package ru.tsu.tpm.vacancyparser.hw14.loom.structured;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.ConcurrencyMeter;

/**
 * Четыре опыта со {@link StructuredTaskScope} — пятый пункт задания.
 *
 * <p><b>Что такое structured concurrency.</b> Задача (единица работы) разбивается на подзадачи,
 * которые должны завершиться до того, как задача продолжит работу. Скоуп — это синтаксический блок
 * ({@code try-with-resources}): пока он не закрыт, наружу выйти нельзя. Отсюда три свойства, которых
 * нет у «сырых» потоков и пулов:
 *
 * <ul>
 *   <li><b>жизненный цикл ограничен блоком</b> — после выхода из {@code try} ни одна подзадача не
 *       работает, «потерянных» потоков не бывает;</li>
 *   <li><b>отмена распространяется</b> — {@code shutdown()} прерывает все незавершённые подзадачи, а
 *       {@code close()} дожидается их фактического завершения;</li>
 *   <li><b>ошибки собираются в одном месте</b> — политика ({@code ShutdownOnFailure},
 *       {@code ShutdownOnSuccess}) решает, что считать успехом группы, а вызывающий код получает уже
 *       готовый итог, а не разбирает десятки {@code Future}.</li>
 * </ul>
 *
 * <p>Четыре опыта отвечают на четыре вопроса:
 *
 * <ol>
 *   <li>{@link #runAll} — «все подзадачи успешны»: {@code join()} ждёт всю группу, результаты
 *       собираются после ожидания;</li>
 *   <li>{@link #runWithFailure} — «одна упала»: {@code ShutdownOnFailure} прерывает остальные,
 *       {@code throwIfFailed()} отдаёт причину; видно, что прогон длится не «полную» задержку, а
 *       время до сбоя;</li>
 *   <li>{@link #runWithTimeout} — «не уложились в срок»: {@code joinUntil(deadline)} бросает
 *       {@link TimeoutException}, после чего {@code shutdown()} снимает всё, что ещё работает;</li>
 *   <li>{@link #runFirstSuccess} — «нужен любой успешный ответ»: {@code ShutdownOnSuccess} отменяет
 *       дубли, как только один источник ответил (типичный приём для зеркал и повторов).</li>
 * </ol>
 *
 * <p>Все опыты запускают подзадачи на виртуальных потоках (фабрика
 * {@code Thread.ofVirtual().name("hw14-sts-", 0)}): скоуп умеет работать с любыми потоками, но
 * смысл в том, что десятки тысяч подзадач стоят копейки.
 *
 * <p><b>Версия API.</b> Используется API JDK 21 ({@code ShutdownOnFailure}, {@code joinUntil},
 * {@code shutdown}, {@code throwIfFailed}) — он же описан в тексте задания. В JDK 25+ тот же класс
 * переработан (JEP 505: {@code open(Joiner)}, {@code Configuration.withTimeout}) и всё ещё остаётся
 * preview, поэтому версия зафиксирована в {@code pom.xml}.
 */
public final class StructuredExperiments {

    /** Префикс имён потоков подзадач. */
    public static final String SUBTASK_PREFIX = "hw14-sts-";

    /** Итог опыта: все подзадачи дошли до результата. */
    public static final String OUTCOME_SUCCESS = "успех";

    /** Итог опыта: подзадача упала, остальные отменены. */
    public static final String OUTCOME_FAILURE = "исключение";

    /** Итог опыта: истёк срок, все незавершённые подзадачи отменены. */
    public static final String OUTCOME_TIMEOUT = "таймаут";

    /** Итог опыта: ожидание прервали снаружи. */
    public static final String OUTCOME_INTERRUPTED = "прервано";

    private StructuredExperiments() {
    }

    /**
     * Опыт 1: группа подзадач, все успешны.
     *
     * <p>{@code join()} ждёт завершения <b>всех</b> подзадач, {@code throwIfFailed()} ничего не
     * делает, если ни одна не упала, после чего результаты забираются из {@code Subtask.get()}.
     *
     * @param tasks       число подзадач
     * @param delayMillis сколько «работает» каждая подзадача
     */
    public static ScopeRun runAll(int tasks, long delayMillis) {
        if (tasks < 0) {
            throw new IllegalArgumentException("число подзадач не может быть отрицательным");
        }
        if (delayMillis < 0L) {
            throw new IllegalArgumentException("задержка не может быть отрицательной");
        }

        State[] states = newStates(tasks);
        List<String> results = Collections.synchronizedList(new ArrayList<>(tasks));
        ConcurrencyMeter meter = new ConcurrencyMeter();
        String outcome = OUTCOME_SUCCESS;
        String exceptionType = null;
        String exceptionMessage = null;

        long start = System.nanoTime();
        try (var scope = newScopeFailure("hw14-sts-all")) {
            for (int index = 0; index < tasks; index++) {
                int taskNumber = index;
                scope.fork(() -> subtask(taskNumber, states, meter, () -> {
                    sleep(delayMillis);
                    results.add("задача №" + taskNumber);
                    return taskNumber;
                }));
            }
            scope.join();
            scope.throwIfFailed();
        } catch (ExecutionException e) {
            outcome = OUTCOME_FAILURE;
            exceptionType = rootCause(e).getClass().getSimpleName();
            exceptionMessage = rootCause(e).getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = OUTCOME_INTERRUPTED;
            exceptionType = "InterruptedException";
            exceptionMessage = "ожидание группы прервано";
        }
        long wallNanos = System.nanoTime() - start;

        return build("все успешны", "ShutdownOnFailure", tasks, states, meter, wallNanos,
                outcome, exceptionType, exceptionMessage, results);
    }

    /**
     * Опыт 2: одна подзадача падает — остальные должны быть отменены.
     *
     * <p>Политика {@code ShutdownOnFailure} срабатывает при первом сбое: скоуп вызывает
     * {@code shutdown()}, незавершённые подзадачи получают {@code InterruptedException}, а
     * {@code throwIfFailed()} отдаёт {@link ExecutionException} с исходной причиной. Падение
     * происходит раньше, чем остальные успевают досчитать, поэтому прогон короткий — это и есть
     * доказательство отмены.
     *
     * @param tasks           число подзадач
     * @param delayMillis     сколько «работает» обычная подзадача
     * @param failingTask     номер подзадачи, которая упадёт
     * @param failAfterMillis через сколько миллисекунд она упадёт
     */
    public static ScopeRun runWithFailure(
            int tasks, long delayMillis, int failingTask, long failAfterMillis) {
        if (tasks <= 0) {
            throw new IllegalArgumentException("число подзадач должно быть положительным");
        }
        if (delayMillis < 0L || failAfterMillis < 0L) {
            throw new IllegalArgumentException("задержки не могут быть отрицательными");
        }
        if (failingTask < 0 || failingTask >= tasks) {
            throw new IllegalArgumentException("номер падающей подзадачи вне диапазона: " + failingTask);
        }

        State[] states = newStates(tasks);
        List<String> results = Collections.synchronizedList(new ArrayList<>(tasks));
        ConcurrencyMeter meter = new ConcurrencyMeter();
        String outcome = OUTCOME_SUCCESS;
        String exceptionType = null;
        String exceptionMessage = null;

        long start = System.nanoTime();
        try (var scope = newScopeFailure("hw14-sts-failure")) {
            for (int index = 0; index < tasks; index++) {
                int taskNumber = index;
                scope.fork(() -> subtask(taskNumber, states, meter, () -> {
                    if (taskNumber == failingTask) {
                        sleep(failAfterMillis);
                        throw new IllegalStateException(
                                "источник недоступен: подзадача №" + taskNumber);
                    }
                    sleep(delayMillis);
                    results.add("задача №" + taskNumber);
                    return taskNumber;
                }));
            }
            scope.join();
            scope.throwIfFailed();
        } catch (ExecutionException e) {
            outcome = OUTCOME_FAILURE;
            exceptionType = rootCause(e).getClass().getSimpleName();
            exceptionMessage = rootCause(e).getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = OUTCOME_INTERRUPTED;
            exceptionType = "InterruptedException";
            exceptionMessage = "ожидание группы прервано";
        }
        long wallNanos = System.nanoTime() - start;

        return build("одна падает", "ShutdownOnFailure", tasks, states, meter, wallNanos,
                outcome, exceptionType, exceptionMessage, results);
    }

    /**
     * Опыт 3: срок истёк — отмена по таймауту.
     *
     * <p>{@code joinUntil(deadline)} ждёт группу не дольше указанного момента и бросает
     * {@link TimeoutException}. Сам скоуп при этом <b>не</b> останавливает подзадачи — отмена
     * выполняется явно: {@code shutdown()} прерывает незавершённые, {@code close()} (его вызовет
     * {@code try-with-resources}) дожидается, пока они действительно закончат.
     *
     * @param tasks            число подзадач
     * @param taskDelayMillis  сколько «работает» каждая подзадача (заведомо больше срока)
     * @param timeoutMillis    срок ожидания группы
     */
    public static ScopeRun runWithTimeout(int tasks, long taskDelayMillis, long timeoutMillis) {
        if (tasks <= 0) {
            throw new IllegalArgumentException("число подзадач должно быть положительным");
        }
        if (taskDelayMillis < 0L) {
            throw new IllegalArgumentException("задержка не может быть отрицательной");
        }
        if (timeoutMillis <= 0L) {
            throw new IllegalArgumentException("срок ожидания должен быть положительным");
        }

        State[] states = newStates(tasks);
        List<String> results = Collections.synchronizedList(new ArrayList<>(tasks));
        ConcurrencyMeter meter = new ConcurrencyMeter();
        String outcome = OUTCOME_SUCCESS;
        String exceptionType = null;
        String exceptionMessage = null;
        Instant deadline = Instant.now().plusMillis(timeoutMillis);

        long start = System.nanoTime();
        try (var scope = newScopeFailure("hw14-sts-timeout")) {
            for (int index = 0; index < tasks; index++) {
                int taskNumber = index;
                scope.fork(() -> subtask(taskNumber, states, meter, () -> {
                    sleep(taskDelayMillis);
                    results.add("задача №" + taskNumber);
                    return taskNumber;
                }));
            }
            try {
                scope.joinUntil(deadline);
                scope.throwIfFailed();
            } catch (TimeoutException e) {
                outcome = OUTCOME_TIMEOUT;
                exceptionType = "TimeoutException";
                exceptionMessage = "группа не уложилась в " + timeoutMillis + " мс";
                scope.shutdown();
            }
        } catch (ExecutionException e) {
            outcome = OUTCOME_FAILURE;
            exceptionType = rootCause(e).getClass().getSimpleName();
            exceptionMessage = rootCause(e).getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = OUTCOME_INTERRUPTED;
            exceptionType = "InterruptedException";
            exceptionMessage = "ожидание группы прервано";
        }
        long wallNanos = System.nanoTime() - start;

        return build("таймаут", "ShutdownOnFailure", tasks, states, meter, wallNanos,
                outcome, exceptionType, exceptionMessage, results);
    }

    /**
     * Опыт 4: нужен первый успешный ответ, остальные источники отменяются.
     *
     * <p>Классический приём «повтор по зеркалам»: пока один источник отвечает за 150 мс, два других
     * уже запущены, но как только пришёл первый результат, {@code ShutdownOnSuccess} вызывает
     * {@code shutdown()} и они прерываются. Прогон длится столько, сколько работал самый быстрый, а не
     * самый медленный.
     *
     * @param delaysMillis задержка каждого источника, миллисекунды
     */
    public static ScopeRun runFirstSuccess(List<Long> delaysMillis) {
        if (delaysMillis == null || delaysMillis.isEmpty()) {
            throw new IllegalArgumentException("нужен хотя бы один источник");
        }
        int replicas = delaysMillis.size();
        for (Long delay : delaysMillis) {
            if (delay == null || delay < 0L) {
                throw new IllegalArgumentException("задержка источника не может быть отрицательной");
            }
        }

        State[] states = newStates(replicas);
        List<String> results = Collections.synchronizedList(new ArrayList<>(1));
        ConcurrencyMeter meter = new ConcurrencyMeter();
        String outcome = OUTCOME_SUCCESS;
        String exceptionType = null;
        String exceptionMessage = null;

        long start = System.nanoTime();
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>(
                "hw14-sts-first", Thread.ofVirtual().name(SUBTASK_PREFIX, 0).factory())) {
            for (int index = 0; index < replicas; index++) {
                int sourceNumber = index;
                long delay = delaysMillis.get(index);
                scope.fork(() -> subtask(sourceNumber, states, meter, () -> {
                    sleep(delay);
                    String answer = "источник №" + sourceNumber + " (ответ через " + delay + " мс)";
                    results.add(answer);
                    return answer;
                }));
            }
            scope.join();
            results.clear();
            results.add(scope.result());
        } catch (ExecutionException e) {
            outcome = OUTCOME_FAILURE;
            exceptionType = rootCause(e).getClass().getSimpleName();
            exceptionMessage = rootCause(e).getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = OUTCOME_INTERRUPTED;
            exceptionType = "InterruptedException";
            exceptionMessage = "ожидание группы прервано";
        }
        long wallNanos = System.nanoTime() - start;

        return build("первый успех", "ShutdownOnSuccess", replicas, states, meter, wallNanos,
                outcome, exceptionType, exceptionMessage, results);
    }

    private static StructuredTaskScope.ShutdownOnFailure newScopeFailure(String name) {
        return new StructuredTaskScope.ShutdownOnFailure(
                name, Thread.ofVirtual().name(SUBTASK_PREFIX, 0).factory());
    }

    /**
     * Тело подзадачи: учёт одновременности и единая запись состояния.
     *
     * <p>Состояние пишет сама подзадача, а читает владелец скоупа уже после {@code join()}/{@code close()}:
     * к этому моменту все потоки группы завершены, поэтому дополнительной синхронизации не нужно.
     */
    private static <T> T subtask(
            int index, State[] states, ConcurrencyMeter meter, Callable<T> body) {
        meter.enter(Thread.currentThread());
        try {
            T value = body.call();
            states[index] = State.SUCCESS;
            return value;
        } catch (InterruptedException | CancellationException e) {
            states[index] = State.CANCELLED;
            Thread.currentThread().interrupt();
            throw new CancellationException("подзадача №" + index + " отменена");
        } catch (Exception e) {
            states[index] = State.FAILED;
            throw e instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("подзадача №" + index + " упала", e);
        } finally {
            meter.exit();
        }
    }

    private static ScopeRun build(
            String label,
            String policy,
            int tasks,
            State[] states,
            ConcurrencyMeter meter,
            long wallNanos,
            String outcome,
            String exceptionType,
            String exceptionMessage,
            List<String> results) {
        int succeeded = 0;
        int failed = 0;
        for (State state : states) {
            switch (state) {
                case SUCCESS -> succeeded++;
                case FAILED -> failed++;
                case CANCELLED, PENDING -> { }
            }
        }
        // Отменённые — это всё, что не дало ни результата, ни исключения: подзадача, прерванная
        // shutdown(), остаётся в состоянии CANCELLED, а не успевшая стартовать — в PENDING.
        // Считаем остаток, чтобы счётчики всегда сходились с числом подзадач.
        int cancelled = tasks - succeeded - failed;
        return new ScopeRun(
                label,
                policy,
                tasks,
                succeeded,
                failed,
                cancelled,
                wallNanos,
                meter.peak(),
                meter.virtualExecutions(),
                meter.platformExecutions(),
                outcome,
                exceptionType,
                exceptionMessage,
                List.copyOf(results));
    }

    private static State[] newStates(int tasks) {
        State[] states = new State[tasks];
        Arrays.fill(states, State.PENDING);
        return states;
    }

    private static void sleep(long millis) throws InterruptedException {
        if (millis > 0L) {
            Thread.sleep(millis);
        } else {
            Thread.yield();
        }
    }

    /** Причина {@link ExecutionException} без обёрток; для {@code null} — сам {@code null}. */
    public static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** Имена потоков подзадач можно проверить по префиксу — служебный метод для тестов и отчёта. */
    public static boolean isSubtaskThread(Thread thread) {
        return thread != null && thread.getName().startsWith(SUBTASK_PREFIX);
    }

    /** Состояние подзадачи внутри скоупа. */
    private enum State {
        /** Ещё не закончила (или была отменена). */
        PENDING,
        /** Вернула результат. */
        SUCCESS,
        /** Упала с исключением. */
        FAILED,
        /** Прервана отменой скоупа. */
        CANCELLED
    }
}
