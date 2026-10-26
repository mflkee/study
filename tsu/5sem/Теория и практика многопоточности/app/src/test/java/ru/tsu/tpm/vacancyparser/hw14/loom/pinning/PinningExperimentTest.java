package ru.tsu.tpm.vacancyparser.hw14.loom.pinning;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.SchedulerInfo;

/**
 * Опыт с pinning.
 *
 * <p>Проверяются структурные признаки: при {@code synchronized} одновременно ждут не больше задач,
 * чем есть carrier-потоков, и ни один поток не сменил carrier; при {@code ReentrantLock} ждут все
 * задачи сразу. Время в утверждениях не участвует — только счётчики.
 */
class PinningExperimentTest {

    @Test
    @DisplayName("synchronized пришпиливает виртуальные потоки к carrier'ам")
    void synchronizedPinsToCarriers() {
        int parallelism = SchedulerInfo.parallelism();
        int tasks = Math.max(8, parallelism * 2);
        PinningRun run = PinningExperiment.runSynchronized(tasks, 40L);

        assertThat(run.complete()).isTrue();
        assertThat(run.parallelism()).isEqualTo(parallelism);
        assertThat(run.peakConcurrentTasks()).isLessThanOrEqualTo(parallelism);
        assertThat(run.peakConcurrentTasks()).isLessThan(tasks);
        assertThat(run.carrierSwitches()).isZero();
        assertThat(run.carriersUsed()).isLessThanOrEqualTo(parallelism);
        assertThat(run.pinnedRounds()).isEqualTo(2);
        assertThat(run.expectedPinnedMillis()).isEqualTo(80.0);
        assertThat(run.limitedByCarriers()).isTrue();
    }

    @Test
    @DisplayName("ReentrantLock отпускает carrier — одновременно ждут все задачи")
    void reentrantLockReleasesCarrier() {
        int parallelism = SchedulerInfo.parallelism();
        int tasks = Math.max(8, parallelism * 2);
        PinningRun pinned = PinningExperiment.runSynchronized(tasks, 40L);
        PinningRun free = PinningExperiment.runReentrantLock(tasks, 40L);

        assertThat(free.complete()).isTrue();
        assertThat(free.peakConcurrentTasks()).isGreaterThan(parallelism);
        assertThat(free.peakConcurrentTasks()).isEqualTo(tasks);
        assertThat(free.peakConcurrentTasks()).isGreaterThan(pinned.peakConcurrentTasks());
        assertThat(free.limitedByCarriers()).isFalse();
        assertThat(free.carriersUsed()).isLessThanOrEqualTo(parallelism);
    }
}
