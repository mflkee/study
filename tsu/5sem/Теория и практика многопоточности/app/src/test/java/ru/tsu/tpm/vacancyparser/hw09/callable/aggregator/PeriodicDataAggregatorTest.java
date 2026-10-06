package ru.tsu.tpm.vacancyparser.hw09.callable.aggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Сервис периодической агрегации: полнота цикла, изоляция сбоев, периодичность и остановка.
 */
class PeriodicDataAggregatorTest {

    private static final List<String> ENTITIES = List.of("EUR", "USD", "GBP", "JPY");

    private static PeriodicDataAggregator aggregator(EntitySource source, DataProvider provider, long period, int workers) {
        return new PeriodicDataAggregator(source, provider, null, period, workers, "hw09-test-agg-");
    }

    private static DataProvider okProvider() {
        return id -> new EntityData(id, "v-" + id);
    }

    @Test
    @Timeout(30)
    @DisplayName("Успешный цикл: данные по всем сущностям, в порядке списка")
    void successfulCycleCollectsAll() {
        PeriodicDataAggregator aggregator = aggregator(() -> ENTITIES, okProvider(), 1_000L, 4);
        try {
            CycleReport report = aggregator.runCycle();

            assertThat(report.cycle()).isEqualTo(1L);
            assertThat(report.requestedIds()).isEqualTo(ENTITIES);
            assertThat(report.successes()).hasSize(4);
            assertThat(report.successes()).extracting(EntityData::id).containsExactlyElementsOf(ENTITIES);
            assertThat(report.failures()).isEmpty();
            assertThat(report.sourceError()).isNull();
            assertThat(report.fullySuccessful()).isTrue();
        } finally {
            aggregator.stop(2_000L);
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Частичный сбой: сбойная сущность изолирована, остальные собраны")
    void partialFailureIsIsolated() {
        DataProvider provider = id -> {
            if (id.equals("USD")) {
                throw new IllegalStateException("источник недоступен: " + id);
            }
            return new EntityData(id, "v-" + id);
        };
        PeriodicDataAggregator aggregator = aggregator(() -> ENTITIES, provider, 1_000L, 4);
        try {
            CycleReport report = aggregator.runCycle();

            assertThat(report.requestedIds()).isEqualTo(ENTITIES);
            assertThat(report.successes()).hasSize(3);
            assertThat(report.failures()).hasSize(1);
            assertThat(report.failures().get(0).id()).isEqualTo("USD");
            assertThat(report.failures().get(0).reason()).contains("IllegalStateException");
            assertThat(report.fullySuccessful()).isFalse();
        } finally {
            aggregator.stop(2_000L);
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Недоступность источника списка: цикл пуст, следующий цикл восстанавливается")
    void sourceFailureDoesNotStopTheService() {
        AtomicBoolean down = new AtomicBoolean(true);
        EntitySource source = () -> {
            if (down.get()) {
                throw new IllegalStateException("источник списка недоступен");
            }
            return ENTITIES;
        };
        PeriodicDataAggregator aggregator = aggregator(source, okProvider(), 1_000L, 4);
        try {
            CycleReport failed = aggregator.runCycle();
            assertThat(failed.sourceError()).isNotNull();
            assertThat(failed.requestedIds()).isEmpty();
            assertThat(failed.successes()).isEmpty();

            down.set(false);
            CycleReport recovered = aggregator.runCycle();
            assertThat(recovered.sourceError()).isNull();
            assertThat(recovered.successes()).hasSize(4);
            assertThat(aggregator.cycles()).isEqualTo(2L);
        } finally {
            aggregator.stop(2_000L);
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Запросы по сущностям выполняются параллельно")
    void requestsRunInParallel() {
        DataProvider slow = id -> {
            Thread.sleep(200L);
            return new EntityData(id, "v-" + id);
        };
        PeriodicDataAggregator aggregator = aggregator(() -> ENTITIES, slow, 1_000L, 4);
        try {
            CycleReport report = aggregator.runCycle();

            assertThat(report.durationMillis())
                    .as("длительность цикла (%.1f мс) меньше последовательной суммы 800 мс", report.durationMillis())
                    .isLessThan(700.0);
            assertThat(report.successes()).hasSize(4);
        } finally {
            aggregator.stop(2_000L);
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Периодические циклы: несколько подряд, список берётся каждый раз заново")
    void cyclesRepeatPeriodically() throws InterruptedException {
        PeriodicDataAggregator aggregator = aggregator(() -> ENTITIES, okProvider(), 30L, 4);
        try {
            aggregator.start();
            assertThat(aggregator.awaitCycles(3, 3_000L)).isTrue();
            assertThat(aggregator.cycles()).isGreaterThanOrEqualTo(3L);
            assertThat(aggregator.reports()).hasSizeGreaterThanOrEqualTo(3);
        } finally {
            aggregator.stop(2_000L);
        }
        assertThat(aggregator.isTerminated()).isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("После остановки новые циклы не запускаются")
    void stopPreventsFurtherCycles() throws InterruptedException {
        PeriodicDataAggregator aggregator = aggregator(() -> ENTITIES, okProvider(), 30L, 4);
        aggregator.start();
        assertThat(aggregator.awaitCycles(2, 3_000L)).isTrue();
        aggregator.stop(2_000L);

        long cyclesAfterStop = aggregator.cycles();
        Thread.sleep(150L);
        assertThat(aggregator.cycles())
                .as("после остановки счётчик циклов не растёт")
                .isEqualTo(cyclesAfterStop);
        assertThat(aggregator.isTerminated()).isTrue();
    }

    @Test
    @DisplayName("Некорректные параметры отвергаются")
    void invalidParametersRejected() {
        assertThatThrownBy(() -> aggregator(() -> ENTITIES, okProvider(), 0L, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> aggregator(() -> ENTITIES, okProvider(), 100L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
