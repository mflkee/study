package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Чувствительность: признак <b>без</b> {@code volatile} действительно не наблюдается.
 *
 * <p>Проверка обязательна иначе зелёный {@link VisibilityTest} ничего не доказывает: возможно, он
 * просто не задевает дефект. Здесь запускается отдельный процесс с признаком без {@code volatile}, и
 * ожидается «зомби»-цикл: поток не видит установленный флаг. Отдельный процесс нужен, чтобы застрявший
 * поток не мешал остальным тестам и замерам.
 */
class NonVolatileVisibilityTest {

    @Test
    @Timeout(60)
    @DisplayName("Признак без volatile не наблюдается: поток не завершается")
    void flagWithoutVolatileIsNotSeen() throws Exception {
        VisibilityDefectProbe.DefectOutcome outcome = VisibilityDefectProbe.run();

        assertThat(outcome.output())
                .as("вывод дочернего процесса: %s", outcome.output())
                .contains(NonVolatileStopProcess.STOPPED_MARKER + "false");
        assertThat(outcome.workerStopped())
                .as("без volatile поток не должен увидеть установленный признак — это и есть дефект")
                .isFalse();
    }
}
