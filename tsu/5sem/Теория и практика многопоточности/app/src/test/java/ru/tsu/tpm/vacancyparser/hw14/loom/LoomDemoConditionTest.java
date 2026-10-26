package ru.tsu.tpm.vacancyparser.hw14.loom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Условный запуск демо ДЗ 14: бин появляется только при {@code app.hw=hw14}.
 */
class LoomDemoConditionTest {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "ru.tsu.tpm.vacancyparser.common",
            "ru.tsu.tpm.vacancyparser.hw14.loom"})
    static class ScanConfiguration {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ScanConfiguration.class);

    @Test
    @DisplayName("Демо создаётся только при --app.hw=hw14")
    void demoIsPresentOnlyForHw14() {
        contextRunner.withPropertyValues("app.hw=hw14").run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(LoomDemo.class));

        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(LoomDemo.class));

        for (String other : List.of("hw01", "hw08", "hw10", "hw12", "hw13")) {
            contextRunner.withPropertyValues("app.hw=" + other).run(context -> assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(LoomDemo.class));
        }
    }
}
