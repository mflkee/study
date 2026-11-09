package ru.tsu.tpm.vacancyparser.hw14.loom.swarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Сценарий «поток на задачу»: {@code Thread.ofVirtual().start()} против
 * {@code new Thread().start()}.
 *
 * <p>Проверяются инварианты, а не миллисекунды: каждая задача обязана завершиться, каждая — работать
 * в своём потоке (столько же имён, сколько задач), и каждый прогон обязан идти либо только на
 * виртуальных, либо только на платформенных потоках. Время в утверждениях почти не участвует —
 * только как грубая граница «не быстрее физически возможного».
 */
class ThreadSwarmTest {

    @Test
    @DisplayName("Thread.ofVirtual().start(): все задачи завершены, у каждой своё имя потока")
    void virtualSwarmRecordsEveryTask() {
        int tasks = 300;
        SwarmRun run = ThreadSwarm.runVirtual(tasks, 5L);

        assertThat(run.complete()).isTrue();
        assertThat(run.completed()).isEqualTo(tasks);
        assertThat(run.failed()).isZero();
        assertThat(run.batches()).isEqualTo(1);
        assertThat(run.names()).hasSize(tasks);
        assertThat(run.distinctNames()).isEqualTo(tasks);
        assertThat(run.names()).allMatch(name -> name.startsWith(ThreadSwarm.VIRTUAL_PREFIX));
        assertThat(run.virtualOnly()).isTrue();
        assertThat(run.platformOnly()).isFalse();
        assertThat(run.peakConcurrentTasks()).isEqualTo(tasks);
        // Процессорное время — оценка сверху для самого процесса; проверяем только, что оно есть.
        assertThat(run.cpuMillis()).isGreaterThanOrEqualTo(0.0);
        assertThat(run.wallMillis()).isGreaterThanOrEqualTo(4.0);
        assertThat(run.sample(3)).hasSize(3);
        assertThat(run.sample(0)).isEmpty();
    }

    @Test
    @DisplayName("new Thread().start(): платформенные потоки, тоже по одному на задачу")
    void platformSwarmUsesPlatformThreads() {
        int tasks = 40;
        SwarmRun run = ThreadSwarm.runPlatform(tasks, 5L);

        assertThat(run.complete()).isTrue();
        assertThat(run.completed()).isEqualTo(tasks);
        assertThat(run.batches()).isEqualTo(1);
        assertThat(run.distinctNames()).isEqualTo(tasks);
        assertThat(run.names()).allMatch(name -> name.startsWith(ThreadSwarm.PLATFORM_PREFIX));
        assertThat(run.platformOnly()).isTrue();
        assertThat(run.virtualOnly()).isFalse();
        assertThat(run.peakConcurrentTasks()).isBetween(1, tasks);
    }

    @Test
    @DisplayName("Ограничение одновременных потоков ОС разбивает запуск на пачки")
    void platformSwarmRunsInBatches() {
        int tasks = 40;
        int maxConcurrent = 10;
        SwarmRun run = ThreadSwarm.runPlatform(tasks, 5L, maxConcurrent, null);

        assertThat(run.complete()).isTrue();
        assertThat(run.batches()).isEqualTo(4);
        assertThat(run.distinctNames()).isEqualTo(tasks);
        // Четыре пачки по 5 мс сна — нижняя граница времени, ниже которой прогон быть не может.
        assertThat(run.wallMillis()).isGreaterThanOrEqualTo(20.0);
        assertThat(run.platformOnly()).isTrue();
    }

    @Test
    @DisplayName("Каждая задача отдаёт имя своего потока в приёмник")
    void everyTaskReportsItsThreadName() {
        int tasks = 25;
        List<String> echoed = new CopyOnWriteArrayList<>();
        SwarmRun run = ThreadSwarm.runVirtual(tasks, 1L, echoed::add);

        assertThat(echoed).hasSize(tasks);
        // Порядок печати задаёт планировщик, поэтому сравниваем как множества.
        assertThat(echoed).containsExactlyInAnyOrderElementsOf(run.names());
    }

    @Test
    @DisplayName("Yield вместо сна и пустой прогон — допустимые границы")
    void yieldModeAndEmptyRun() {
        SwarmRun yielded = ThreadSwarm.runVirtual(20, 0L);
        assertThat(yielded.complete()).isTrue();
        assertThat(yielded.distinctNames()).isEqualTo(20);

        SwarmRun empty = ThreadSwarm.runPlatform(0, 1L);
        assertThat(empty.tasks()).isZero();
        assertThat(empty.completed()).isZero();
        assertThat(empty.batches()).isZero();
        assertThat(empty.names()).isEmpty();
    }

    @Test
    @DisplayName("Некорректные параметры отвергаются")
    void invalidArguments() {
        assertThatThrownBy(() -> ThreadSwarm.runVirtual(-1, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ThreadSwarm.runVirtual(1, -1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ThreadSwarm.runPlatform(1, 1L, -5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
