package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Условный запуск демо: бин {@link LocksConditionsDemo} появляется только при {@code app.hw=hw05}.
 *
 * <p>Контекст собирается без полной автоконфигурации Spring Boot, а {@code run()} не вызывается:
 * демонстрация делает нагрузку и закрывает контекст, для проверки условия этого не нужно.
 */
class LocksConditionsDemoConditionTest {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "ru.tsu.tpm.vacancyparser.common",
            "ru.tsu.tpm.vacancyparser.hw05.locks"})
    static class ScanConfiguration {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ScanConfiguration.class);

    @Test
    @DisplayName("Демо не создаётся без --app.hw")
    void demoIsAbsentWithoutProperty() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(LocksConditionsDemo.class));
    }

    @Test
    @DisplayName("Демо создаётся только при --app.hw=hw05")
    void demoIsPresentOnlyForHw05() {
        contextRunner.withPropertyValues("app.hw=hw05").run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(LocksConditionsDemo.class));
    }

    @Test
    @DisplayName("Демо не создаётся при значениях других домашних заданий")
    void demoIsAbsentForOtherHomeworks() {
        for (String other : List.of("hw01", "hw02", "hw03", "hw04", "hw06")) {
            contextRunner.withPropertyValues("app.hw=" + other).run(context -> assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(LocksConditionsDemo.class));
        }
    }
}
