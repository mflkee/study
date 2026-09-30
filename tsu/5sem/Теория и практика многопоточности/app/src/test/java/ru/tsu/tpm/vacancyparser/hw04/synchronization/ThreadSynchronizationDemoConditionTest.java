package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Проверки условного запуска демо и выводов замеров.
 *
 * <p>Контекст собирается без полной автоконфигурации Spring Boot, а {@code run()} не вызывается:
 * демонстрация делает нагрузку и закрывает контекст, для проверки условия этого не нужно.
 */
class ThreadSynchronizationDemoConditionTest {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "ru.tsu.tpm.vacancyparser.common",
            "ru.tsu.tpm.vacancyparser.hw04.synchronization"})
    static class ScanConfiguration {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ScanConfiguration.class);

    @Test
    @DisplayName("Демо не создаётся без --app.hw")
    void demoIsAbsentWithoutProperty() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ThreadSynchronizationDemo.class));
    }

    @Test
    @DisplayName("Демо создаётся только при --app.hw=hw04")
    void demoIsPresentOnlyForHw04() {
        contextRunner.withPropertyValues("app.hw=hw04").run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(ThreadSynchronizationDemo.class));
    }

    @Test
    @DisplayName("Демо не создаётся при значениях других домашних заданий")
    void demoIsAbsentForOtherHomeworks() {
        for (String other : List.of("hw01", "hw02", "hw03", "hw05")) {
            contextRunner.withPropertyValues("app.hw=" + other).run(context -> assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(ThreadSynchronizationDemo.class));
        }
    }

    @Test
    @Timeout(120)
    @DisplayName("Сравнение счётчиков: защита точна, незащищённый вариант теряет обновления")
    void counterComparisonShowsBothSides() throws InterruptedException {
        SyncBenchmark.CounterComparison comparison = SyncBenchmark.compareCounters(4, 20_000, 2);

        assertThat(comparison.expected()).isEqualTo(80_000L);
        assertThat(comparison.runs()).hasSize(4);
        assertThat(comparison.runs())
                .filteredOn(run -> run.protection().equals(Protection.ON.label()))
                .as("защищённый счётчик обязан быть точным во всех прогонах")
                .allSatisfy(run -> assertThat(run.value()).isEqualTo(80_000L));
        assertThat(comparison.runs())
                .filteredOn(run -> run.protection().equals(Protection.OFF.label()))
                .as("незащищённый счётчик теряет обновления — это и есть предмет сравнения")
                .anySatisfy(run -> assertThat(run.value()).isLessThan(80_000L));
        assertThat(comparison.nanosFor(Protection.ON.label())).hasSize(2);
        assertThat(comparison.nanosFor(Protection.OFF.label())).hasSize(2);
    }

    @Test
    @Timeout(180)
    @DisplayName("Нагрузочный прогон: инварианты и сумма балансов выдерживают смешанную нагрузку")
    void mixedLoadKeepsInvariants() throws InterruptedException {
        SyncBenchmark.MixedLoadOutcome outcome = SyncBenchmark.runMixedLoad(4, 5_000, 2_000);

        assertThat(outcome.collectorConsistent()).isTrue();
        assertThat(outcome.collectedCount()).isEqualTo(20_000);
        assertThat(outcome.counterActual()).isEqualTo(20_000L);
        assertThat(outcome.accountsPreserved())
                .as("при защищённом переводе сумма балансов обязана сохраниться")
                .isTrue();
        assertThat(outcome.elapsedNanos()).isPositive();
    }

    @Test
    @Timeout(180)
    @DisplayName("Перевод: с защитой сумма сохраняется, без защиты нарушается")
    void transferComparisonShowsBothSides() throws InterruptedException {
        SyncBenchmark.TransferComparison comparison = SyncBenchmark.compareTransfers(6, 20_000, 1);

        assertThat(comparison.runs()).hasSize(2);
        assertThat(comparison.runs())
                .filteredOn(run -> run.protection().equals(Protection.ON.label()))
                .allSatisfy(run -> assertThat(run.preserved())
                        .as("при защите сумма балансов обязана сохраниться")
                        .isTrue());
        assertThat(comparison.runs())
                .filteredOn(run -> run.protection().equals(Protection.OFF.label()))
                .as("без защиты сумма балансов обычно нарушается: это демонстрация дефекта")
                .anySatisfy(run -> assertThat(run.preserved()).isFalse());
    }
}
