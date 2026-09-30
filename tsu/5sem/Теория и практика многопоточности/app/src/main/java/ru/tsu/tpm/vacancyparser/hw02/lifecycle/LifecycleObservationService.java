package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

/**
 * Сервис наблюдения за жизненным циклом потоков.
 *
 * <p>Ведёт демонстрацию по явным фазам (design D7), чтобы вывод читался как хронология и
 * годился для дословного приложения к отчёту:
 *
 * <ol>
 *   <li>создание всех потоков и фиксация {@code NEW} — до вызова {@code start()};</li>
 *   <li>запуск потоков и фиксация {@code RUNNABLE} внутри участка работы;</li>
 *   <li>последовательная фиксация {@code TIMED_WAITING} → {@code WAITING} → {@code BLOCKED};</li>
 *   <li>освобождение ожидающих, повторная фиксация {@code RUNNABLE} и завершение
 *       с {@code TERMINATED};</li>
 *   <li>сводка и проверка полноты набора из шести состояний.</li>
 * </ol>
 *
 * <p>Ни одна фаза не может заблокировать демонстрацию: у каждого ожидания есть таймаут, все
 * ожидающие стороны освобождаются в {@code finally}, а недостигнутое состояние попадает в
 * список предупреждений вместо того, чтобы повесить процесс.
 */
@Service
public class LifecycleObservationService {

    /**
     * Настройки демонстрации.
     *
     * @param sleepMillis        длительность сна {@link SleepingWorker}, удерживающего
     *                           {@code TIMED_WAITING}; запас относительно шага наблюдателя
     * @param phaseTimeoutMillis предел ожидания на каждой фазе
     */
    public record Timing(long sleepMillis, long phaseTimeoutMillis) {

        /** Значения по умолчанию: сон заметно длиннее шага наблюдателя, фазы не висят. */
        public static final Timing DEFAULT = new Timing(500L, 5_000L);
    }

    /**
     * Итог наблюдения.
     *
     * @param statesByThread наборы наблюдённых состояний по потокам
     * @param missingStates  состояния из полного набора, которых не зафиксировано
     * @param unreached      предупреждения: недостигнутые состояния и расхождения с фактом
     * @param transitions    хронология переходов
     */
    public record Observation(
            Map<String, Set<Thread.State>> statesByThread,
            List<Thread.State> missingStates,
            List<String> unreached,
            List<ThreadStateObserver.Transition> transitions) {

        public boolean success() {
            return missingStates.isEmpty() && unreached.isEmpty();
        }
    }

    private final ConsoleOutput console;
    private final ThreadStateObserver observer;
    private final Timing timing;

    /** Заголовок последней начатой фазы — попадает в предупреждение о прерванном сценарии. */
    private String currentPhase = "подготовка";

    @Autowired
    public LifecycleObservationService(ConsoleOutput console, ThreadStateObserver observer) {
        this(console, observer, Timing.DEFAULT);
    }

    public LifecycleObservationService(ConsoleOutput console, ThreadStateObserver observer, Timing timing) {
        this.console = console;
        this.observer = observer;
        this.timing = timing;
    }

    /**
     * Выполнить демонстрацию целиком и вернуть её итог.
     *
     * <p>Метод не бросает {@link InterruptedException} наружу: прерывание наблюдателя — это
     * прерванный сценарий, а не ошибка запуска. Фаза, на которой пришло прерывание, не
     * достигается и попадает в список предупреждений, но освобождение потоков выполняется в
     * любом случае, и сводка с проверкой полноты печатается как обычно. Итог тогда честно
     * неуспешен — набор состояний неполон.
     */
    public Observation observe() {
        console.section("ДЗ 2. Жизненный цикл потока");
        List<String> unreached = new ArrayList<>();

        long safety = timing.phaseTimeoutMillis();
        Object monitor = new Object();
        LifecycleStartWorker starter = new LifecycleStartWorker(
                "LifecycleStartWorker", window("старт", safety), window("финал", safety), safety);
        SleepingWorker sleeper = new SleepingWorker(
                "SleepingWorker", window("старт", safety), window("финал", safety), timing.sleepMillis(), safety);
        WaitingWorker waiter = new WaitingWorker(
                "WaitingWorker", window("старт", safety), window("финал", safety), safety);
        BlockedWorker blocked = new BlockedWorker(
                "BlockedWorker", window("старт", safety), window("финал", safety), monitor, safety);
        MonitorHolderThread holder = new MonitorHolderThread(
                "MonitorHolderThread", window("старт", safety), window("финал", safety), monitor, safety);

        List<LifecycleWorker> workers = List.of(starter, sleeper, waiter, blocked, holder);
        List<RunnableWindow> workWindows = workers.stream().map(LifecycleWorker::workWindow).toList();
        List<RunnableWindow> finishWindows = workers.stream().map(LifecycleWorker::finishWindow).toList();

        boolean interrupted = false;
        try {
            phase1RegisterNew(workers);
            phase2CaptureRunnable(workers, workWindows, unreached);
            phase3aCaptureTimedWaiting(sleeper, unreached);
            phase3bCaptureWaiting(waiter, unreached);
            phase3cCaptureBlocked(holder, blocked, unreached);
            phase4ReleaseAndTerminate(workers, waiter, holder, blocked, finishWindows, unreached);
        } catch (InterruptedException e) {
            // Прерванный сценарий: недостигнутые состояния уже перечислены фазами, а потоки
            // освобождаются в finally. Продолжать фазы после прерывания бессмысленно —
            // каждая следующая немедленно упрётся в прерывание.
            interrupted = true;
            unreached.add("наблюдение прервано на фазе «" + currentPhase + "», сценарий не завершён");
            Thread.currentThread().interrupt();
        } finally {
            releaseEverything(waiter, holder);
        }

        if (interrupted) {
            // Набор состояний незавершённого сценария сверять с фактом бессмысленно: потоки ещё
            // могут дойти до TERMINATED после возврата, и расхождение ничего не скажет о журнале.
            console.warn("наблюдение прервано, сводка неполная — проверка соответствия журнала факту пропущена");
        } else {
            unreached.addAll(observer.verifyAgainstActualStates());
        }
        observer.printSummary();

        return new Observation(
                observer.statesByThread(),
                observer.missingStates(),
                List.copyOf(unreached),
                observer.transitions());
    }

