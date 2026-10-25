package ru.tsu.tpm.vacancyparser.hw10.completable.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Привязка настроек ДЗ 10 ({@code app.hw10.*}) и их переопределение аргументами запуска.
 */
class Hw10PropertiesBindingTest {

    @EnableConfigurationProperties(Hw10Properties.class)
    static class EnableConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(EnableConfig.class);

    @Test
    @DisplayName("Значения по умолчанию связываются")
    void defaultsAreBound() {
        contextRunner.run(context -> {
            Hw10Properties properties = context.getBean(Hw10Properties.class);
            assertThat(properties.getPoolSize()).isEqualTo(6);
            assertThat(properties.getLatencyMillis()).isEqualTo(120L);
            assertThat(properties.getIds()).hasSize(12);
            assertThat(properties.getDetailsFailId()).isEqualTo("v-07");
            assertThat(properties.getCompanyFailId()).isEqualTo("v-04");
        });
    }

    @Test
    @DisplayName("Свойства переопределяют пул, задержки, таймауты и список идентификаторов")
    void propertiesOverrideValues() {
        contextRunner
                .withPropertyValues(
                        "app.hw10.pool-size=3",
                        "app.hw10.latency-millis=25",
                        "app.hw10.transform-delay-millis=1",
                        "app.hw10.request-timeout-millis=900",
                        "app.hw10.shutdown-timeout-millis=700",
                        "app.hw10.ids[0]=v-77",
                        "app.hw10.ids[1]=v-02",
                        "app.hw10.details-fail-id=v-77",
                        "app.hw10.company-fail-id=v-02",
                        "app.hw10.trace-lines=3")
                .run(context -> {
                    Hw10Properties properties = context.getBean(Hw10Properties.class);
                    assertThat(properties.getPoolSize()).isEqualTo(3);
                    assertThat(properties.getLatencyMillis()).isEqualTo(25L);
                    assertThat(properties.getTransformDelayMillis()).isEqualTo(1L);
                    assertThat(properties.getRequestTimeoutMillis()).isEqualTo(900L);
                    assertThat(properties.getShutdownTimeoutMillis()).isEqualTo(700L);
                    assertThat(properties.getIds()).containsExactly("v-77", "v-02");
                    assertThat(properties.getDetailsFailId()).isEqualTo("v-77");
                    assertThat(properties.getCompanyFailId()).isEqualTo("v-02");
                    assertThat(properties.getTraceLines()).isEqualTo(3);
                });
    }
}
