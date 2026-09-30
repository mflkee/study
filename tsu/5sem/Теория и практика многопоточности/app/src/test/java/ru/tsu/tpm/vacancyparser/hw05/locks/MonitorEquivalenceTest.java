package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Обе реализации обязаны проходить один и тот же набор проверок.
 *
 * <p>Это и есть условие осмысленности сравнения производительности: сравнивать быстрое решение с
 * неправильным нельзя. Поэтому все проверки написаны против интерфейса и выполняются на обеих
 * реализациях, а отдельный тест убеждается, что при одинаковой нагрузке они дают одинаковый набор
 * извлечённых элементов — различаться может только время.
 */
class MonitorEquivalenceTest {

    static Stream<Supplier<Buffer<String>>> implementations() {
        return Stream.of(
                () -> new BoundedBuffer<>(2),
                () -> new MonitorBuffer<>(2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementations")
    @DisplayName("Инварианты выполняются на обеих реализациях")
    @Timeout(120)
    void invariantsHoldForBothImplementations(Supplier<Buffer<String>> factory) throws InterruptedException {
        Buffer<String> buffer = factory.get();

        ProducerConsumerRunner.Result result = ProducerConsumerRunner.run(buffer, 3, 3, 300);

        assertThat(result.capacity()).isEqualTo(2);
        assertThat(result.produced()).isEqualTo(900);
        assertThat(result.taken()).hasSize(900);
        assertThat(result.remainingInBuffer()).isZero();
        assertThat(result.nothingLost()).isTrue();
        assertThat(result.nothingDuplicated()).isTrue();
        assertThat(result.sameElements()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("implementations")
    @DisplayName("Ёмкость 1 на обеих реализациях: максимальное переключение состояний")
    @Timeout(120)
    void capacityOneHoldsForBothImplementations(Supplier<Buffer<String>> factory) throws InterruptedException {
        ProducerConsumerRunner.Result result = ProducerConsumerRunner.run(factory.get(), 4, 4, 200);

        assertThat(result.consistent()).isTrue();
        assertThat(result.taken()).hasSize(800);
    }

    @Test
    @DisplayName("При одинаковой нагрузке набор извлечённых элементов совпадает")
    @Timeout(180)
    void bothImplementationsProduceTheSameElements() throws InterruptedException {
        ProducerConsumerRunner.Result lockRun =
                ProducerConsumerRunner.run(new BoundedBuffer<>(3), 4, 4, 400);
        ProducerConsumerRunner.Result monitorRun =
                ProducerConsumerRunner.run(new MonitorBuffer<>(3), 4, 4, 400);

        assertThat(new LinkedHashSet<>(monitorRun.taken()))
                .as("различаться может только время, но не результат")
                .isEqualTo(new LinkedHashSet<>(lockRun.taken()));
        assertThat(monitorRun.taken()).hasSize(lockRun.taken().size());
        assertThat(monitorRun.remainingInBuffer()).isEqualTo(lockRun.remainingInBuffer()).isZero();
    }

    @Test
    @DisplayName("Число впустую проснувшихся: у двух условий их меньше, чем у одного")
    @Timeout(120)
    void twoConditionsWakeFewerThreadsInVain() throws InterruptedException {
        BoundedBuffer<String> lock = new BoundedBuffer<>(1);
        MonitorBuffer<String> monitor = new MonitorBuffer<>(1);

        ProducerConsumerRunner.run(lock, 3, 3, 300);
        ProducerConsumerRunner.run(monitor, 3, 3, 300);

        assertThat(monitor.wastedWakeups())
                .as("одно множество ожидающих будит не ту сторону: монитор просыпается впустую чаще "
                        + "(монитор: %d, два условия: %d)", monitor.wastedWakeups(), lock.wastedWakeups())
                .isGreaterThan(lock.wastedWakeups());
    }

    @Test
    @DisplayName("Сравнение не вырождено: две реализации действительно разные механизмы")
    void implementationsUseDifferentMechanisms() {
        List<String> mechanisms = List.of(BufferBenchmark.LOCK_MECHANISM, BufferBenchmark.MONITOR_MECHANISM);

        assertThat(mechanisms).hasSize(2).doesNotHaveDuplicates();
        assertThat(mechanisms.get(0)).contains("Condition");
        assertThat(mechanisms.get(1)).contains("wait/notify");
    }
}
