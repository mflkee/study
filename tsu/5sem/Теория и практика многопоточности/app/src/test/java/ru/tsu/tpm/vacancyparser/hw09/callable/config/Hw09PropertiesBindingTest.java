package ru.tsu.tpm.vacancyparser.hw09.callable.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Привязка настроек ДЗ 9 ({@code app.hw09.*}) и их переопределение.
 */
class Hw09PropertiesBindingTest {

    @EnableConfigurationProperties(Hw09Properties.class)
    static class EnableConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(EnableConfig.class);

    @Test
    @DisplayName("Значения по умолчанию связываются")
    void defaultsAreBound() {
        contextRunner.run(context -> {
            Hw09Properties properties = context.getBean(Hw09Properties.class);
            assertThat(properties.getPeriodSeconds()).isEqualTo(2L);
            assertThat(properties.getCycles()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("Свойства переопределяют период и число циклов")
    void propertiesOverrideValues() {
        contextRunner
                .withPropertyValues("app.hw09.period-seconds=1", "app.hw09.cycles=2")
                .run(context -> {
                    Hw09Properties properties = context.getBean(Hw09Properties.class);
                    assertThat(properties.getPeriodSeconds()).isEqualTo(1L);
                    assertThat(properties.getCycles()).isEqualTo(2);
                });
    }
}
