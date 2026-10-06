package ru.tsu.tpm.vacancyparser.hw08.pool.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.HttpRequester;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.OutcomeCategory;
import ru.tsu.tpm.vacancyparser.hw08.pool.web.RequestResult;

/**
 * Локальный сервер-заглушка: коды, задержки, освобождение порта и список адресов.
 */
class LocalStubServerTest {

    @Test
    @Timeout(60)
    @DisplayName("Поднимается на свободном порту, отдаёт 20 адресов и заявленные коды")
    void routesReturnDeclaredCodes() throws IOException {
        try (LocalStubServer stub = LocalStubServer.start()) {
            assertThat(stub.port()).isPositive();
            assertThat(stub.urls()).hasSize(LocalStubServer.LOCAL_URL_COUNT);
            assertThat(stub.urls()).allSatisfy(url -> assertThat(url).startsWith(stub.baseUrl()));

            HttpRequester requester = new HttpRequester(1_000L, 3_000L);
            List<String> urls = stub.urls();
            for (int index = 0; index < urls.size(); index++) {
                if (LocalStubServer.isSlow(index)) {
                    continue;
                }
                RequestResult result = requester.fetch(urls.get(index));
                assertThat(result.category())
                        .as("маршрут %d должен ответить", index)
                        .isEqualTo(OutcomeCategory.RESPONSE);
                assertThat(result.httpStatus())
                        .as("код маршрута %d", index)
                        .isEqualTo(LocalStubServer.expectedStatus(index));
            }
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Медленный маршрут даёт таймаут, а не ответ")
    void slowRouteTimesOut() throws IOException {
        try (LocalStubServer stub = LocalStubServer.start(1_000L)) {
            HttpRequester impatient = new HttpRequester(500L, 200L);
            RequestResult result = impatient.fetch(stub.baseUrl() + "/slow");

            assertThat(result.category()).isEqualTo(OutcomeCategory.TIMEOUT);
            assertThat(result.hasStatus()).isFalse();
            assertThat(result.responseMillis())
                    .as("время при таймауте равно границе таймаута")
                    .isEqualTo(200.0);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Остановка освобождает порт, повторный запуск не падает с «адрес занят»")
    void stopFreesPort() throws IOException {
        LocalStubServer first = LocalStubServer.start();
        int port = first.port();
        first.stop();

        assertThatThrownBy(() -> {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.getOutputStream().write(1);
            }
        }).isInstanceOf(IOException.class);

        try (LocalStubServer second = LocalStubServer.start()) {
            assertThat(second.port()).isPositive();
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Счётчик обработанных запросов растёт")
    void requestCounterGrows() throws IOException {
        try (LocalStubServer stub = LocalStubServer.start()) {
            HttpRequester requester = new HttpRequester(1_000L, 2_000L);
            int before = stub.requestCounter().get();
            requester.fetch(stub.baseUrl() + "/ok");
            requester.fetch(stub.baseUrl() + "/not-found");

            assertThat(stub.requestCounter().get()).isGreaterThanOrEqualTo(before + 2);
        }
    }
}
