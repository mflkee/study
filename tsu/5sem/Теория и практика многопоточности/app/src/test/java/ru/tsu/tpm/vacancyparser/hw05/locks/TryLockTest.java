package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Попытка операции с ограничением по времени: явный признак неуспеха вместо бесконечного ожидания.
 *
 * <p>Это второе, чего не умеет монитор: у {@code synchronized} нет ни тайм-аута, ни попытки — только
 * «ждать, сколько потребуется». У {@code ReentrantLock}/{@code Condition} ожидание ограничивается
 * временем, и вызывающий код получает ответ, а не зависает.
 */
class TryLockTest {

    private static final long SHORT_TIMEOUT_MILLIS = 150L;

    @Test
    @Timeout(20)
    @DisplayName("Добавление в заполненный буфер возвращает признак неуспеха в пределах интервала")
    void offerOnFullBufferFailsWithinTimeout() {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, SHORT_TIMEOUT_MILLIS);
        buffer.put("занял место");

        long startedAt = System.nanoTime();
        boolean accepted = buffer.offer("не пройдёт", SHORT_TIMEOUT_MILLIS);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(accepted)
                .as("явный признак неуспеха доходит до вызывающего кода")
                .isFalse();
        assertThat(elapsedMillis)
                .as("попытка ограничена заданным интервалом, а не блокируется намертво (%d мс)", elapsedMillis)
                .isGreaterThanOrEqualTo(SHORT_TIMEOUT_MILLIS - 20L)
                .isLessThan(5_000L);
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.take()).isEqualTo("занял место");
    }

    @Test
    @Timeout(20)
    @DisplayName("Извлечение из пустого буфера возвращает null в пределах интервала")
    void pollOnEmptyBufferReturnsNullWithinTimeout() {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, SHORT_TIMEOUT_MILLIS);

        long startedAt = System.nanoTime();
        String item = buffer.poll(SHORT_TIMEOUT_MILLIS);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(item).isNull();
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(SHORT_TIMEOUT_MILLIS - 20L).isLessThan(5_000L);
    }

    @Test
    @Timeout(20)
    @DisplayName("Операции без явного тайм-аута сообщают об истечении срока явной ошибкой")
    void defaultTimeoutIsReportedAsFailure() {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, SHORT_TIMEOUT_MILLIS);
        buffer.put("занял место");

        assertThatExceptionOfType(BufferOperationException.class)
                .isThrownBy(() -> buffer.put("не пройдёт"))
                .satisfies(e -> assertThat(e.reason())
                        .isEqualTo(BufferOperationException.Reason.TIMEOUT));

        BoundedBuffer<String> empty = new BoundedBuffer<>(1, SHORT_TIMEOUT_MILLIS);
        assertThatExceptionOfType(BufferOperationException.class)
                .isThrownBy(empty::take)
                .satisfies(e -> assertThat(e.reason())
                        .isEqualTo(BufferOperationException.Reason.TIMEOUT));
    }

    @Test
    @Timeout(20)
    @DisplayName("Тайм-аут не портит буфер: после неуспеха он работает как обычно")
    void failedAttemptLeavesBufferUsable() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, SHORT_TIMEOUT_MILLIS);
        buffer.put("первый");

        assertThat(buffer.offer("отвергнут", SHORT_TIMEOUT_MILLIS)).isFalse();

        assertThat(buffer.take()).isEqualTo("первый");
        assertThat(buffer.offer("теперь пройдёт", TimeUnit.SECONDS.toMillis(2))).isTrue();
        assertThat(buffer.take()).isEqualTo("теперь пройдёт");
    }

    @Test
    @Timeout(20)
    @DisplayName("Тот же контракт у мониторной реализации: обе ведут себя одинаково")
    void monitorBufferHasSameTimeoutContract() {
        MonitorBuffer<String> buffer = new MonitorBuffer<>(1, SHORT_TIMEOUT_MILLIS);
        buffer.put("занял место");

        assertThat(buffer.offer("не пройдёт", SHORT_TIMEOUT_MILLIS)).isFalse();
        assertThat(buffer.poll(SHORT_TIMEOUT_MILLIS)).isEqualTo("занял место");
        assertThat(buffer.poll(SHORT_TIMEOUT_MILLIS)).isNull();
        MonitorBuffer<String> empty = new MonitorBuffer<>(1, SHORT_TIMEOUT_MILLIS);
        assertThatExceptionOfType(BufferOperationException.class)
                .isThrownBy(empty::take)
                .satisfies(e -> assertThat(e.reason())
                        .isEqualTo(BufferOperationException.Reason.TIMEOUT));
    }
}
