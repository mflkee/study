package ru.tsu.tpm.vacancyparser.hw08.pool.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Привязка настроек ДЗ 8 к конфигурации приложения ({@code app.hw08.*}).
 *
 * <p>Проверяется, что параметры действительно переопределяются свойствами/аргументами запуска — без
 * правки кода — и что вложенный блок {@code app.hw08.pool.*} связывается.
 */
class Hw08PropertiesBindingTest {

    @EnableConfigurationProperties(Hw08Properties.class)
    static class EnableConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(EnableConfig.class);

    @Test
    @DisplayName("Значения по умолчанию связываются без свойств")
    void defaultsAreBound() {
        contextRunner.run(context -> {
            Hw08Properties properties = context.getBean(Hw08Properties.class);
            assertThat(properties.getMode()).isEqualTo("local");
            assertThat(properties.getUrls()).hasSize(20);
            assertThat(properties.getPool().getCoreSize()).isPositive();
        });
    }

    @Test
    @DisplayName("Свойства переопределяют режим, размеры пула, ёмкость и таймауты")
    void propertiesOverrideValues() {
        contextRunner
                .withPropertyValues(
                        "app.hw08.mode=mixed",
                        "app.hw08.pool.core-size=6",
                        "app.hw08.pool.max-size=12",
                        "app.hw08.pool.queue-capacity=3",
                        "app.hw08.pool.request-timeout-millis=1500",
                        "app.hw08.urls[0]=http://one",
                        "app.hw08.urls[1]=http://two")
                .run(context -> {
                    Hw08Properties properties = context.getBean(Hw08Properties.class);
                    assertThat(properties.getMode()).isEqualTo("mixed");
                    assertThat(properties.getPool().getCoreSize()).isEqualTo(6);
                    assertThat(properties.getPool().getMaxSize()).isEqualTo(12);
                    assertThat(properties.getPool().getQueueCapacity()).isEqualTo(3);
                    assertThat(properties.getPool().getRequestTimeoutMillis()).isEqualTo(1500L);
                    assertThat(properties.getUrls()).containsExactly("http://one", "http://two");

                    RunConfig config = RunConfig.from(properties);
                    assertThat(config.mode()).isEqualTo(WorkMode.MIXED);
                    assertThat(config.coreSize()).isEqualTo(6);
                });
    }
}
