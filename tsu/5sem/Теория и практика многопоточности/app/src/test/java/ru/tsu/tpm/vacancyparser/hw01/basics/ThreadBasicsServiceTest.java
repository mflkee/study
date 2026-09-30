package ru.tsu.tpm.vacancyparser.hw01.basics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.hw01.basics.ThreadBasicsService.WorkerGroup;

class ThreadBasicsServiceTest {

    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
    private final ConsoleOutput console =
            new ConsoleOutput(new PrintStream(sink, true, StandardCharsets.UTF_8));
    private final ThreadBasicsService service = new ThreadBasicsService(console);

    private WorkerGroup group;

    @BeforeEach
    void startWorkers() throws InterruptedException {
        group = service.startWorkers();
    }

    @Test
    @DisplayName("Созданы ровно два потока с доменными уникальными именами")
    void workersHaveUniqueDomainNames() {
        List<Thread> threads = group.threads();

        assertThat(threads).hasSize(2);
        Set<String> names = threads.stream().map(Thread::getName).collect(Collectors.toSet());
        assertThat(names)
                .as("имена должны быть уникальными и доменными, а не Thread-N")
                .containsExactlyInAnyOrder(
                        ThreadBasicsService.COUNTER_WORKER, ThreadBasicsService.LOGGER_THREAD);
        assertThat(names).noneMatch(name -> name.matches("Thread-\\d+"));
        assertThat(names).allSatisfy(name -> assertThat(name).isNotBlank());
    }

    @Test
    @DisplayName("Порядковые номера задач не повторяются")
    void ordinalsAreUnique() {
        assertThat(Set.of(group.counting().ordinal(), group.logging().ordinal()))
                .hasSize(2);
        assertThat(List.of(group.counting().ordinal(), group.logging().ordinal()))
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("Демонстрационные потоки живы в момент наблюдения и попадают в список активных")
    void activeThreadsListContainsWorkers() {
        service.printActiveThreads();

        String output = console.captured();
        assertThat(group.threads())
                .as("на момент наблюдения оба демонстрационных потока должны быть живы")
                .allMatch(Thread::isAlive);
        assertThat(output)
                .contains(ThreadBasicsService.COUNTER_WORKER + " — " + Thread.State.WAITING)
                .contains(ThreadBasicsService.LOGGER_THREAD + " — " + Thread.State.WAITING);
        assertThat(output).contains("Всего активных потоков:");
    }

    @Test
    @DisplayName("Список активных потоков включает потоки самого приложения")
    void activeThreadsListIncludesApplicationThreads() {
        service.printActiveThreads();

        String output = console.captured();
        assertThat(output)
                .as("главный поток приложения обязан присутствовать в списке")
                .contains(Thread.currentThread().getName());
    }

    @Test
    @DisplayName("После открытия барьера и join оба потока завершены")
    void workersTerminateAfterJoin() throws InterruptedException {
        group.openGate();
        group.joinAll();
        service.printWorkerStates(group);

        assertThat(group.allTerminated()).isTrue();
        assertThat(console.captured())
                .contains("Состояние демонстрационных потоков после join():")
                .contains(ThreadBasicsService.COUNTER_WORKER + " — " + Thread.State.TERMINATED)
                .contains(ThreadBasicsService.LOGGER_THREAD + " — " + Thread.State.TERMINATED);
    }
}
