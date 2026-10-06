package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Видимость {@code volatile}-признака без синхронизации.
 *
 * <p>Основной сценарий: один поток устанавливает признак, рабочий читает его в цикле — и обязан
 * увидеть изменение, хотя между ними нет ни блокировок, ни иных механизмов синхронизации. Отдельно
 * проверяется, что в рабочем цикле действительно нет «подпорок» (блокировок или задержек), которые
 * маскировали бы проблему видимости.
 */
class VisibilityTest {

    @Test
    @Timeout(30)
    @DisplayName("Признак виден рабочему потоку без внешней синхронизации")
    void flagIsSeenWithoutSynchronization() throws InterruptedException {
        StoppableWorker worker = new StoppableWorker();
        Thread thread = new Thread(worker, "hw06-visibility-worker");
        thread.setDaemon(true);
        thread.start();
        assertThat(worker.awaitStarted(2_000L)).isTrue();

        worker.finish();
        thread.join(2_000L);

        assertThat(thread.isAlive())
                .as("volatile-признак обязан быть виден без синхронизации: поток завершился")
                .isFalse();
    }

    @Test
    @DisplayName("В рабочем цикле нет блокировок, сна и иных искусственных задержек")
    void workerUsesNoSynchronization() throws IOException {
        Path source = TestSources.mainJava().resolve("ru/tsu/tpm/vacancyparser/hw06/atomics/StoppableWorker.java");
        String code = Files.readString(source);

        assertThat(code)
                .as("поле-признак обязано быть volatile")
                .contains("volatile boolean finished");

        for (String forbidden : new String[] {"synchronized", "java.util.concurrent.locks", "Thread.sleep"}) {
            assertThat(code)
                    .as("в рабочем цикле не должно быть «подпорок», маскирующих видимость: %s", forbidden)
                    .doesNotContain(forbidden);
        }
    }
}
