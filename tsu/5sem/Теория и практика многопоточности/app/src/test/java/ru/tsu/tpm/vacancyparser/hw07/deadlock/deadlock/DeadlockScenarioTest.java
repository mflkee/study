package ru.tsu.tpm.vacancyparser.hw07.deadlock.deadlock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Полный сценарий взаимной блокировки: подтверждение, дамп, разбор, освобождение.
 */
class DeadlockScenarioTest {

    @Test
    @Timeout(60)
    @DisplayName("Сценарий подтверждает блокировку, снимает и разбирает дамп, освобождает потоки")
    void scenarioConfirmsCapturesParsesAndReleases() throws InterruptedException {
        DeadlockScenario.Outcome outcome = DeadlockScenario.run(100L);

        assertThat(outcome.deadlocked())
                .as("взаимное ожидание обязано подтверждаться програмно")
                .isTrue();
        assertThat(outcome.report().detected())
                .as("разбор дампа обязан найти секцию обнаружения")
                .isTrue();
        assertThat(outcome.report().threadNames())
                .as("разбор и сценарий обязаны называть одни и те же потоки")
                .containsExactlyInAnyOrder(DeadlockRig.THREAD_A, DeadlockRig.THREAD_B);
        assertThat(Set.of(outcome.jvmThreadNames().split(",\\s*")))
                .isEqualTo(Set.copyOf(outcome.report().threadNames()));
        assertThat(outcome.released()).as("потоки освобождены").isTrue();
        assertThat(outcome.liveThreads()).isEmpty();

        assumeTrue(outcome.dumpCaptured(), "jstack/jcmd недоступны — проверка файла пропущена");
        assertThat(outcome.dumpPath()).endsWith(".jstack");
        assertThat(Files.exists(Path.of(outcome.dumpPath())))
                .as("дамп сохранён в каталог сборки")
                .isTrue();
    }
}
