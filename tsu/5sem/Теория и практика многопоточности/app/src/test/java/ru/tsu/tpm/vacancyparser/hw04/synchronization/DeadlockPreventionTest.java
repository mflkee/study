package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Ограничение демонстрации по времени и дамп потоков.
 *
 * <p>Проверяется именно страховка, а не «красивый вывод»: если поток демонстрации почему-то завис,
 * процесс обязан уложиться в лимит и показать дамп, а не висеть вместе с ним.
 *
 * <p>Взаимная блокировка здесь намеренно <b>не</b> создаётся: разбор такого случая и строки
 * «Found one Java-level deadlock» — задача ДЗ 7. Проверяется смежное и нужное здесь: дамп снимается,
 * а зависший поток с ожиданием не выдаётся за взаимную блокировку.
 */
class DeadlockPreventionTest {

    private static final long LIMIT_MILLIS = 300L;

    @Test
    @Timeout(20)
    @DisplayName("Задача, которая не завершилась в лимит, прерывается: процесс не ждёт её вечно")
    void timeBoxInterruptsHungTask() throws InterruptedException {
        CountDownLatch neverReleased = new CountDownLatch(1);

        long startedAt = System.nanoTime();
        TimeBoxedExecution.Outcome outcome = TimeBoxedExecution.run(LIMIT_MILLIS, () -> {
            try {
                // Поток блокируется навсегда: так выглядит несработавшая защита.
                neverReleased.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(outcome.completed())
                .as("задача не уложилась в лимит — это и должен обнаружить ограничитель")
                .isFalse();
        assertThat(outcome.failure()).isNull();
        assertThat(elapsedMillis)
                .as("ожидание ограничено лимитом, а не бесконечно")
                .isLessThan(TimeUnit.SECONDS.toMillis(5));
    }

    @Test
    @Timeout(20)
    @DisplayName("Уложившаяся задача возвращает результат и своё исключение, если оно было")
    void timeBoxReturnsTaskResult() throws InterruptedException {
        TimeBoxedExecution.Outcome ok = TimeBoxedExecution.run(LIMIT_MILLIS, () -> {
        });

        assertThat(ok.completed()).isTrue();
        assertThat(ok.failure()).isNull();

        IllegalStateException boom = new IllegalStateException("сбой внутри задачи");
        TimeBoxedExecution.Outcome failed = TimeBoxedExecution.run(LIMIT_MILLIS, () -> {
            throw boom;
        });

        assertThat(failed.completed()).isTrue();
        assertThat(failed.failure()).isSameAs(boom);
    }

    @Test
    @Timeout(20)
    @DisplayName("В дампе потоков видны имя потока и его стек — зависший поток можно разобрать")
    void dumpContainsThreadNamesAndStacks() throws InterruptedException {
        CountDownLatch parked = new CountDownLatch(1);
        CountDownLatch inside = new CountDownLatch(1);

        Thread hung = new Thread(() -> {
            inside.countDown();
            try {
                parked.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "hung-demo-thread");
        hung.setDaemon(true);
        hung.start();
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

        String dump = ThreadDump.capture();

        assertThat(dump).contains("Дамп потоков");
        assertThat(dump)
                .as("по дампу должно быть видно, какой поток завис и где именно")
                .contains("hung-demo-thread")
                .contains("at ");
        assertThat(dump).contains("взаимная блокировка: не обнаружена");

        parked.countDown();
        hung.join(5_000L);
    }

    @Test
    @DisplayName("Ожидание потока не считается взаимной блокировкой")
    void blockingIsNotReportedAsDeadlock() throws InterruptedException {
        CountDownLatch parked = new CountDownLatch(1);
        CountDownLatch inside = new CountDownLatch(1);

        Thread blocked = new Thread(() -> {
            inside.countDown();
            try {
                parked.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "blocked-not-deadlocked");
        blocked.setDaemon(true);
        blocked.start();
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(ThreadDump.hasDeadlock())
                .as("заблокированный поток — не взаимная блокировка: различать их обязательно")
                .isFalse();
        assertThat(ThreadDump.deadlockedThreadNames()).isEmpty();
        assertThat(ThreadDump.summary()).contains("взаимная блокировка: не обнаружена");

        parked.countDown();
        blocked.join(5_000L);
    }

    @Test
    @DisplayName("Нулевой и отрицательный лимит отвергаются")
    void limitMustBePositive() {
        assertThatIllegalArgumentException().isThrownBy(() -> TimeBoxedExecution.run(0L, () -> {
        }));
        assertThatIllegalArgumentException().isThrownBy(() -> TimeBoxedExecution.run(-1L, () -> {
        }));
    }
}