    /**
     * Объявить начало фазы: напечатать заголовок и запомнить его для предупреждения о прерывании.
     */
    private void beginPhase(String title) {
        currentPhase = title;
        console.raw("");
        console.raw(title);
    }

    /** Фаза 1: потоки созданы, но ещё не запущены — состояние каждого {@code NEW}. */
    private void phase1RegisterNew(List<LifecycleWorker> workers) {
        beginPhase("Фаза 1. Создание потоков и фиксация NEW (start() ещё не вызван)");
        workers.forEach(worker -> {
            observer.register(worker);
            observer.observe(worker);
        });
    }

    /**
     * Фаза 2: запуск и фиксация {@code RUNNABLE} внутри участка работы.
     *
     * <p>Сначала дожидаемся, что <b>все</b> потоки вошли в работу, и лишь потом читаем состояния
     * и открываем затворы. Порядок важен: если открывать затворы по одному, поток успеет дойти до
     * своей фазы ожидания раньше, чем наблюдатель дойдёт до конца фазы.
     */
    private void phase2CaptureRunnable(
            List<LifecycleWorker> workers, List<RunnableWindow> workWindows, List<String> unreached)
            throws InterruptedException {
        beginPhase("Фаза 2. Запуск потоков и фиксация RUNNABLE в участке работы");

        workers.forEach(Thread::start);

        for (RunnableWindow window : workWindows) {
            if (!window.awaitStarted(timing.phaseTimeoutMillis())) {
                unreached.add("поток не вошёл в участок работы за " + timing.phaseTimeoutMillis() + " мс");
            }
        }
        workers.forEach(observer::observe);
        workWindows.forEach(RunnableWindow::open);
    }

    /** Фаза 3a: {@code Thread.sleep} удерживает {@code TIMED_WAITING}. */
    private void phase3aCaptureTimedWaiting(SleepingWorker sleeper, List<String> unreached)
            throws InterruptedException {
        beginPhase("Фаза 3a. Фиксация TIMED_WAITING — поток спит через Thread.sleep()");

        if (!sleeper.awaitAboutToSleep(timing.phaseTimeoutMillis())) {
            unreached.add("поток " + sleeper.getName() + " не дошёл до Thread.sleep()");
            return;
        }
        if (awaitState(sleeper, Thread.State.TIMED_WAITING)) {
            observer.observe(sleeper);
        } else {
            unreached.add("у потока " + sleeper.getName() + " не зафиксировано состояние TIMED_WAITING");
        }
    }

    /**
     * Фаза 3b: {@link Object#wait()} без таймаута удерживает {@code WAITING}.
     *
     * <p>Состояние читается под тем же монитором, что и {@code wait()} самого потока, — это
     * закрывает гонку с переходом и не полагается на таймаут.
     */
    private void phase3bCaptureWaiting(WaitingWorker waiter, List<String> unreached)
            throws InterruptedException {
        beginPhase("Фаза 3b. Фиксация WAITING — поток ждёт в Object.wait() без таймаута");

        if (!waiter.awaitAboutToWait(timing.phaseTimeoutMillis())) {
            unreached.add("поток " + waiter.getName() + " не дошёл до Object.wait()");
            return;
        }
        waiter.stateInsideMonitor();
        observer.observe(waiter);
    }

