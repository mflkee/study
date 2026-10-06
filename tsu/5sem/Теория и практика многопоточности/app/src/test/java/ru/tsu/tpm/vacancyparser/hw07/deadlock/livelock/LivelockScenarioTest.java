package ru.tsu.tpm.vacancyparser.hw07.deadlock.livelock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Livelock: активные попытки без продвижения и детектор, отличающий это от обычной работы.
 */
class LivelockScenarioTest {

    private static final long WINDOW_MILLIS = 300L;

    @Test
    @DisplayName("Детектор: нет успешных итераций при растущих попытках — livelock; есть успехи — нет")
    void detectorDistinguishesProgress() {
        LivelockDetector.Verdict noProgress = LivelockDetector.evaluate(10_000L, 0L);
        assertThat(noProgress.livelock()).isTrue();
        assertThat(noProgress.reason()).contains("отсутствие продвижения");

        LivelockDetector.Verdict progress = LivelockDetector.evaluate(10_000L, 42L);
        assertThat(progress.livelock()).isFalse();
        assertThat(progress.reason()).contains("обычная работа");

        assertThat(LivelockDetector.evaluate(0L, 0L).livelock()).isFalse();
    }

    @Test
    @Timeout(30)
    @DisplayName("Livelock-конфигурация: попытки растут, успешных итераций нет, потоки RUNNABLE")
    void livelockConfigShowsNoProgress() throws InterruptedException {
        long start = System.nanoTime();
        LivelockScenario.Outcome outcome = LivelockScenario.run(LivelockConfig.LIVELOCK, WINDOW_MILLIS);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertThat(outcome.totalAttempts()).as("попытки обязаны быть").isPositive();
        assertThat(outcome.successes()).as("в livelock успешных итераций нет").isZero();
        assertThat(outcome.verdict().livelock()).isTrue();
        assertThat(outcome.attemptsPerWorker()).hasSize(2);
        assertThat(outcome.attemptsPerWorker()).allSatisfy(count -> assertThat(count).isPositive());
        assertThat(outcome.threadStates()).allSatisfy(state -> assertThat(state).contains("RUNNABLE"));
        assertThat(elapsedMillis).as("сценарий ограничен окном наблюдения").isLessThan(10_000L);
    }

    @Test
    @Timeout(30)
    @DisplayName("Нормальная конфигурация: успешные итерации происходят, детектор молчит")
    void normalConfigMakesProgress() throws InterruptedException {
        LivelockScenario.Outcome outcome = LivelockScenario.run(LivelockConfig.NORMAL, WINDOW_MILLIS);

        assertThat(outcome.totalAttempts()).isPositive();
        assertThat(outcome.successes()).as("детерминированный порядок даёт прогресс").isPositive();
        assertThat(outcome.verdict().livelock()).isFalse();
    }
}
