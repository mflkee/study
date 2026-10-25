package ru.tsu.tpm.vacancyparser.hw10.completable.config;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.tsu.tpm.vacancyparser.hw10.completable.server.VacancyStubServer;
import ru.tsu.tpm.vacancyparser.hw10.completable.service.AsyncVacancyService;
import ru.tsu.tpm.vacancyparser.hw10.completable.source.HttpVacancyClient;

/**
 * Сборка ДЗ 10: заглушка внешнего API, HTTP-клиент и сервис асинхронной выборки.
 *
 * <p><b>Жизненный цикл.</b> Все три бина объявлены с {@code destroyMethod = "close"} — Spring вызывает
 * {@code close()} при остановке приложения. Для {@link AsyncVacancyService} это означает
 * {@code shutdown()} собственного пула с ожиданием: требование «корректное завершение ExecutorService
 * при остановке приложения» выполняется средствами контейнера, а не забытым вызовом в конце демо.
 *
 * <p>Бины создаются только при {@code --app.hw=hw10}, поэтому остальные домашние задания не поднимают
 * ни HTTP-заглушку, ни пул потоков.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.hw", havingValue = "hw10")
public class Hw10Configuration {

    /** Локальный API-источник: два маршрута, задержка ответа и «сломанные» идентификаторы. */
    @Bean(destroyMethod = "close")
    public VacancyStubServer vacancyStubServer(Hw10Properties properties) throws IOException {
        return VacancyStubServer.start(
                properties.getLatencyMillis(),
                failIds(properties.getDetailsFailId()),
                failIds(properties.getCompanyFailId()));
    }

    /** Синхронный HTTP-клиент; асинхронность обеспечивает конвейер CompletableFuture. */
    @Bean(destroyMethod = "close")
    public HttpVacancyClient httpVacancyClient(VacancyStubServer stub, Hw10Properties properties) {
        return new HttpVacancyClient(
                stub.baseUrl(), properties.getConnectTimeoutMillis(), properties.getRequestTimeoutMillis());
    }

    /** Сервис выборки: собственный пул именованных демон-потоков. */
    @Bean(destroyMethod = "close")
    public AsyncVacancyService asyncVacancyService(HttpVacancyClient client, Hw10Properties properties) {
        return new AsyncVacancyService(
                client,
                client,
                properties.getPoolSize(),
                properties.getTransformDelayMillis(),
                properties.getShutdownTimeoutMillis(),
                null);
    }

    private static Set<String> failIds(String id) {
        if (id == null || id.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Set.of(id.trim()));
    }
}
