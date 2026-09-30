package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

/**
 * Проверка признака неуспеха демонстрации.
 *
 * <p>Сам {@code run()} не вызывается: он завершает процесс через {@code System.exit} в ветке
 * неуспеха. Проверяется чистая функция отчёта — она возвращает код выхода и печатает сообщение.
 */
class ThreadLifecycleDemoReportTest {

    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
    private final ConsoleOutput console =
            new ConsoleOutput(new PrintStream(sink, true, StandardCharsets.UTF_8));

    @Test
    @DisplayName("Полный набор состояний — код выхода 0 и сообщение об успехе")
    void completeSetGivesZeroExitCode() {
        ObservationStubs.Observation observation = ObservationStubs.complete();

        int exitCode = ThreadLifecycleDemo.report(observation.toObservation(), console);

        assertThat(exitCode).isZero();
        assertThat(console.captured())
                .contains("Демонстрация завершена")
                .doesNotContain("ВНИМАНИЕ");
    }

    @Test
    @DisplayName("Неполный набор состояний — ненулевой код выхода и предупреждение")
    void incompleteSetGivesFailureExitCode() {
        ObservationStubs.Observation observation = ObservationStubs.incomplete();

        int exitCode = ThreadLifecycleDemo.report(observation.toObservation(), console);

        assertThat(exitCode)
                .as("неполный набор обязан давать признак неуспеха, а не тихий успех")
                .isEqualTo(1);
        assertThat(console.captured())
                .contains("ВНИМАНИЕ")
                .contains("неполон")
                .contains("TIMED_WAITING");
    }

    /**
     * Сборка значений {@link LifecycleObservationService.Observation} без запуска демонстрации.
     */
    static final class ObservationStubs {

        private ObservationStubs() {
        }

        record Observation(Map<String, Set<Thread.State>> statesByThread, List<Thread.State> missing, List<String> unreached) {

            LifecycleObservationService.Observation toObservation() {
                return new LifecycleObservationService.Observation(statesByThread, missing, unreached, List.of());
            }
        }

        static Observation complete() {
            return new Observation(
                    Map.of("Worker", Set.of(Thread.State.NEW, Thread.State.RUNNABLE, Thread.State.TERMINATED)),
                    List.of(),
                    List.of());
        }

        static Observation incomplete() {
            return new Observation(
                    Map.of("Worker", Set.of(Thread.State.NEW, Thread.State.RUNNABLE, Thread.State.TERMINATED)),
                    List.of(Thread.State.TIMED_WAITING),
                    List.of("у потока SleepingWorker не зафиксировано состояние TIMED_WAITING"));
        }
    }
}
