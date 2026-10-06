package ru.tsu.tpm.vacancyparser.hw08.pool.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw08.pool.server.LocalStubServer;

/**
 * HTTP-клиент и классификация исходов: ответ любого кода, таймаут, сетевая ошибка, отказ.
 */
class HttpRequesterTest {

    @Test
    @Timeout(60)
    @DisplayName("Ответы 200, 404, 500, 503 — это «получен ответ» с соответствующим кодом")
    void anyStatusCodeIsAResponse() throws IOException {
        try (LocalStubServer stub = LocalStubServer.start()) {
            HttpRequester requester = new HttpRequester(1_000L, 2_000L);

            assertThat(requester.fetch(stub.baseUrl() + "/ok").httpStatus()).isEqualTo(200);
            assertThat(requester.fetch(stub.baseUrl() + "/not-found").httpStatus()).isEqualTo(404);
            assertThat(requester.fetch(stub.baseUrl() + "/error").httpStatus()).isEqualTo(500);
            assertThat(requester.fetch(stub.baseUrl() + "/unavailable").httpStatus()).isEqualTo(503);

            for (String path : new String[] {"/ok", "/not-found", "/error", "/unavailable"}) {
                RequestResult result = requester.fetch(stub.baseUrl() + path);
                assertThat(result.category()).isEqualTo(OutcomeCategory.RESPONSE);
                assertThat(result.responseNanos()).isNotNegative();
            }
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Неуспешный код — не сетевая ошибка")
    void unsuccessfulCodeIsNotNetworkError() throws IOException {
        try (LocalStubServer stub = LocalStubServer.start()) {
            HttpRequester requester = new HttpRequester(1_000L, 2_000L);
            RequestResult result = requester.fetch(stub.baseUrl() + "/error");

            assertThat(result.category()).isEqualTo(OutcomeCategory.RESPONSE);
            assertThat(result.httpStatus()).isEqualTo(500);
            assertThat(result.category()).isNotEqualTo(OutcomeCategory.NETWORK_ERROR);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Отсутствие ответа дольше таймаута — категория «таймаут» на границе таймаута")
    void slowServerProducesTimeout() throws IOException {
        try (LocalStubServer stub = LocalStubServer.start(2_000L)) {
            HttpRequester requester = new HttpRequester(500L, 200L);
            RequestResult result = requester.fetch(stub.baseUrl() + "/slow");

            assertThat(result.category()).isEqualTo(OutcomeCategory.TIMEOUT);
            assertThat(result.hasStatus()).as("при таймауте код не выдумывается").isFalse();
            assertThat(result.responseMillis()).isEqualTo(200.0);
            assertThat(result.reason()).contains("таймаут");
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Недоступный порт — сетевая ошибка с причиной")
    void closedPortIsNetworkError() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        HttpRequester requester = new HttpRequester(500L, 1_000L);
        RequestResult result = requester.fetch("http://127.0.0.1:" + closedPort + "/");

        assertThat(result.category()).isEqualTo(OutcomeCategory.NETWORK_ERROR);
        assertThat(result.hasStatus()).isFalse();
        assertThat(result.reason()).isNotBlank();
    }

    @Test
    @Timeout(60)
    @DisplayName("Неразрешимое имя — сетевая ошибка")
    void unknownHostIsNetworkError() {
        HttpRequester requester = new HttpRequester(500L, 1_000L);
        RequestResult result = requester.fetch("http://this-host-does-not-exist.invalid/");

        assertThat(result.category()).isEqualTo(OutcomeCategory.NETWORK_ERROR);
        assertThat(result.hasStatus()).isFalse();
    }

    @Test
    @DisplayName("Непригодный адрес — отказ в выполнении, без обращения к сети")
    void invalidUrlIsRejected() {
        HttpRequester requester = new HttpRequester(500L, 1_000L);
        RequestResult result = requester.fetch("это-не-url");

        assertThat(result.category()).isEqualTo(OutcomeCategory.REJECTED);
        assertThat(result.httpStatus()).isNull();
    }
}
