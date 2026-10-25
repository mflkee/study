package ru.tsu.tpm.vacancyparser.hw12.collections.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Замеры коллекций: корректность результатов (без потерь) и отказ некорректных параметров.
 *
 * <p>Скорость здесь не проверяется: на маленькой нагрузке разница тонет в шуме, а абсолютные числа
 * зависят от машины. Проверяется то, что обязано быть верным всегда, — итоговые данные.
 */
class ConcurrentBenchmarksTest {

    @Test
    @Timeout(60)
    @DisplayName("Конкурентная карта сохраняет все ключи, счётчики не теряют инкременты")
    void resultsAreCorrect() throws InterruptedException {
        ConcurrentBenchmarks.MapRun concurrent = ConcurrentBenchmarks.concurrentMap(4, 50, 2_000);
        ConcurrentBenchmarks.MapRun synchronizedMap = ConcurrentBenchmarks.synchronizedMap(4, 50, 2_000);

        assertThat(concurrent.operations()).isEqualTo(8_000);
        assertThat(concurrent.finalSize()).isBetween(1, 50);
        assertThat(concurrent.opsPerSecond()).isPositive();
        assertThat(synchronizedMap.finalSize()).isBetween(1, 50);
        assertThat(concurrent.reportLine()).contains("ConcurrentHashMap").contains("операций");

        ConcurrentBenchmarks.CounterRun adder = ConcurrentBenchmarks.longAdder(4, 5_000);
        ConcurrentBenchmarks.CounterRun atomic = ConcurrentBenchmarks.atomicLong(4, 5_000);

        assertThat(adder.correct()).isTrue();
        assertThat(atomic.correct()).isTrue();
        assertThat(adder.actual()).isEqualTo(20_000);
        assertThat(atomic.actual()).isEqualTo(20_000);
        assertThat(adder.reportLine()).contains("LongAdder").contains("корректно: да");
    }

    @Test
    @Timeout(60)
    @DisplayName("Замер произвольной карты возвращает её фактический размер")
    void runMapOnCustomMap() throws InterruptedException {
        Map<String, String> map = new HashMap<>();

        ConcurrentBenchmarks.MapRun run = ConcurrentBenchmarks.runMap("HashMap", map, 2, 10, 100);

        assertThat(run.finalSize()).isEqualTo(map.size());
        assertThat(run.finalSize()).isBetween(1, 10);
        assertThat(run.label()).isEqualTo("HashMap");
    }

    @Test
    @DisplayName("Некорректные параметры замеров отвергаются")
    void validation() {
        assertThatThrownBy(() -> ConcurrentBenchmarks.concurrentMap(0, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConcurrentBenchmarks.concurrentMap(2, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConcurrentBenchmarks.concurrentMap(2, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConcurrentBenchmarks.runMap(null, new HashMap<>(), 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConcurrentBenchmarks.runMap("x", null, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
