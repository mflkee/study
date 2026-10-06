package ru.tsu.tpm.vacancyparser.hw08.pool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Условный запуск демо: бин {@link HttpPoolDemo} появляется только при {@code app.hw=hw08}.
 */
class HttpPoolDemoConditionTest {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "ru.tsu.tpm.vacancyparser.common",
            "ru.tsu.tpm.vacancyparser.hw08.pool"})
    static class ScanConfiguration {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ScanConfiguration.class);

    @Test
    @DisplayName("Демо не создаётся без --app.hw")
    void demoIsAbsentWithoutProperty() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(HttpPoolDemo.class));
    }

    @Test
    @DisplayName("Демо создаётся только при --app.hw=hw08")
    void demoIsPresentOnlyForHw08() {
        contextRunner.withPropertyValues("app.hw=hw08").run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(HttpPoolDemo.class));
    }

    @Test
    @DisplayName("Демо не создаётся при значениях других домашних заданий")
    void demoIsAbsentForOtherHomeworks() {
        for (String other : List.of("hw01", "hw02", "hw03", "hw04", "hw05", "hw06", "hw07")) {
            contextRunner.withPropertyValues("app.hw=" + other).run(context -> assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(HttpPoolDemo.class));
        }
    }
}
