package ru.tsu.tpm.vacancyparser.hw10.completable.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.CompanyInfo;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.VacancyDetails;
import ru.tsu.tpm.vacancyparser.hw10.completable.source.HttpVacancyClient;

/**
 * Локальная заглушка API и HTTP-клиент: успешные ответы, ошибки источников, освобождение порта.
 */
class VacancyStubServerTest {

    @Test
    @Timeout(60)
    @DisplayName("Заглушка отдаёт данные вакансии и компании, клиент их разбирает")
    void clientReadsBothSources() throws Exception {
        try (VacancyStubServer stub = VacancyStubServer.start(0L);
                HttpVacancyClient client = new HttpVacancyClient(stub.baseUrl(), 1_000L, 2_000L)) {
            VacancyDetails details = client.fetchDetails("v-03");
            assertThat(details.id()).isEqualTo("v-03");
            assertThat(details.title()).isEqualTo(VacancyStubServer.expectedTitle("v-03"));
            assertThat(details.city()).isEqualTo(VacancyStubServer.expectedCity("v-03"));
            assertThat(details.salaryTo()).isGreaterThan(details.salaryFrom());

            CompanyInfo company = client.fetchCompany("v-03");
            assertThat(company.id()).isEqualTo("v-03");
            assertThat(company.name()).isNotBlank().isNotEqualTo(CompanyInfo.UNKNOWN_NAME);
            assertThat(company.rating()).isBetween(0.0, 5.0);
            assertThat(company.isFallback()).isFalse();
            assertThat(client.requestCount()).isEqualTo(2);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Сломанный идентификатор даёт исключение с кодом ответа, остальные работают")
    void failingIdIsIsolatedAtClientLevel() throws Exception {
        try (VacancyStubServer stub = VacancyStubServer.start(0L, Set.of("v-02"), Set.of("v-03"));
                HttpVacancyClient client = new HttpVacancyClient(stub.baseUrl(), 1_000L, 2_000L)) {
            assertThatThrownBy(() -> client.fetchDetails("v-02"))
                    .isInstanceOfSatisfying(HttpVacancyClient.SourceUnavailableException.class,
                            error -> assertThat(error.status()).isEqualTo(500));

            assertThatThrownBy(() -> client.fetchCompany("v-03"))
                    .isInstanceOfSatisfying(HttpVacancyClient.SourceUnavailableException.class,
                            error -> assertThat(error.status()).isEqualTo(503));

            assertThat(client.fetchDetails("v-01").id()).isEqualTo("v-01");
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Задержка ответа действительно применяется")
    void latencyIsApplied() throws Exception {
        try (VacancyStubServer stub = VacancyStubServer.start(80L);
                HttpVacancyClient client = new HttpVacancyClient(stub.baseUrl(), 1_000L, 2_000L)) {
            long start = System.nanoTime();
            client.fetchDetails("v-05");
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

            assertThat(elapsedMillis).isGreaterThanOrEqualTo(70L);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Заглушка освобождает порт после остановки")
    void stopReleasesPort() throws Exception {
        VacancyStubServer stub = VacancyStubServer.start(0L);
        int port = stub.port();
        stub.close();

        assertThatThrownBy(() -> {
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
                socket.getOutputStream().write(1);
            }
        }).isInstanceOf(IOException.class);
    }

    @Test
    @Timeout(60)
    @DisplayName("Список идентификаторов по умолчанию и проверка «сломанных» идентификаторов")
    void defaultIdsAndFailIdCheck() {
        List<String> ids = VacancyStubServer.defaultIds(12);
        assertThat(ids).hasSize(12).startsWith("v-01").endsWith("v-12");

        assertThat(VacancyStubServer.missingFailIds(ids, "v-04", "v-99")).containsExactly("v-99");
        assertThat(VacancyStubServer.missingFailIds(ids, null, " ")).isEmpty();
    }

    @Test
    @Timeout(60)
    @DisplayName("Отрицательная задержка отвергается")
    void negativeDelayRejected() {
        assertThatThrownBy(() -> VacancyStubServer.start(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
