package ru.tsu.tpm.vacancyparser.hw12.collections.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Привязка настроек ДЗ 12 ({@code app.hw12.*}) и их переопределение.
 */
class Hw12PropertiesBindingTest {

    @EnableConfigurationProperties(Hw12Properties.class)
    static class EnableConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(EnableConfig.class);

    @Test
    @DisplayName("Значения по умолчанию связываются")
    void defaultsAreBound() {
        contextRunner.run(context -> {
            Hw12Properties properties = context.getBean(Hw12Properties.class);
            assertThat(properties.getThreads()).isEqualTo(8);
            assertThat(properties.getKeys()).isEqualTo(200);
            assertThat(properties.getOpsPerThread()).isEqualTo(2_000);
            assertThat(properties.getTtlMillis()).isEqualTo(300L);
            assertThat(properties.getCacheCapacity()).isEqualTo(256);
            assertThat(properties.getEvictionCapacity()).isEqualTo(128);
            assertThat(properties.getSubscribers()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("Свойства переопределяют нагрузку, кэш, журнал и замеры")
    void propertiesOverrideValues() {
        contextRunner
                .withPropertyValues(
                        "app.hw12.threads=4",
                        "app.hw12.keys=50",
                        "app.hw12.ops-per-thread=100",
                        "app.hw12.loader-delay-millis=0",
                        "app.hw12.ttl-millis=50",
                        "app.hw12.cache-capacity=16",
                        "app.hw12.log-limit=20",
                        "app.hw12.subscribers=2",
                        "app.hw12.ttl-keys=3",
                        "app.hw12.eviction-capacity=8",
                        "app.hw12.eviction-insertions=20",
                        "app.hw12.benchmark-threads=2",
                        "app.hw12.benchmark-keys=10",
                        "app.hw12.benchmark-ops-per-thread=1000",
                        "app.hw12.top-size=3")
                .run(context -> {
                    Hw12Properties properties = context.getBean(Hw12Properties.class);
                    assertThat(properties.getThreads()).isEqualTo(4);
                    assertThat(properties.getKeys()).isEqualTo(50);
                    assertThat(properties.getOpsPerThread()).isEqualTo(100);
                    assertThat(properties.getLoaderDelayMillis()).isZero();
                    assertThat(properties.getTtlMillis()).isEqualTo(50L);
                    assertThat(properties.getCacheCapacity()).isEqualTo(16);
                    assertThat(properties.getLogLimit()).isEqualTo(20);
                    assertThat(properties.getSubscribers()).isEqualTo(2);
                    assertThat(properties.getTtlKeys()).isEqualTo(3);
                    assertThat(properties.getEvictionCapacity()).isEqualTo(8);
                    assertThat(properties.getEvictionInsertions()).isEqualTo(20);
                    assertThat(properties.getBenchmarkThreads()).isEqualTo(2);
                    assertThat(properties.getBenchmarkKeys()).isEqualTo(10);
                    assertThat(properties.getBenchmarkOpsPerThread()).isEqualTo(1_000);
                    assertThat(properties.getTopSize()).isEqualTo(3);
                });
    }
}
