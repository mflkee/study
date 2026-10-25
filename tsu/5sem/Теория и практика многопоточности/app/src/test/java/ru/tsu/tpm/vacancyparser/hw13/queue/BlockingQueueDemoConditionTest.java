package ru.tsu.tpm.vacancyparser.hw13.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Условный запуск демо ДЗ 13: бин появляется только при {@code app.hw=hw13}.
 */
class BlockingQueueDemoConditionTest {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "ru.tsu.tpm.vacancyparser.common",
            "ru.tsu.tpm.vacancyparser.hw13.queue"})
    static class ScanConfiguration {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ScanConfiguration.class);

    @Test
    @DisplayName("Демо не создаётся без --app.hw")
    void demoIsAbsentWithoutProperty() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(BlockingQueueDemo.class));
    }

    @Test
    @DisplayName("Демо создаётся только при --app.hw=hw13")
    void demoIsPresentOnlyForHw13() {
        contextRunner.withPropertyValues("app.hw=hw13").run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(BlockingQueueDemo.class));
    }

    @Test
    @DisplayName("Демо не создаётся при значениях других домашних заданий")
    void demoIsAbsentForOtherHomeworks() {
        for (String other : List.of("hw01", "hw08", "hw09", "hw10", "hw11", "hw12")) {
            contextRunner.withPropertyValues("app.hw=" + other).run(context -> assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(BlockingQueueDemo.class));
        }
    }
}
