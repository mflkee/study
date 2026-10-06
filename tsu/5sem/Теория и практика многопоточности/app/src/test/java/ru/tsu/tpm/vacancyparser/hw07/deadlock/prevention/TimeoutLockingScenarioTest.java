package ru.tsu.tpm.vacancyparser.hw07.deadlock.prevention;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Захват с ограничением по времени: занятый ресурс превращается в явный отказ, а не в зависание.
 */
class TimeoutLockingScenarioTest {

    @Test
    @Timeout(30)
    @DisplayName("Занятый ресурс: таймаут, ресурс не удержан, держатель освободил и завершился")
    void timeoutReplacesInfiniteWait() throws InterruptedException {
        TimeoutLockingScenario.Outcome outcome = TimeoutLockingScenario.run();

        assertThat(outcome.timedOut()).isTrue();
        assertThat(outcome.waitedMillis())
                .as("ожидание ограничено таймаутом")
                .isGreaterThanOrEqualTo(100L)
                .isLessThan(5_000L);
        assertThat(outcome.resourceReleased())
                .as("после сценария ресурс свободен")
                .isTrue();
        assertThat(outcome.holderFinished()).isTrue();
    }
}
