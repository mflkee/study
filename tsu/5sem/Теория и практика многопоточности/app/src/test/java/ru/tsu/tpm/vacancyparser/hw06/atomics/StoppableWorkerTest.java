package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Остановка потока через {@code volatile}-признак.
 *
 * <p>Проверяется не только поведение, но и сама сигнатура поля: без {@code volatile} конструкция
 * «правильная наполовину», и это должно ловиться, а не полагаться на комментарий в коде.
 */
class StoppableWorkerTest {

    private static final long JOIN_TIMEOUT_MILLIS = 2_000L;

    @Test
    @DisplayName("Поле-признак finished объявлено private volatile boolean и по умолчанию значит «работать»")
    void flagFieldIsVolatileAndMeansWorkingByDefault() throws Exception {
        Field field = StoppableWorker.class.getDeclaredField("finished");

        assertThat(field.getType())
                .as("признак завершения обязан быть boolean")
                .isEqualTo(boolean.class);
        assertThat(Modifier.isVolatile(field.getModifiers()))
                .as("поле finished обязано быть volatile: иначе поток может не увидеть изменение")
                .isTrue();
        assertThat(Modifier.isPrivate(field.getModifiers()))
                .as("поле должно быть скрыто за методами класса")
                .isTrue();

        StoppableWorker worker = new StoppableWorker();
        field.setAccessible(true);
        boolean initial = (boolean) field.get(worker);
        assertThat(initial)
                .as("начальное значение признака означает «поток работает, не завершён»")
                .isFalse();
        assertThat(worker.isFinished()).isFalse();
    }

    @Test
    @Timeout(30)
    @DisplayName("Рабочий поток работает до установки признака и завершается после неё")
    void workerRunsUntilFlagSet() throws InterruptedException {
        StoppableWorker worker = new StoppableWorker();
        Thread thread = new Thread(worker, "hw06-flag-worker");
        thread.setDaemon(true);
        thread.start();
        assertThat(worker.awaitStarted(2_000L)).isTrue();

        Thread.sleep(30L);
        assertThat(worker.iterations())
                .as("до установки признака поток обязан успевать работать")
                .isPositive();
        assertThat(thread.isAlive()).isTrue();

        worker.finish();
        thread.join(JOIN_TIMEOUT_MILLIS);
        assertThat(thread.isAlive())
                .as("после установки volatile-признака поток обязан завершиться")
                .isFalse();
    }

    @Test
    @Timeout(30)
    @DisplayName("Повторная установка признака безопасна и новых потоков не создаёт")
    void repeatedFinishIsSafe() throws InterruptedException {
        StoppableWorker worker = new StoppableWorker();
        Thread thread = new Thread(worker, "hw06-flag-worker");
        thread.setDaemon(true);
        thread.start();
        worker.awaitStarted(2_000L);

        worker.finish();
        assertThatCode(() -> {
            worker.finish();
            worker.finish();
            worker.finish();
        })
                .as("многократная установка признака не должна приводить к исключению")
                .doesNotThrowAnyException();

        thread.join(JOIN_TIMEOUT_MILLIS);
        assertThat(worker.isFinished()).isTrue();
        assertThat(countAlive("hw06-flag-worker"))
                .as("после завершения не должно остаться работающих потоков с этим именем")
                .isZero();
    }

    private static long countAlive(String namePrefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> thread.getName().startsWith(namePrefix))
                .count();
    }
}
