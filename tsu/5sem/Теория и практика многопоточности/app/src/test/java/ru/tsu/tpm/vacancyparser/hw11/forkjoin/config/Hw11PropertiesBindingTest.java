package ru.tsu.tpm.vacancyparser.hw11.forkjoin.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountCriteria;

/**
 * Привязка настроек ДЗ 11 ({@code app.hw11.*}) и критерий, собранный из них.
 */
class Hw11PropertiesBindingTest {

    @EnableConfigurationProperties(Hw11Properties.class)
    static class EnableConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(EnableConfig.class);

    @Test
    @DisplayName("Значения по умолчанию связываются, критерий собирается")
    void defaultsAreBound() {
        contextRunner.run(context -> {
            Hw11Properties properties = context.getBean(Hw11Properties.class);
            assertThat(properties.getDatasetSize()).isEqualTo(1_000_000);
            assertThat(properties.getBatchSize()).isEqualTo(5_000);
            assertThat(properties.getPoolSize()).isEqualTo(8);
            assertThat(properties.getBatchSizes()).containsExactly(1_000, 5_000, 20_000, 100_000);
            assertThat(properties.getPoolSizes()).containsExactly(2, 4, 8);
            assertThat(properties.getRepeats()).isEqualTo(2);

            CountCriteria criteria = properties.criteria();
            assertThat(criteria.city()).isEqualTo("Томск");
            assertThat(criteria.minSalary()).isEqualTo(100_000);
            assertThat(criteria.keyword()).isEqualTo("java");
        });
    }

    @Test
    @DisplayName("Свойства переопределяют набор, партии, параллелизм и критерий")
    void propertiesOverrideValues() {
        contextRunner
                .withPropertyValues(
                        "app.hw11.dataset-size=5000",
                        "app.hw11.batch-size=250",
                        "app.hw11.pool-size=3",
                        "app.hw11.batch-sizes=100,200",
                        "app.hw11.pool-sizes=1,2",
                        "app.hw11.repeats=1",
                        "app.hw11.seed=7",
                        "app.hw11.criteria-city=Омск",
                        "app.hw11.criteria-min-salary=50000",
                        "app.hw11.criteria-keyword=spring")
                .run(context -> {
                    Hw11Properties properties = context.getBean(Hw11Properties.class);
                    assertThat(properties.getDatasetSize()).isEqualTo(5_000);
                    assertThat(properties.getBatchSize()).isEqualTo(250);
                    assertThat(properties.getPoolSize()).isEqualTo(3);
                    assertThat(properties.getBatchSizes()).containsExactly(100, 200);
                    assertThat(properties.getPoolSizes()).containsExactly(1, 2);
                    assertThat(properties.getRepeats()).isEqualTo(1);
                    assertThat(properties.getSeed()).isEqualTo(7L);

                    CountCriteria criteria = properties.criteria();
                    assertThat(criteria.city()).isEqualTo("Омск");
                    assertThat(criteria.minSalary()).isEqualTo(50_000);
                    assertThat(criteria.keyword()).isEqualTo("spring");
                });
    }
}
