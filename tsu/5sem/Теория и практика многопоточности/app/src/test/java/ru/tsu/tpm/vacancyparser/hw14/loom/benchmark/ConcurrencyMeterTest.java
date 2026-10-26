package ru.tsu.tpm.vacancyparser.hw14.loom.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Счётчик одновременной работы: пик, число потоков, разделение на виртуальные и платформенные.
 */
class ConcurrencyMeterTest {

    @Test
    @DisplayName("Пик фиксирует одновременную работу, а выход уменьшает счётчик")
    void peakTracksConcurrentWork() throws InterruptedException {
        ConcurrencyMeter meter = new ConcurrencyMeter();
        CountDownLatch bothInside = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        Runnable task = () -> {
            meter.enter(Thread.currentThread());
            try {
                bothInside.countDown();
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                meter.exit();
            }
        };
        Thread first = Thread.ofVirtual().start(task);
        Thread second = Thread.ofVirtual().start(task);

        assertThat(bothInside.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(meter.inFlight()).isEqualTo(2);
        assertThat(meter.peak()).isEqualTo(2);
        release.countDown();
        first.join(5_000L);
        second.join(5_000L);

        assertThat(meter.inFlight()).isZero();
        assertThat(meter.peak()).isEqualTo(2);
        assertThat(meter.virtualExecutions()).isEqualTo(2);
        assertThat(meter.platformExecutions()).isZero();
        assertThat(meter.workerCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Платформенные потоки считаются отдельно от виртуальных")
    void platformAndVirtualExecutionsAreSeparated() throws InterruptedException {
        ConcurrencyMeter meter = new ConcurrencyMeter();
        Thread platform = new Thread(() -> {
            meter.enter(Thread.currentThread());
            meter.exit();
        }, "hw14-test-platform-meter");
        platform.start();
        platform.join(5_000L);

        Thread virtual = Thread.ofVirtual().start(() -> {
            meter.enter(Thread.currentThread());
            meter.exit();
        });
        virtual.join(5_000L);

        assertThat(meter.platformExecutions()).isEqualTo(1);
        assertThat(meter.virtualExecutions()).isEqualTo(1);
        assertThat(meter.workerCount()).isEqualTo(2);
    }
}
