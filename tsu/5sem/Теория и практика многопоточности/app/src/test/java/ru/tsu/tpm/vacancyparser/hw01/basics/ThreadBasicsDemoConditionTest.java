package ru.tsu.tpm.vacancyparser.hw01.basics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Проверка условного запуска демо: бин {@link ThreadBasicsDemo} должен появляться
 * только при {@code app.hw=hw01}.
 *
 * <p>Контекст собирается сканированием компонентов без полной автоконфигурации Spring Boot:
 * для проверки условия web-контекст не нужен, а метод {@code run()} здесь не вызывается,
 * поэтому демо не завершает процесс.
 */
class ThreadBasicsDemoConditionTest {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "ru.tsu.tpm.vacancyparser.common",
            "ru.tsu.tpm.vacancyparser.hw01.basics"})
    static class ScanConfiguration {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ScanConfiguration.class);

    @Test
    @DisplayName("Демо не создаётся без --app.hw")
    void demoIsAbsentWithoutProperty() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ThreadBasicsDemo.class)
                .hasSingleBean(ThreadBasicsService.class));
    }

    @Test
    @DisplayName("Демо создаётся при --app.hw=hw01")
    void demoIsPresentWithHw01Property() {
        contextRunner.withPropertyValues("app.hw=hw01").run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(ThreadBasicsDemo.class));
    }

    @Test
    @DisplayName("Демо не создаётся при значении другого домашнего задания")
    void demoIsAbsentForAnotherHomework() {
        contextRunner.withPropertyValues("app.hw=hw02").run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ThreadBasicsDemo.class));
    }
}
