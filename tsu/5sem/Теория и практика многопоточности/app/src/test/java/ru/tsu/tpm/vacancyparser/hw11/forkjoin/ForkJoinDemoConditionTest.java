package ru.tsu.tpm.vacancyparser.hw11.forkjoin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Условный запуск демо ДЗ 11: бин появляется только при {@code app.hw=hw11}.
 */
class ForkJoinDemoConditionTest {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "ru.tsu.tpm.vacancyparser.common",
            "ru.tsu.tpm.vacancyparser.hw11.forkjoin"})
    static class ScanConfiguration {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ScanConfiguration.class)
            .withPropertyValues("app.hw11.dataset-size=1000");

    @Test
    @DisplayName("Демо не создаётся без --app.hw")
    void demoIsAbsentWithoutProperty() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ForkJoinDemo.class));
    }

    @Test
    @DisplayName("Демо создаётся только при --app.hw=hw11")
    void demoIsPresentOnlyForHw11() {
        contextRunner.withPropertyValues("app.hw=hw11").run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(ForkJoinDemo.class));
    }

    @Test
    @DisplayName("Демо не создаётся при значениях других домашних заданий")
    void demoIsAbsentForOtherHomeworks() {
        for (String other : List.of("hw01", "hw08", "hw09", "hw10", "hw12", "hw13")) {
            contextRunner.withPropertyValues("app.hw=" + other).run(context -> assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(ForkJoinDemo.class));
        }
    }
}
