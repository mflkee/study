package ru.tsu.tpm.vacancyparser.hw10.completable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw10.completable.server.VacancyStubServer;
import ru.tsu.tpm.vacancyparser.hw10.completable.service.AsyncVacancyService;
import ru.tsu.tpm.vacancyparser.hw10.completable.source.HttpVacancyClient;

/**
 * Условный запуск ДЗ 10 и корректная остановка исполнителя при закрытии контекста.
 *
 * <p>Главная проверка здесь — не «бин есть», а «после закрытия контекста пул остановлен и потоков не
 * осталось»: именно этого требует пункт задания о корректном завершении {@code ExecutorService}.
 */
class CompletableFutureDemoConditionTest {

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {
            "ru.tsu.tpm.vacancyparser.common",
            "ru.tsu.tpm.vacancyparser.hw10.completable"})
    static class ScanConfiguration {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ScanConfiguration.class)
            .withPropertyValues(
                    "app.hw10.latency-millis=0",
                    "app.hw10.transform-delay-millis=0",
                    "app.hw10.ids=v-01,v-02");

    @Test
    @DisplayName("Без --app.hw не создаются ни демо, ни пул, ни заглушка")
    void nothingIsCreatedWithoutProperty() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(CompletableFutureDemo.class)
                .doesNotHaveBean(AsyncVacancyService.class)
                .doesNotHaveBean(VacancyStubServer.class));
    }

    @Test
    @Timeout(60)
    @DisplayName("При --app.hw=hw10 создаются демо, клиент, заглушка и сервис")
    void beansAreCreatedForHw10() {
        contextRunner.withPropertyValues("app.hw=hw10").run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(CompletableFutureDemo.class)
                .hasSingleBean(AsyncVacancyService.class)
                .hasSingleBean(HttpVacancyClient.class)
                .hasSingleBean(VacancyStubServer.class));
    }

    @Test
    @Timeout(60)
    @DisplayName("После закрытия контекста пул остановлен и живых потоков ДЗ 10 нет")
    void contextCloseStopsExecutor() {
        AtomicReference<AsyncVacancyService> service = new AtomicReference<>();
        AtomicReference<VacancyStubServer> stub = new AtomicReference<>();

        contextRunner.withPropertyValues("app.hw=hw10").run(context -> {
            service.set(context.getBean(AsyncVacancyService.class));
            stub.set(context.getBean(VacancyStubServer.class));
            assertThat(service.get().isShutdown()).isFalse();
            assertThat(stub.get().port()).isPositive();
        });

        assertThat(service.get().isShutdown()).isTrue();
        assertThat(service.get().isTerminated()).isTrue();
        assertThat(ThreadDump.liveThreadsWithPrefixes(AsyncVacancyService.THREAD_PREFIX)).isEmpty();
    }

    @Test
    @DisplayName("Демо не создаётся при значениях других домашних заданий")
    void demoIsAbsentForOtherHomeworks() {
        for (String other : java.util.List.of("hw01", "hw08", "hw09", "hw11", "hw13")) {
            contextRunner.withPropertyValues("app.hw=" + other).run(context -> assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(CompletableFutureDemo.class)
                    .doesNotHaveBean(AsyncVacancyService.class));
        }
    }
}
