package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Базовое поведение singleton-кэша: создание при первом обращении и повторное использование.
 */
class SingletonCacheTest {

    @Test
    @DisplayName("Первый вызов создаёт значение, последующие возвращают тот же экземпляр")
    void firstCallCreatesAndLaterCallsReuse() {
        SingletonCache<Object> cache = new SingletonCache<>();

        Object first = cache.get(Object::new);
        Object second = cache.get(Object::new);
        Object third = cache.get(Object::new);

        assertThat(first).isNotNull();
        assertThat(second).isSameAs(first);
        assertThat(third).isSameAs(first);
        assertThat(cache.creations()).isEqualTo(1);
        assertThat(cache.factoryInvocations())
                .as("при непустом кэше фабрика не вызывается вовсе")
                .isEqualTo(1);
    }

    @Test
    @Timeout(60)
    @DisplayName("Альтернатива updateAndGet даёт тот же результат и создаёт значение ровно один раз")
    void updateAndGetVariantAgreesWithPrimary() throws InterruptedException {
        SingletonCache<Object> primary = new SingletonCache<>();
        UpdateAndGetCache<Object> alternative = new UpdateAndGetCache<>();

        CacheRaceRunner.RaceOutcome primaryRace = CacheRaceRunner.run(primary, 16);
        CacheRaceRunner.RaceOutcome alternativeRace = CacheRaceRunner.run(alternative, 16);

        assertThat(primaryRace.createdOnce())
                .as("основная реализация: создано ровно один раз")
                .isTrue();
        assertThat(primaryRace.singleInstance())
                .as("основная реализация: все потоки получили один экземпляр")
                .isTrue();

        assertThat(alternativeRace.createdOnce())
                .as("реализация через updateAndGet: создано ровно один раз")
                .isTrue();
        assertThat(alternativeRace.singleInstance())
                .as("реализация через updateAndGet: все потоки получили один экземпляр")
                .isTrue();
    }
}
