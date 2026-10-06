package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Базовое поведение атомарного счётчика: последовательность увеличений и чтение.
 */
class AtomicCounterTest {

    @Test
    @DisplayName("Последовательность увеличений даёт ожидаемые значения")
    void sequentialIncrementsAreExact() {
        AtomicCounter counter = new AtomicCounter();

        assertThat(counter.get()).isZero();
        for (int i = 1; i <= 5; i++) {
            assertThat(counter.incrementAndGet())
                    .as("увеличение №%d возвращает новое значение", i)
                    .isEqualTo(i);
        }
        assertThat(counter.get()).isEqualTo(5);
        assertThat(counter.value()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Чтение значения не требует синхронизации и совпадает с записанным")
    void readReflectsTheOnlyWrite() {
        AtomicCounter counter = new AtomicCounter();
        counter.increment();
        counter.increment();
        counter.increment();

        assertThat(counter.get()).isEqualTo(3);
        assertThat(counter.value()).isEqualTo(3L);
    }
}
