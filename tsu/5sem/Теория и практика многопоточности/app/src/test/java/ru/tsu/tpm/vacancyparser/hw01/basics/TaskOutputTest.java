package ru.tsu.tpm.vacancyparser.hw01.basics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

class TaskOutputTest {

    private static final CountDownLatch OPEN = new CountDownLatch(0);

    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
    private final ConsoleOutput console =
            new ConsoleOutput(new PrintStream(sink, true, StandardCharsets.UTF_8));

    @Test
    @DisplayName("Задача, созданная наследованием от Thread, печатает имя потока и номер")
    void countingTaskPrintsThreadNameAndOrdinal() {
        CountingTask task =
                new CountingTask(ThreadBasicsService.COUNTER_WORKER, 7, console, OPEN, OPEN);

        task.run();

        assertThat(task.ordinal()).isEqualTo(7);
        assertThat(console.captured())
                .as("в выводе должно быть имя выполняющего потока и порядковый номер")
                .contains(Thread.currentThread().getName())
                .contains("№7")
                .contains("наследованием от Thread");
    }

    @Test
    @DisplayName("Задача, реализующая Runnable, печатает имя потока и номер")
    void loggingTaskPrintsThreadNameAndOrdinal() {
        LoggingTask task = new LoggingTask(3, console, OPEN, OPEN);

        task.run();

        assertThat(task.ordinal()).isEqualTo(3);
        assertThat(console.captured())
                .as("в выводе должно быть имя выполняющего потока и порядковый номер")
                .contains(Thread.currentThread().getName())
                .contains("№3")
                .contains("реализует Runnable");
    }

    @Test
    @DisplayName("Барьер удерживает задачу до открытия")
    void gateHoldsTaskUntilOpened() throws InterruptedException {
        CountDownLatch gate = new CountDownLatch(1);
        CountingTask task =
                new CountingTask(ThreadBasicsService.COUNTER_WORKER, 1, console, OPEN, gate);

        Thread worker = new Thread(task, ThreadBasicsService.COUNTER_WORKER);
        worker.start();
        Thread.sleep(100);

        assertThat(worker.isAlive()).as("поток должен ждать на барьере").isTrue();
        assertThat(console.captured()).doesNotContain("обработал элемент");

        gate.countDown();
        worker.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(worker.isAlive()).isFalse();
        assertThat(console.captured()).contains("обработал элемент");
    }
}
