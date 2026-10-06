package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Печать ДЗ 6: строки — чистые функции от результатов, поэтому проверяются без запуска нагрузки.
 */
class AtomicsReportTest {

    @Test
    @DisplayName("Блок остановки по флагу печатает итог; при срыве лимита — дамп потоков")
    void flagStopBlockPrintsOutcomeAndDump() {
        FlagStopScenario.StopOutcome ok = new FlagStopScenario.StopOutcome(true, 3_000_000L, 1_234L, null);
        String text = String.join("\n", AtomicsReport.flagStopBlock(ok));

        assertThat(text)
                .contains(FlagStopScenario.WORKER_NAME)
                .contains("итераций до запроса завершения: 1234")
                .contains("поток завершился по volatile-флагу: да")
                .contains("дамп потоков не потребовался");

        FlagStopScenario.StopOutcome stuck =
                new FlagStopScenario.StopOutcome(false, 2_000_000_000L, 999L, "Дамп потоков: тест");
        String stuckText = String.join("\n", AtomicsReport.flagStopBlock(stuck));

        assertThat(stuckText)
                .contains("поток завершился по volatile-флагу: НЕТ")
                .contains("поток НЕ завершился в отведённое время — дамп потоков:")
                .contains("Дамп потоков: тест");
    }

    @Test
    @DisplayName("Блок кэша показывает создания и экземпляры для корректной и дефектной реализаций")
    void cacheBlockReportsBothImplementations() {
        CacheRaceRunner.RaceOutcome correct = new CacheRaceRunner.RaceOutcome(16, 1, 3, 1, 1_000L);
        CacheRaceRunner.RaceOutcome broken = new CacheRaceRunner.RaceOutcome(16, 5, 5, 3, 1_000L);

        String text = String.join("\n", AtomicsReport.cacheBlock(correct, broken));

        assertThat(text)
                .contains("корректная реализация (compareAndSet): созданий = 1")
                .contains("учебный дефект (check-then-act): созданий = 5")
                .contains("все потоки получили один экземпляр: да")
                .contains("дефектная реализация создала значение более одного раза: да");
    }

    @Test
    @DisplayName("Блок механизмов печатает состояние всех трёх механизмов")
    void mechanismsBlockPrintsAllThree() {
        AtomicsBenchmark.MechanismsOutcome outcome = new AtomicsBenchmark.MechanismsOutcome(
                4, 20_000, true, 1_000_000L, 12_345L, 80_000L, 80_000L, 1, 1, List.of(), 5_000_000L);

        String text = String.join("\n", AtomicsReport.mechanismsBlock(outcome));

        assertThat(text)
                .contains("остановка по volatile-флагу: завершился = да")
                .contains("AtomicInteger: значение = 80000 из 80000 (точно)")
                .contains("singleton-кэш: созданий = 1, различных экземпляров = 1 (один экземпляр)")
                .contains("живых потоков демонстрации после прогона: []");
    }

    @Test
    @DisplayName("Вывод о разнице не объявляет победителя при пересечении интервалов")
    void conclusionAvoidsWinnerWhenOverlapping() {
        AtomicsBenchmark.CounterComparison comparison = new AtomicsBenchmark.CounterComparison(
                4,
                20_000,
                80_000L,
                List.of(
                        new AtomicsBenchmark.CounterRun(CounterVariant.MONITOR.label(), 30_000_000L, 80_000L, 80_000L),
                        new AtomicsBenchmark.CounterRun(CounterVariant.MONITOR.label(), 20_000_000L, 80_000L, 80_000L),
                        new AtomicsBenchmark.CounterRun(CounterVariant.ATOMIC.label(), 25_000_000L, 80_000L, 80_000L),
                        new AtomicsBenchmark.CounterRun(CounterVariant.ATOMIC.label(), 21_000_000L, 80_000L, 80_000L)));

        assertThat(AtomicsReport.conclusion(comparison))
                .as("интервалы [20,30] и [21,25] пересекаются — победителя быть не должно")
                .contains("укладывается в разброс повторов")
                .contains("равноценны");
    }

    @Test
    @DisplayName("Блок инварианта счётчика отмечает расхождение, если оно есть")
    void counterInvariantBlockMarksViolation() {
        String ok = String.join("\n", AtomicsReport.counterInvariantBlock(8, 10_000, 80_000L, 80_000L));
        assertThat(ok).contains("инвариант выполнен").contains("расхождений: 0");

        String broken = String.join("\n", AtomicsReport.counterInvariantBlock(8, 10_000, 79_001L, 80_000L));
        assertThat(broken).contains("ИНВАРИАНТ НАРУШЕН").contains("расхождений: 999");
    }
}