    /**
     * Фаза 3c: contended-монитор даёт {@code BLOCKED}.
     *
     * <p>Порядок сигналов и есть защита от гонки: сначала подтверждаем, что держатель внутри
     * секции и монитор захвачен, и только потом разрешаем претенденту попытаться войти.
     */
    private void phase3cCaptureBlocked(
            MonitorHolderThread holder, BlockedWorker blocked, List<String> unreached)
            throws InterruptedException {
        beginPhase("Фаза 3c. Фиксация BLOCKED — претендент ждёт входа в занятый монитор");

        holder.awaitSectionRequested(timing.phaseTimeoutMillis());
        holder.grantSectionPermit();

        if (!holder.awaitInsideSection(timing.phaseTimeoutMillis())) {
            unreached.add("поток " + holder.getName() + " не вошёл в секцию, монитор не удерживается");
            return;
        }
        if (!holder.awaitWaitingInside(timing.phaseTimeoutMillis())) {
            unreached.add("поток " + holder.getName() + " не дошёл до ожидания внутри секции");
            return;
        }
        observer.observe(holder);

        if (!blocked.awaitEntryRequested(timing.phaseTimeoutMillis())) {
            unreached.add("поток " + blocked.getName() + " не дошёл до попытки входа в секцию");
            return;
        }
        blocked.grantEntryPermit();

        if (awaitState(blocked, Thread.State.BLOCKED)) {
            observer.observe(blocked);
        } else {
            unreached.add("у потока " + blocked.getName() + " не зафиксировано состояние BLOCKED");
        }
    }

    /**
     * Фаза 4: освобождение, возврат в {@code RUNNABLE} и завершение с {@code TERMINATED}.
     *
     * <p>Ожидающие просыпаются, попадают в финальное окно работы, где снова читается
     * {@code RUNNABLE}; после открытия затворов потоки дорабатывают и завершаются. Состояние
     * {@code TERMINATED} читается после {@code join()} — единственный способ гарантировать его,
     * а не надеяться, что поток уже завершился.
     */
    private void phase4ReleaseAndTerminate(
            List<LifecycleWorker> workers,
            WaitingWorker waiter,
            MonitorHolderThread holder,
            BlockedWorker blocked,
            List<RunnableWindow> finishWindows,
            List<String> unreached)
            throws InterruptedException {
        beginPhase("Фаза 4. Освобождение ожидающих, фиксация RUNNABLE и завершение по join()");

        waiter.release();
        holder.release();
        blocked.awaitEnteredSection(timing.phaseTimeoutMillis());

        for (RunnableWindow window : finishWindows) {
            if (!window.awaitStarted(timing.phaseTimeoutMillis())) {
                unreached.add("поток не дошёл до финального участка работы за "
                        + timing.phaseTimeoutMillis() + " мс");
            }
        }
        workers.forEach(observer::observe);
        finishWindows.forEach(RunnableWindow::open);

        for (LifecycleWorker worker : workers) {
            worker.join(timing.phaseTimeoutMillis());
            if (worker.isAlive()) {
                unreached.add("поток " + worker.getName() + " не завершился за " + timing.phaseTimeoutMillis() + " мс");
            }
        }
        workers.forEach(observer::observe);
    }

    /**
     * Гарантированное освобождение всех ожидающих сторон.
     *
     * <p>Вызывается из {@code finally}: даже если фаза сорвалась, ни один поток не остаётся
     * удерживающим монитор или спящим на latch'е — иначе демо повесило бы приложение.
     */
    private void releaseEverything(WaitingWorker waiter, MonitorHolderThread holder) {
        waiter.release();
        holder.release();
    }

    /**
     * Дождаться, пока поток окажется в ожидаемом состоянии.
     *
     * <p>Это не перебор в цикле, каким обычно пробуют «поймать» состояния: состояние уже
     * удерживается гарантированно (поток внутри {@code synchronized}, в {@code wait()} или в
     * {@code sleep()}), а ожидание лишь узнаёт, в какой момент оно наступило. Предел времени
     * обязателен — иначе сломанный сценарий вместо предупреждения дал бы зависание.
     */
    private boolean awaitState(Thread thread, Thread.State expected) throws InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(timing.phaseTimeoutMillis());
        while (System.nanoTime() < deadline) {
            if (thread.getState() == expected) {
                return true;
            }
            Thread.onSpinWait();
        }
        return thread.getState() == expected;
    }

    private RunnableWindow window(String label, long safetyMillis) {
        return new RunnableWindow(label, safetyMillis);
    }

    /** Наблюдатель, накопленный данным сервиса, — доступен тестам и отладке. */
    public ThreadStateObserver observer() {
        return observer;
    }
}
