package ru.tsu.tpm.vacancyparser.hw09.callable.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Периодическая задача на ScheduledExecutorService: повторяется и переживает сбой одного выполнения.
 */
class BackgroundMonitorTest {

    @Test
    @Timeout(30)
    @DisplayName("Периодическая задача выполняется многократно, счётчик циклов растёт")
    void repeatsMultipleTimes() throws InterruptedException {
        BackgroundMonitor monitor = new BackgroundMonitor("hw09-test-monitor-");
        try {
            monitor.start(20L);
            assertThat(monitor.awaitCycles(3, 3_000L)).isTrue();
            assertThat(monitor.cycles()).isGreaterThanOrEqualTo(3L);
            assertThat(monitor.failures()).isZero();
        } finally {
            monitor.stop(2_000L);
        }
        assertThat(monitor.isTerminated()).isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("Сбой одного выполнения не останавливает расписание")
    void failureDoesNotStopSchedule() throws InterruptedException {
        BackgroundMonitor monitor = new BackgroundMonitor("hw09-test-monitor-");
        AtomicLong tick = new AtomicLong();
        monitor.setOperation(() -> {
            if (tick.incrementAndGet() == 2L) {
                throw new IllegalStateException("сбой выполнения");
            }
        });
        try {
            monitor.start(20L);
            assertThat(monitor.awaitCycles(4, 3_000L))
                    .as("после сбоя расписание обязано продолжать выполняться")
                    .isTrue();
            assertThat(monitor.failures())
                    .as("сбой одного выполнения учтён")
                    .isEqualTo(1L);
        } finally {
            monitor.stop(2_000L);
        }
        assertThat(monitor.isTerminated()).isTrue();
    }

    @Test
    @DisplayName("Некорректный период отвергается")
    void invalidPeriodIsRejected() {
        try (BackgroundMonitor monitor = new BackgroundMonitor("hw09-test-monitor-")) {
            assertThatThrownBy(() -> monitor.start(0L)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
