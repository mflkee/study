package ru.tsu.tpm.vacancyparser.hw13.queue.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.PageResult;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.UrlTask;
import ru.tsu.tpm.vacancyparser.hw13.queue.web.PageFetcher;
import ru.tsu.tpm.vacancyparser.hw13.queue.web.TitleExtractor;

/**
 * Локальный сайт вакансий и загрузчик страниц: коды, заголовки, задержка, освобождение порта.
 */
class VacancySiteServerTest {

    @Test
    @Timeout(60)
    @DisplayName("Страницы отдают заявленные коды, заголовки совпадают с ожидаемыми")
    void pagesReturnDeclaredContent() throws IOException {
        try (VacancySiteServer site = VacancySiteServer.start(0L);
                PageFetcher fetcher = new PageFetcher(1_000L, 2_000L)) {
            List<String> urls = site.urls(6);
            assertThat(urls).hasSize(6).allSatisfy(url -> assertThat(url).startsWith(site.baseUrl()));

            for (int page = 0; page < 6; page++) {
                PageResult result = fetcher.fetch(new UrlTask(urls.get(page), page));
                assertThat(result.status()).isEqualTo(VacancySiteServer.expectedStatus(page));
                if (result.ok()) {
                    assertThat(result.title()).isEqualTo(VacancySiteServer.expectedTitle(page));
                } else {
                    assertThat(result.error()).contains("404");
                }
            }
            assertThat(fetcher.requestCount()).isEqualTo(6);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("HTML страницы содержит заголовок, который извлекается тем же правилом")
    void generatedHtmlIsParsable() {
        String html = VacancySiteServer.pageHtml(3);

        assertThat(TitleExtractor.extract(html)).isEqualTo(VacancySiteServer.expectedTitle(3));
        assertThat(VacancySiteServer.expectedStatus(4)).isEqualTo(404);
        assertThat(VacancySiteServer.expectedStatus(5)).isEqualTo(200);
    }

    @Test
    @Timeout(60)
    @DisplayName("Задержка отдачи страницы применяется")
    void pageDelayIsApplied() throws IOException {
        try (VacancySiteServer site = VacancySiteServer.start(80L);
                PageFetcher fetcher = new PageFetcher(1_000L, 2_000L)) {
            long start = System.nanoTime();
            PageResult result = fetcher.fetch(new UrlTask(site.urls(1).get(0), 0));
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

            assertThat(result.ok()).isTrue();
            assertThat(elapsedMillis).isGreaterThanOrEqualTo(70L);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Загрузчик различает «ответ с ошибкой» и «сети нет»")
    void fetcherDistinguishesFailures() throws IOException {
        try (VacancySiteServer site = VacancySiteServer.start(0L);
                PageFetcher fetcher = new PageFetcher(300L, 300L)) {
            // Порт закрытого сайта: соединение отклоняется, код 0 и текст ошибки.
            int closedPort = site.port();
            site.close();

            PageResult result = fetcher.fetch(new UrlTask("http://127.0.0.1:" + closedPort + "/vacancy/0", 0));

            assertThat(result.ok()).isFalse();
            assertThat(result.status()).isZero();
            assertThat(result.error()).isNotBlank();
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Остановка сайта освобождает порт, живых потоков не остаётся")
    void stopReleasesPort() throws IOException {
        VacancySiteServer site = VacancySiteServer.start(0L);
        int port = site.port();
        assertThat(site.requestCount()).isZero();
        site.close();

        assertThatThrownBy(() -> {
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
                socket.getOutputStream().write(1);
            }
        }).isInstanceOf(IOException.class);
        assertThat(VacancySiteServer.liveThreads()).isZero();
    }

    @Test
    @DisplayName("Некорректные параметры отвергаются")
    void invalidParameters() {
        assertThatThrownBy(() -> VacancySiteServer.start(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageFetcher(0L, 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageFetcher(1_000L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
