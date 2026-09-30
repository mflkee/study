package ru.tsu.tpm.vacancyparser.hw03.parallel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Проверка условного запуска демо: бин {@link ParallelVsConcurrencyDemo} появляется только при
 * {@code app.hw=hw03}.
 *
 * <p>Контекст собирается сканированием компонентов без полной автоконфигурации Spring Boot, а
 * метод {@code run()} не вызывается: демонстрация измеряет миллион элементов и закрывает контекст,
 * для проверки условия этого не требуется.
 */
class ParallelVsConcurrencyDemoConditionTest {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "ru.tsu.tpm.vacancyparser.common",
            "ru.tsu.tpm.vacancyparser.hw03.parallel"})
    static class ScanConfiguration {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ScanConfiguration.class);

    @Test
    @DisplayName("Демо не создаётся без --app.hw")
    void demoIsAbsentWithoutProperty() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ParallelVsConcurrencyDemo.class)
                .hasSingleBean(BenchmarkService.class));
    }

    @Test
    @DisplayName("Демо создаётся при --app.hw=hw03")
    void demoIsPresentWithHw03Property() {
        contextRunner.withPropertyValues("app.hw=hw03").run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(ParallelVsConcurrencyDemo.class));
    }

    @Test
    @DisplayName("Демо не создаётся при --app.hw=hw02")
    void demoIsAbsentForHw02() {
        contextRunner.withPropertyValues("app.hw=hw02").run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ParallelVsConcurrencyDemo.class));
    }

    @Test
    @DisplayName("Демо не создаётся при --app.hw=hw04")
    void demoIsAbsentForHw04() {
        contextRunner.withPropertyValues("app.hw=hw04").run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ParallelVsConcurrencyDemo.class));
    }

    @Test
    @DisplayName("Параметры демо берутся из свойств, диапазон выводится из размера")
    void demoReadsParametersFromProperties() {
        contextRunner.withPropertyValues("app.hw=hw03", "app.hw03.size=1000", "app.hw03.repeats=2")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ParallelVsConcurrencyDemo.class);

                    BenchmarkConfig applied = context.getBean(ParallelVsConcurrencyDemo.class).config();
                    assertThat(applied.size()).isEqualTo(1_000);
                    assertThat(applied.repeats()).isEqualTo(2);
                    assertThat(applied.upperBound())
                            .as("диапазон значений выводится из размера и остаётся вдвое шире")
                            .isEqualTo(2_000);
                });
    }

    @Test
    @DisplayName("По умолчанию демо настроено на миллион элементов и пять повторов из текста задания")
    void demoDefaultsMatchAssignment() {
        contextRunner.withPropertyValues("app.hw=hw03").run(context -> {
            assertThat(context).hasNotFailed();

            BenchmarkConfig applied = context.getBean(ParallelVsConcurrencyDemo.class).config();
            assertThat(applied.size()).isEqualTo(1_000_000);
            assertThat(applied.repeats()).isEqualTo(5);
            assertThat(applied.upperBound()).isEqualTo(2_000_000);
        });
    }
}
