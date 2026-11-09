package ru.tsu.tpm.vacancyparser.hw14.loom.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Опыты со {@code StructuredTaskScope}.
 *
 * <p>Тесты проверяют то, ради чего скоуп и нужен: группа не теряет подзадач (сумма счётчиков равна
 * числу подзадач), при сбое одной остальные действительно отменяются (прогон заканчивается раньше
 * «полной» задержки), по таймауту отменяются все незавершённые, а «первый успешный» отменяет дубли.
 * Абсолютные миллисекунды не проверяются — только соотношения с запасом.
 */
class StructuredExperimentsTest {

    @Test
    @DisplayName("join() ждёт всю группу: все подзадачи вернули результат")
    void allSubtasksComplete() {
        int tasks = 200;
        ScopeRun run = StructuredExperiments.runAll(tasks, 10L);

        assertThat(run.allSucceeded()).isTrue();
        assertThat(run.settled()).isTrue();
        assertThat(run.succeeded()).isEqualTo(tasks);
        assertThat(run.failed()).isZero();
        assertThat(run.cancelled()).isZero();
        assertThat(run.outcome()).isEqualTo(StructuredExperiments.OUTCOME_SUCCESS);
        assertThat(run.endedWithError()).isFalse();
        assertThat(run.policy()).isEqualTo("ShutdownOnFailure");
        assertThat(run.results()).hasSize(tasks);
        assertThat(run.virtualExecutions()).isEqualTo(tasks);
        assertThat(run.platformExecutions()).isZero();
        assertThat(run.peakConcurrentTasks()).isGreaterThan(1);
        assertThat(run.wallMillis()).isGreaterThanOrEqualTo(8.0);
    }

    @Test
    @DisplayName("Падение одной подзадачи отменяет остальные — прогон короче полной задержки")
    void failureCancelsRemainingSubtasks() {
        int tasks = 200;
        int failingTask = 137;
        ScopeRun run = StructuredExperiments.runWithFailure(tasks, 500L, failingTask, 20L);

        assertThat(run.settled()).isTrue();
        assertThat(run.outcome()).isEqualTo(StructuredExperiments.OUTCOME_FAILURE);
        assertThat(run.exceptionType()).isEqualTo("IllegalStateException");
        assertThat(run.exceptionMessage()).contains("источник недоступен").contains("137");
        assertThat(run.failed()).isEqualTo(1);
        assertThat(run.cancelled()).isGreaterThanOrEqualTo(tasks - 5);
        assertThat(run.succeeded()).isZero();
        // Полная пауза — 500 мс; отмена должна закончить прогон заметно раньше.
        assertThat(run.wallMillis()).isLessThan(400.0);
    }

    @Test
    @DisplayName("joinUntil по сроку: TimeoutException и отмена всех незавершённых")
    void timeoutCancelsEverything() {
        int tasks = 100;
        ScopeRun run = StructuredExperiments.runWithTimeout(tasks, 3_000L, 150L);

        assertThat(run.settled()).isTrue();
        assertThat(run.outcome()).isEqualTo(StructuredExperiments.OUTCOME_TIMEOUT);
        assertThat(run.exceptionType()).isEqualTo("TimeoutException");
        assertThat(run.cancelled()).isEqualTo(tasks);
        assertThat(run.succeeded()).isZero();
        // Задачи спали бы 3 с; со сроком 150 мс прогон должен уложиться заметно раньше.
        assertThat(run.wallMillis()).isLessThan(1_000.0);
    }

    @Test
    @DisplayName("Первый успешный ответ отменяет дубли")
    void firstSuccessCancelsReplicas() {
        ScopeRun run = StructuredExperiments.runFirstSuccess(List.of(600L, 40L, 200L));

        assertThat(run.settled()).isTrue();
        assertThat(run.outcome()).isEqualTo(StructuredExperiments.OUTCOME_SUCCESS);
        assertThat(run.succeeded()).isEqualTo(1);
        assertThat(run.results()).hasSize(1);
        assertThat(run.results().get(0)).contains("источник №1");
        assertThat(run.cancelled()).isGreaterThanOrEqualTo(1);
        // Самый быстрый источник отвечает за 40 мс; ждать 600 мс незачем.
        assertThat(run.wallMillis()).isLessThan(400.0);
    }

    @Test
    @DisplayName("Некорректные параметры опытов отвергаются")
    void invalidArguments() {
        assertThatThrownBy(() -> StructuredExperiments.runAll(-1, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuredExperiments.runAll(1, -1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuredExperiments.runWithFailure(0, 1L, 0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuredExperiments.runWithFailure(5, 1L, 9, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuredExperiments.runWithTimeout(5, 10L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuredExperiments.runFirstSuccess(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StructuredExperiments.runFirstSuccess(List.of(-1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Причина ExecutionException разворачивается до исходного исключения")
    void rootCauseUnwrapsExecutionException() {
        IllegalStateException source = new IllegalStateException("сбой");
        ExecutionException wrapped = new ExecutionException(source);

        assertThat(StructuredExperiments.rootCause(wrapped)).isSameAs(source);
        assertThat(StructuredExperiments.rootCause(source)).isSameAs(source);
        assertThat(StructuredExperiments.isSubtaskThread(new Thread(() -> { }, "hw14-sts-0"))).isTrue();
        assertThat(StructuredExperiments.isSubtaskThread(new Thread(() -> { }, "main"))).isFalse();
        assertThat(StructuredExperiments.isSubtaskThread(null)).isFalse();
    }
}
