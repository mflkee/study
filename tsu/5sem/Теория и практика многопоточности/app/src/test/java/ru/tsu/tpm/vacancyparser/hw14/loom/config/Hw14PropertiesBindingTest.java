package ru.tsu.tpm.vacancyparser.hw14.loom.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Привязка настроек ДЗ 14 ({@code app.hw14.*}) и их переопределение.
 */
class Hw14PropertiesBindingTest {

    @EnableConfigurationProperties(Hw14Properties.class)
    static class EnableConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(EnableConfig.class);

    @Test
    @DisplayName("Значения по умолчанию связываются")
    void defaultsAreBound() {
        contextRunner.run(context -> {
            Hw14Properties properties = context.getBean(Hw14Properties.class);
            assertThat(properties.getPages()).isEqualTo(200);
            assertThat(properties.getPageDelayMillis()).isEqualTo(50L);
            assertThat(properties.getFixedPoolSize()).isEqualTo(16);
            assertThat(properties.getScalingTasks()).isEqualTo(10_000);
            assertThat(properties.getScalingDelayMillis()).isEqualTo(50L);
            assertThat(properties.getPlatformPoolSize()).isEqualTo(200);
            assertThat(properties.getPinningTasks()).isEqualTo(96);
            assertThat(properties.getPinningBlockMillis()).isEqualTo(60L);
            assertThat(properties.getTaskTimeoutMillis()).isEqualTo(60_000L);
        });
    }

    @Test
    @DisplayName("Свойства переопределяют страницы, пулы, задачи и таймауты")
    void propertiesOverrideValues() {
        contextRunner
                .withPropertyValues(
                        "app.hw14.pages=7",
                        "app.hw14.page-delay-millis=0",
                        "app.hw14.fixed-pool-size=2",
                        "app.hw14.scaling-tasks=64",
                        "app.hw14.scaling-delay-millis=3",
                        "app.hw14.platform-pool-size=8",
                        "app.hw14.pinning-tasks=10",
                        "app.hw14.pinning-block-millis=5",
                        "app.hw14.request-timeout-millis=500",
                        "app.hw14.task-timeout-millis=1500",
                        "app.hw14.shutdown-timeout-millis=1000",
                        "app.hw14.sample-titles=2")
                .run(context -> {
                    Hw14Properties properties = context.getBean(Hw14Properties.class);
                    assertThat(properties.getPages()).isEqualTo(7);
                    assertThat(properties.getPageDelayMillis()).isZero();
                    assertThat(properties.getFixedPoolSize()).isEqualTo(2);
                    assertThat(properties.getScalingTasks()).isEqualTo(64);
                    assertThat(properties.getScalingDelayMillis()).isEqualTo(3L);
                    assertThat(properties.getPlatformPoolSize()).isEqualTo(8);
                    assertThat(properties.getPinningTasks()).isEqualTo(10);
                    assertThat(properties.getPinningBlockMillis()).isEqualTo(5L);
                    assertThat(properties.getRequestTimeoutMillis()).isEqualTo(500L);
                    assertThat(properties.getTaskTimeoutMillis()).isEqualTo(1_500L);
                    assertThat(properties.getShutdownTimeoutMillis()).isEqualTo(1_000L);
                    assertThat(properties.getSampleTitles()).isEqualTo(2);
                });
    }
}
