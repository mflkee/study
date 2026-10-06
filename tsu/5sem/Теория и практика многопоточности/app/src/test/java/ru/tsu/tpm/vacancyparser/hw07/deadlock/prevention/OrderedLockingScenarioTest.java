package ru.tsu.tpm.vacancyparser.hw07.deadlock.prevention;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.SourceScan;

/**
 * Предотвращение взаимной блокировки единым порядком захвата ресурсов.
 */
class OrderedLockingScenarioTest {

    private static final int RUNS = 100;
    private static final int THREADS = 2;
    private static final int ITERATIONS = 50;

    @Test
    @Timeout(180)
    @DisplayName("100 прогонов: взаимной блокировки нет, инвариант выполнен, порядок захвата един")
    void orderedLockingPreventsDeadlock() throws InterruptedException {
        OrderedLockingScenario.Outcome outcome = OrderedLockingScenario.run(RUNS, THREADS, ITERATIONS);

        assertThat(outcome.anyDeadlock())
                .as("единый порядок захвата обязан исключать цикл ожидания")
                .isFalse();
        assertThat(outcome.actual()).isEqualTo(outcome.expected());
        assertThat(outcome.orders()).isEqualTo(Set.of("resource1->resource2"));
        assertThat(outcome.ordersConsistent()).isTrue();
        assertThat(outcome.secondLockWaits())
                .as("поток не должен ждать вторую блокировку, удерживая первую")
                .isZero();
        assertThat(outcome.conclusion()).contains("ДЗ 4").contains("взаимной блокировки нет");
    }

    @Test
    @DisplayName("Второй ресурс берётся без блокирующего ожидания: tryLock вместо lock")
    void secondResourceIsNotWaitedForUnderLock() {
        String code = SourceScan.readMain(
                "ru/tsu/tpm/vacancyparser/hw07/deadlock/prevention/OrderedLockingScenario.java");

        assertThat(code).contains("high.lock().tryLock()");
        assertThat(code)
                .as("блокирующий захват второго ресурса под первым создал бы вложенное ожидание")
                .doesNotContain("high.lock().lock()");
    }
}
