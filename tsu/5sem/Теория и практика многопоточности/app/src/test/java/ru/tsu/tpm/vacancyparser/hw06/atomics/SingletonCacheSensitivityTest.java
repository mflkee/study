package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Чувствительность: раздельные проверка и создание (check-then-act) действительно ломаются.
 *
 * <p>На учебном дефектном кэше при конкурентном обращении создаётся больше одного экземпляра, и разные
 * потоки получают разные ссылки — это и есть доказательство, что {@code AtomicReference} сам по себе
 * не делает пару «проверить и создать» атомарной. Корректная реализация в том же прогоне создаёт
 * значение ровно один раз.
 *
 * <p>Дефект зависит от взаимного положения потоков, поэтому попытки повторяются до первого наблюдения.
 */
class SingletonCacheSensitivityTest {

    private static final int THREADS = 16;
    private static final int ATTEMPTS = 10;

    @Test
    @Timeout(120)
    @DisplayName("check-then-act создаёт значение больше одного раза и раздаёт разные экземпляры")
    void brokenCacheCreatesMoreThanOnce() throws InterruptedException {
        CacheRaceRunner.RaceOutcome defect = null;
        for (int attempt = 1; attempt <= ATTEMPTS && defect == null; attempt++) {
            CacheRaceRunner.RaceOutcome outcome =
                    CacheRaceRunner.run(new BrokenSingletonCache<>(), THREADS);
            if (outcome.creations() > 1 || outcome.distinctInstances() > 1) {
                defect = outcome;
            }
        }

        assertThat(defect)
                .as("за %d попыток дефект check-then-act обязан проявиться", ATTEMPTS)
                .isNotNull();
        assertThat(defect.creations())
                .as("созданий больше одного: %d", defect.creations())
                .isGreaterThan(1);
        assertThat(defect.singleInstance())
                .as("разные потоки получили разные экземпляры")
                .isFalse();
    }

    @Test
    @Timeout(60)
    @DisplayName("Корректная реализация в том же прогоне создаёт значение ровно один раз")
    void correctCachePassesTheSameRun() throws InterruptedException {
        CacheRaceRunner.RaceOutcome outcome =
                CacheRaceRunner.run(new SingletonCache<>(), THREADS);

        assertThat(outcome.creations()).isEqualTo(1);
        assertThat(outcome.singleInstance()).isTrue();
    }
}
