package ru.tsu.tpm.vacancyparser.hw07.deadlock.starvation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw07.deadlock.SourceScan;

/**
 * Starvation: контрпример через приоритеты и корректное решение через честную очередь.
 */
class StarvationScenarioTest {

    private static final int WORKERS = 4;

    @Test
    @Timeout(30)
    @DisplayName("Некорректный вариант: числа захватов по потокам и оговорка о негарантированности")
    void unfairVariantReportsFactsAndCaveat() throws InterruptedException {
        StarvationScenario.Outcome outcome = StarvationScenario.runUnfair(WORKERS, 400L);

        assertThat(outcome.workers()).isEqualTo(WORKERS);
        assertThat(outcome.workerNames()).hasSize(WORKERS).contains(StarvationScenario.VICTIM_NAME);
        assertThat(outcome.acquisitions()).hasSize(WORKERS);
        assertThat(outcome.max()).isGreaterThanOrEqualTo(outcome.min());
        assertThat(outcome.caveat())
                .as("некорректность должна быть оговорена явно")
                .contains("НЕ гарантирован")
                .contains("приоритет");
    }

    @Test
    @Timeout(30)
    @DisplayName("Корректный вариант: «жертва» гарантированно получает ресурс за окно наблюдения")
    void fairVariantGuaranteesVictimAccess() throws InterruptedException {
        StarvationScenario.Outcome outcome = FairStarvationScenario.run(WORKERS, 500L);

        assertThat(outcome.fair()).isTrue();
        assertThat(outcome.victimAcquisitions())
                .as("честная очередь обязана дать жертве не меньше заданного числа захватов")
                .isGreaterThanOrEqualTo(StarvationScenario.FAIR_MIN_VICTIM_ACQUISITIONS);
        assertThat(outcome.acquisitions()).allSatisfy(count -> assertThat(count).isPositive());
        assertThat(outcome.caveat()).contains("честная очередь").contains("не используются");
    }

    @Test
    @DisplayName("Корректное средство не использует приоритеты и уступку процессора")
    void fairImplementationDoesNotRelyOnPriorities() {
        String fair = SourceScan.readMain(
                "ru/tsu/tpm/vacancyparser/hw07/deadlock/starvation/FairStarvationScenario.java");

        assertThat(fair).contains("new ReentrantLock(true)");
        assertThat(fair).doesNotContain("setPriority");
        assertThat(fair).doesNotContain("Thread.yield");
    }
}
