package ru.tsu.tpm.vacancyparser.hw07.deadlock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.common.ThreadDumpParser;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.deadlock.DeadlockScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.livelock.LivelockConfig;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.livelock.LivelockDetector;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.livelock.LivelockScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.prevention.OrderedLockingScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.prevention.TimeoutLockingScenario;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.starvation.StarvationScenario;

/**
 * Печать ДЗ 7: строки — чистые функции от результатов, поэтому проверяются без запуска сценариев.
 */
class Hw07ReportTest {

    @Test
    @DisplayName("Блок deadlock печатает PID, подтверждение, дамп, разбор и освобождение")
    void deadlockBlockPrintsEverything() {
        ThreadDumpParser.DeadlockReport report = new ThreadDumpParser.DeadlockReport(
                true,
                List.of("Deadlock-A", "Deadlock-B"),
                List.of(
                        new ThreadDumpParser.Wait("Deadlock-A", "ownable synchronizer resourceB", "Deadlock-B"),
                        new ThreadDumpParser.Wait("Deadlock-B", "ownable synchronizer resourceA", "Deadlock-A")),
                "Found one Java-level deadlock:");
        DeadlockScenario.Outcome outcome = new DeadlockScenario.Outcome(
                true, "Deadlock-A, Deadlock-B", true, "/tmp/hw07-deadlock.jstack", report, true, 123L, List.of());

        String text = String.join("\n", Hw07Report.deadlockBlock(outcome, 4242L));

        assertThat(text)
                .contains("pid 4242")
                .contains("взаимное ожидание подтверждено JVM: да")
                .contains("/tmp/hw07-deadlock.jstack")
                .contains("взаимная блокировка обнаружена")
                .contains("Deadlock-A ожидает")
                .contains("потоки завершены = да")
                .contains("живые потоки после освобождения = []");
    }

    @Test
    @DisplayName("Блок livelock печатает попытки по потокам, вердикт и состояния")
    void livelockBlockPrintsAttemptsAndVerdict() {
        LivelockScenario.Outcome outcome = new LivelockScenario.Outcome(
                LivelockConfig.LIVELOCK,
                3_000L,
                List.of(1_000L, 999L),
                0L,
                LivelockDetector.evaluate(1_999L, 0L),
                List.of("Livelock-1=RUNNABLE", "Livelock-2=RUNNABLE"),
                3_000_000_000L);

        String text = String.join("\n", Hw07Report.livelockBlock(outcome));

        assertThat(text)
                .contains("Livelock-1: попыток 1000")
                .contains("Livelock-2: попыток 999")
                .contains("успешных итераций: 0")
                .contains("LIVELOCK")
                .contains("RUNNABLE");
    }

    @Test
    @DisplayName("Блок starvation печатает захваты, неравенство и оговорку")
    void starvationBlockPrintsCountsAndCaveat() {
        StarvationScenario.Outcome unfair = new StarvationScenario.Outcome(
                false, 3, 2_000L,
                List.of("Starvation-victim", "Starvation-1", "Starvation-2"),
                List.of(0L, 500L, 480L), 0L, 0L, 500L,
                StarvationScenario.UNFAIR_CAVEAT);
        String unfairText = String.join("\n", Hw07Report.starvationBlock(unfair));
        assertThat(unfairText)
                .contains("несправедливый захват")
                .contains("Starvation-victim: захватов 0")
                .contains("НЕ гарантирован");

        StarvationScenario.Outcome fair = new StarvationScenario.Outcome(
                true, 3, 2_000L,
                List.of("Starvation-victim", "Starvation-1", "Starvation-2"),
                List.of(400L, 410L, 405L), 400L, 400L, 410L,
                StarvationScenario.FAIR_CAVEAT);
        String fairText = String.join("\n", Hw07Report.starvationBlock(fair));
        assertThat(fairText)
                .contains("честная блокировка")
                .contains("гарантированный доступ жертвы: захватов 400")
                .contains("требуется не меньше 20");
    }

    @Test
    @DisplayName("Блок предотвращения печатает порядок захвата и таймаут")
    void preventionBlockPrintsOrderAndTimeout() {
        OrderedLockingScenario.Outcome ordered = new OrderedLockingScenario.Outcome(
                100, 2, 50, false, 200L, 200L, Set.of("resource1->resource2"), true, 0L);
        TimeoutLockingScenario.Outcome timeout = new TimeoutLockingScenario.Outcome(true, 151L, true, true);

        String text = String.join("\n", Hw07Report.preventionBlock(ordered, timeout));

        assertThat(text)
                .contains("100 прогонов × 2 потоков × 50 итераций")
                .contains("взаимная блокировка: не обнаружена")
                .contains("порядок захвата: [resource1->resource2]")
                .contains("счётчик: 200 из 200")
                .contains("ДЗ 4")
                .contains("таймаут через 151 мс")
                .contains("ресурс после сценария свободен: да");
    }
}
