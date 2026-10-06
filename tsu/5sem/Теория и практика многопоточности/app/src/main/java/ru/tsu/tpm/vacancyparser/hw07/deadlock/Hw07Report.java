package ru.tsu.tpm.vacancyparser.hw07.deadlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.tsu.tpm.vacancyparser.common.ThreadDumpParser;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.deadlock.DeadlockRig;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.deadlock.DeadlockScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.livelock.LivelockScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.prevention.OrderedLockingScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.prevention.TimeoutLockingScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.starvation.StarvationScenario;

/**
 * Формирование вывода ДЗ 7.
 *
 * <p>Как и в ДЗ 3–6, печать отделена от сценариев: строки — чистые функции от результатов, поэтому их
 * можно проверить, не запуская сценарии.
 */
public final class Hw07Report {

    private Hw07Report() {
    }

    /** Вывод по взаимной блокировке: подтверждение, дамп, разбор, освобождение. */
    public static List<String> deadlockBlock(DeadlockScenario.Outcome outcome, long pid) {
        List<String> lines = new ArrayList<>();
        lines.add("    процесс: pid " + pid);
        lines.add("    взаимное ожидание подтверждено JVM: " + marker(outcome.deadlocked())
                + ", заблокированные потоки: " + outcome.jvmThreadNames());
        lines.add(String.format(
                Locale.ROOT, "    состояние удерживалось: %d мс", outcome.holdMillis()));
        if (outcome.dumpCaptured()) {
            lines.add("    дамп потоков сохранён: " + outcome.dumpPath());
        } else {
            lines.add("    дамп потоков снять не удалось; ручная команда: jstack <pid> или kill -3");
        }
        ThreadDumpParser.DeadlockReport report = outcome.report();
        lines.add("    разбор дампа: " + report.summary());
        for (ThreadDumpParser.Wait wait : report.waits()) {
            lines.add("        поток " + wait.thread() + " ожидает " + wait.resource()
                    + (wait.heldBy() == null ? "" : ", ресурс удерживает " + wait.heldBy()));
        }
        lines.add("    освобождение: потоки завершены = " + marker(outcome.released())
                + ", живые потоки после освобождения = " + outcome.liveThreads());
        return lines;
    }

    /** Вывод по livelock: попытки, успехи, вердикт детектора, состояния потоков. */
    public static List<String> livelockBlock(LivelockScenario.Outcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add("    конфигурация: " + outcome.config().label()
                + ", окно наблюдения: " + outcome.windowMillis() + " мс");
        for (int i = 0; i < outcome.attemptsPerWorker().size(); i++) {
            lines.add("        поток " + LivelockScenario.THREAD_PREFIX + (i + 1)
                    + ": попыток " + outcome.attemptsPerWorker().get(i));
        }
        lines.add("    успешных итераций: " + outcome.successes() + ", всего попыток: " + outcome.totalAttempts());
        lines.add("    детектор отсутствия продвижения: "
                + (outcome.verdict().livelock() ? "LIVELOCK" : "нет"));
        lines.add("        обоснование: " + outcome.verdict().reason());
        lines.add("    состояния потоков во время окна: " + outcome.threadStates()
                + " (RUNNABLE, блокировки не удерживаются)");
        return lines;
    }

    /** Вывод по starvation: числа захватов и пояснение, почему вариант некорректен или корректен. */
    public static List<String> starvationBlock(StarvationScenario.Outcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add("    механизм: " + (outcome.fair() ? "честная блокировка" : "несправедливый захват")
                + ", потоков: " + outcome.workers() + ", окно: " + outcome.windowMillis() + " мс");
        for (int i = 0; i < outcome.workerNames().size(); i++) {
            lines.add("        " + outcome.workerNames().get(i) + ": захватов " + outcome.acquisitions().get(i));
        }
        lines.add(String.format(
                Locale.ROOT,
                "    минимум/максимум захватов: %d / %d, неравенство доступа: %s",
                outcome.min(), outcome.max(), marker(outcome.unequal())));
        if (outcome.fair()) {
            lines.add("    гарантированный доступ жертвы: захватов " + outcome.victimAcquisitions()
                    + " (требуется не меньше " + StarvationScenario.FAIR_MIN_VICTIM_ACQUISITIONS + ")");
        } else {
            lines.add("    захватов у жертвы: " + outcome.victimAcquisitions());
        }
        lines.add("    " + outcome.caveat());
        return lines;
    }

    /** Вывод по предотвращению: единый порядок захвата и захват с таймаутом. */
    public static List<String> preventionBlock(
            OrderedLockingScenario.Outcome ordered, TimeoutLockingScenario.Outcome timeout) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(
                Locale.ROOT,
                "    единый порядок захвата: %d прогонов × %d потоков × %d итераций",
                ordered.runs(), ordered.threads(), ordered.iterations()));
        lines.add("        взаимная блокировка: " + (ordered.anyDeadlock() ? "ОБНАРУЖЕНА" : "не обнаружена")
                + ", порядок захвата: " + ordered.orders() + ", одинаков во всех прогонах: "
                + marker(ordered.ordersConsistent()));
        lines.add("        счётчик: " + ordered.actual() + " из " + ordered.expected());
        lines.add("    " + ordered.conclusion());
        lines.add(String.format(
                Locale.ROOT,
                "    захват с таймаутом: ресурс занят → таймаут через %d мс (лимит %d мс), "
                        + "ресурс после сценария свободен: %s, держатель завершился: %s",
                timeout.waitedMillis(),
                TimeoutLockingScenario.TIMEOUT_MILLIS,
                marker(timeout.resourceReleased()),
                marker(timeout.holderFinished())));
        lines.add("    " + timeoutExplanation());
        return lines;
    }

    /** Пояснение к сценарию захвата с таймаутом. */
    public static String timeoutExplanation() {
        return "таймаут вместо бесконечного ожидания: пока ресурс занят, поток не зависает и не удерживает чужих ресурсов";
    }

    /** Пояснение, как имена потоков сценария связаны с циклом из дампа. */
    public static String nameConsistencyHint() {
        return "имена потоков в разборе дампа обязаны совпадать с именами сценария: "
                + DeadlockRig.THREAD_A + ", " + DeadlockRig.THREAD_B;
    }

    private static String marker(boolean value) {
        return value ? "да" : "НЕТ";
    }
}
