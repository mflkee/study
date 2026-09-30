package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Гарантированное освобождение блокировки.
 *
 * <p>У монитора синхронизация неявна, и забыть её нельзя. Явная блокировка такой защиты не даёт:
 * исключение на пути выхода оставит её захваченной, и буфер станет мёртвым до перезапуска процесса.
 * Здесь проверяется, что после отказа внутри критической секции буфер продолжает работать.
 */
class LockReleaseTest {

    private static final long TIMEOUT_MILLIS = 1_000L;

    /**
     * Убедиться, что операция завершается, а не упирается в захваченную блокировку.
     *
     * <p>Каждая операция буфера берёт ту же блокировку, поэтому «зависший» вызов — верный признак
     * того, что блокировка осталась захваченной после исключения.
     */
    private static void assertDoesNotBlock(String what, Runnable operation) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                operation.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "lock-probe");
        thread.setDaemon(true);
        thread.start();
        thread.join(3_000L);

        assertThat(thread.isAlive())
                .as("%s: операция обязана завершиться — захваченная блокировка сделала бы буфер мёртвым", what)
                .isFalse();
        assertThat(failure.get())
                .as("%s: проба не должна падать с неожиданной ошибкой", what)
                .isNull();
    }

    @Test
    @Timeout(30)
    @DisplayName("Исключение при работе с закрытым буфером не оставляет блокировку захваченной")
    void exceptionInsideOperationReleasesTheLock() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);
        buffer.close();

        // Обе операции бросают внутри критической секции: снятие блокировки обязано произойти
        // в finally, иначе следующий вызов повиснет навсегда.
        assertThatExceptionOfType(BufferOperationException.class).isThrownBy(() -> buffer.put("не пройдёт"));
        assertThatExceptionOfType(BufferOperationException.class).isThrownBy(buffer::take);

        assertDoesNotBlock("размер после исключения", buffer::size);
        assertDoesNotBlock("повторная попытка добавления", () -> {
            try {
                buffer.offer("ещё раз", TIMEOUT_MILLIS);
            } catch (BufferOperationException expected) {
                // Буфер закрыт — ожидаемый исход; важно, что вызов завершился.
            }
        });
        assertDoesNotBlock("чтение состояния", () -> {
            buffer.isEmpty();
            buffer.isFull();
            buffer.isClosed();
        });
    }

    @Test
    @Timeout(30)
    @DisplayName("Прерывание внутри ожидания не оставляет блокировку захваченной")
    void interruptDoesNotLeaveLockHeld() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);
        buffer.put("заполнил");

        Thread producer = new Thread(() -> {
            try {
                buffer.put("не пройдёт");
            } catch (BufferOperationException expected) {
                // ожидаемый исход прерывания
            }
        }, "producer");
        producer.setDaemon(true);
        producer.start();
        Thread.sleep(150L);
        producer.interrupt();
        producer.join(5_000L);

        assertDoesNotBlock("извлечение после прерывания", () -> {
            try {
                buffer.take();
            } catch (BufferOperationException e) {
                throw new AssertionError("буфер должен остаться пригодным, получено: " + e.reason());
            }
        });
    }

    @Test
    @Timeout(30)
    @DisplayName("Истечение срока ожидания тоже отпускает блокировку")
    void timeoutReleasesTheLock() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, 100L);
        buffer.put("заполнил");

        // Времени не хватит: метод вернёт false, но обязан отпустить блокировку.
        assertThat(buffer.offer("не пройдёт", 100L)).isFalse();

        assertDoesNotBlock("операции после тайм-аута", () -> {
            buffer.size();
            buffer.take();
        });
    }
}
