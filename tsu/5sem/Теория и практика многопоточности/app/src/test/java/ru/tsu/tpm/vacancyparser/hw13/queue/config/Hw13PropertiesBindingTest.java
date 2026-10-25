package ru.tsu.tpm.vacancyparser.hw13.queue.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Привязка настроек ДЗ 13 ({@code app.hw13.*}) и их переопределение.
 */
class Hw13PropertiesBindingTest {

    @EnableConfigurationProperties(Hw13Properties.class)
    static class EnableConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(EnableConfig.class);

    @Test
    @DisplayName("Значения по умолчанию связываются")
    void defaultsAreBound() {
        contextRunner.run(context -> {
            Hw13Properties properties = context.getBean(Hw13Properties.class);
            assertThat(properties.getPages()).isEqualTo(24);
            assertThat(properties.getProducers()).isEqualTo(3);
            assertThat(properties.getConsumers()).isEqualTo(3);
            assertThat(properties.getQueueCapacity()).isEqualTo(4);
            assertThat(properties.getComparisonCapacity()).isEqualTo(32);
            assertThat(properties.getPageDelayMillis()).isEqualTo(60L);
        });
    }

    @Test
    @DisplayName("Свойства переопределяют число страниц, потоки, ёмкости и таймауты")
    void propertiesOverrideValues() {
        contextRunner
                .withPropertyValues(
                        "app.hw13.pages=7",
                        "app.hw13.producers=2",
                        "app.hw13.consumers=4",
                        "app.hw13.queue-capacity=2",
                        "app.hw13.comparison-capacity=16",
                        "app.hw13.page-delay-millis=0",
                        "app.hw13.request-timeout-millis=500",
                        "app.hw13.shutdown-timeout-millis=1500",
                        "app.hw13.sample-titles=2",
                        "app.hw13.trace-lines=4")
                .run(context -> {
                    Hw13Properties properties = context.getBean(Hw13Properties.class);
                    assertThat(properties.getPages()).isEqualTo(7);
                    assertThat(properties.getProducers()).isEqualTo(2);
                    assertThat(properties.getConsumers()).isEqualTo(4);
                    assertThat(properties.getQueueCapacity()).isEqualTo(2);
                    assertThat(properties.getComparisonCapacity()).isEqualTo(16);
                    assertThat(properties.getPageDelayMillis()).isZero();
                    assertThat(properties.getRequestTimeoutMillis()).isEqualTo(500L);
                    assertThat(properties.getShutdownTimeoutMillis()).isEqualTo(1_500L);
                    assertThat(properties.getSampleTitles()).isEqualTo(2);
                    assertThat(properties.getTraceLines()).isEqualTo(4);
                });
    }
}
