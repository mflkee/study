package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Проверка условного запуска демо: бин {@link ThreadLifecycleDemo} должен появляться
 * только при {@code app.hw=hw02}.
 *
 * <p>Контекст собирается сканированием компонентов без полной автоконфигурации Spring Boot:
 * для проверки условия web-контекст не нужен, а метод {@code run()} здесь не вызывается,
 * поэтому демонстрация не выполняется и процесс не завершается.
 */
class ThreadLifecycleDemoConditionTest {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "ru.tsu.tpm.vacancyparser.common",
            "ru.tsu.tpm.vacancyparser.hw02.lifecycle"})
    static class ScanConfiguration {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ScanConfiguration.class);

    @Test
    @DisplayName("Демо не создаётся без --app.hw")
    void demoIsAbsentWithoutProperty() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ThreadLifecycleDemo.class)
                .hasSingleBean(LifecycleObservationService.class)
                .hasSingleBean(ThreadStateObserver.class));
    }

    @Test
    @DisplayName("Демо создаётся при --app.hw=hw02")
    void demoIsPresentWithHw02Property() {
        contextRunner.withPropertyValues("app.hw=hw02").run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(ThreadLifecycleDemo.class));
    }

    @Test
    @DisplayName("Демо не создаётся при запуске другого домашнего задания")
    void demoIsAbsentForAnotherHomework() {
        contextRunner.withPropertyValues("app.hw=hw03").run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ThreadLifecycleDemo.class));
    }

    @Test
    @DisplayName("Демо не создаётся при значении --app.hw=hw01")
    void demoIsAbsentForHw01() {
        contextRunner.withPropertyValues("app.hw=hw01").run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ThreadLifecycleDemo.class));
    }
}
