package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

/**
 * Устойчивость конкурентного создания: десять прогонов подряд, создание ровно одно в каждом.
 *
 * <p>Один удачный прогон мог бы быть случайностью планировщика; повтор показывает, что объединение
 * проверки и создания действительно атомарно, а не «повезло один раз».
 */
class SingletonCacheRepeatedTest {

    private static final int THREADS = 16;

    @RepeatedTest(10)
    @DisplayName("10 повторов: создание значения ровно один раз в каждом прогоне")
    @Timeout(120)
    void creationsIsOneInEveryRun() throws InterruptedException {
        SingletonCache<Object> cache = new SingletonCache<>();
        CacheRaceRunner.RaceOutcome outcome = CacheRaceRunner.run(cache, THREADS);

        assertThat(outcome.creations())
                .as("в каждом прогоне: %d", outcome.creations())
                .isEqualTo(1);
        assertThat(outcome.distinctInstances()).isEqualTo(1);
    }
}
