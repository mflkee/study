package ru.tsu.tpm.vacancyparser.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsoleOutputAndTimingsTest {

    private static ConsoleOutput consoleWritingTo(ByteArrayOutputStream sink) {
        return new ConsoleOutput(new PrintStream(sink, true, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Замер через nanoTime возвращает неотрицательную длительность")
    void measureReturnsNonNegativeDuration() {
        Duration duration = Timings.measure(() -> { });

        assertThat(duration).isNotNull();
        assertThat(duration.isNegative()).isFalse();
        assertThat(Timings.measureNanos(() -> { })).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Замер охватывает реальное действие и даёт осмысленный порядок величин")
    void measureCoversActualWork() {
        Duration busyWait = Timings.measure(() -> {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
        });

        assertThat(busyWait.toNanos())
                .as("замер должен охватывать выполнение действия")
                .isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(10));
    }

    @Test
    @DisplayName("Человекочитаемый формат длительности покрывает все три порядка величин")
    void humanFormatCoversEveryMagnitude() {
        assertThat(Timings.human(Duration.ofNanos(999))).isEqualTo("999 мкс");
        assertThat(Timings.human(Duration.ofMillis(123))).isEqualTo("123.000 мс");
        assertThat(Timings.human(Duration.ofMillis(2_500))).isEqualTo("2.500 с");
    }

    @Test
    @DisplayName("Вывод накапливается в captured и дублируется в поток вывода")
    void outputIsCapturedAndStreamed() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        ConsoleOutput console = consoleWritingTo(sink);

        console.section("Заголовок");
        console.info("сообщение");
        console.item("элемент");

        assertThat(console.captured())
                .contains("=== Заголовок ===")
                .contains("[tpm] сообщение")
                .contains("    - элемент");
        assertThat(sink.toString(StandardCharsets.UTF_8))
                .as("вывод должен попадать и в поток, и в накопитель")
                .isEqualTo(console.captured());
    }

    @Test
    @DisplayName("Накопитель вывода не теряет строки при записи из нескольких потоков")
    void outputIsThreadSafe() throws InterruptedException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        ConsoleOutput console = consoleWritingTo(sink);

        int threads = 8;
        int linesPerThread = 50;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int line = 0; line < linesPerThread; line++) {
                        console.info("строка");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            worker.start();
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        long written = console.captured().lines().filter(line -> line.equals("[tpm] строка")).count();
        assertThat(written)
                .as("ни одна строка не должна потеряться при конкурентной записи")
                .isEqualTo((long) threads * linesPerThread);
    }
}
