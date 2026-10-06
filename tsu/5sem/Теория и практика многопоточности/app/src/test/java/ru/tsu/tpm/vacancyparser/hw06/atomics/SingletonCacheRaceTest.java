package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Конкурентное создание значения: много потоков одновременно обращаются к пустому кэшу.
 *
 * <p>Проверка и создание объединены в одну атомарную операцию, поэтому значение обязано создаться
 * ровно один раз, а все потоки — получить ссылку на один и тот же экземпляр.
 */
class SingletonCacheRaceTest {

    private static final int THREADS = 16;

    @Test
    @Timeout(60)
    @DisplayName("16 потоков на пустом кэше: создание ровно одно, экземпляр один")
    void concurrentAccessCreatesOnce() throws InterruptedException {
        SingletonCache<Object> cache = new SingletonCache<>();
        CacheRaceRunner.RaceOutcome outcome = CacheRaceRunner.run(cache, THREADS);

        assertThat(outcome.threads()).isEqualTo(THREADS);
        assertThat(outcome.creations())
                .as("значение обязано создаться ровно один раз, а не по разу на поток")
                .isEqualTo(1);
        assertThat(outcome.distinctInstances())
                .as("все потоки обязаны получить ссылку на один и тот же экземпляр")
                .isEqualTo(1);
        assertThat(outcome.singleInstance()).isTrue();
    }
}
