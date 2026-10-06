package ru.tsu.tpm.vacancyparser.hw09.callable.cancel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Отмена длительной задачи: отменённая задача обязана действительно прекратить работу.
 */
class CancellationScenarioTest {

    @Test
    @Timeout(30)
    @DisplayName("Отмена до завершения: задача прервана, результат недоступен, исполнитель завершён")
    void cancelBeforeCompletionStopsTheTask() throws InterruptedException {
        CancellationScenario.CancelOutcome outcome = CancellationScenario.runCancelled(600L, 200L, 50L);

        assertThat(outcome.started()).isTrue();
        assertThat(outcome.cancelled())
                .as("cancel() обязан сообщить об отмене идущей задачи")
                .isTrue();
        assertThat(outcome.resultUnavailable())
                .as("после отмены результат недоступен")
                .isTrue();
        assertThat(outcome.interrupted())
                .as("задача обязана прекратиться по прерыванию, а не досчитать до конца")
                .isTrue();
        assertThat(outcome.completedNaturally())
                .as("отменённая задача не должна завершиться как выполненная")
                .isFalse();
        assertThat(outcome.progressAtCancel())
                .as("отмена произошла до конца работы")
                .isLessThan(outcome.totalSteps());
        assertThat(outcome.executorTerminated()).isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("Задача успела завершиться: результат получен, отмена ничего не меняет")
    void cancelAfterCompletionKeepsResult() throws InterruptedException {
        CancellationScenario.CancelOutcome outcome = CancellationScenario.runCompleted(300L, 50L);

        assertThat(outcome.result())
                .as("завершённая задача возвращает результат")
                .isNotNull();
        assertThat(outcome.cancelled())
                .as("отмена уже завершённой задачи не удалась")
                .isFalse();
        assertThat(outcome.resultUnavailable())
                .as("результат доступен — задача не отменена")
                .isFalse();
        assertThat(outcome.completedNaturally()).isTrue();
        assertThat(outcome.executorTerminated()).isTrue();
    }

    @Test
    @DisplayName("Длительная задача: параметры проверяются, прогресс и шаги согласованы")
    void longTaskBasics() {
        LongTask task = new LongTask(1_000L, 100L);

        assertThat(task.totalSteps()).isEqualTo(10L);
        assertThat(task.progress()).isZero();
        assertThat(task.wasInterrupted()).isFalse();
        assertThat(task.completedNaturally()).isFalse();

        assertThatThrownBy(() -> new LongTask(0L, 100L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LongTask(100L, 0L)).isInstanceOf(IllegalArgumentException.class);
    }
}
