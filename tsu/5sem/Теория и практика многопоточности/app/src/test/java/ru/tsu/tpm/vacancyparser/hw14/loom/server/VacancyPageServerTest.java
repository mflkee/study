package ru.tsu.tpm.vacancyparser.hw14.loom.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.PageLoad;
import ru.tsu.tpm.vacancyparser.hw14.loom.web.PageVacancyClient;

/**
 * Локальный сайт вакансий: адреса, заголовки, задержка и освобождение порта.
 */
class VacancyPageServerTest {

    @Test
    @DisplayName("Страницы отдают детерминированные заголовки, и клиент их извлекает")
    void pagesExposeDeterministicTitles() throws IOException {
        try (VacancyPageServer site = VacancyPageServer.start(0L);
                PageVacancyClient client = new PageVacancyClient(1_000L, 2_000L)) {
            List<String> urls = site.urls(5);
            assertThat(urls).hasSize(5);
            assertThat(urls.get(0)).isEqualTo(site.baseUrl() + "/vacancy/0");
            assertThat(site.expectedTitle(0)).isEqualTo("Вакансия №0 — Java-разработчик");

            for (int index = 0; index < urls.size(); index++) {
                PageLoad page = client.fetch(urls.get(index), index);
                assertThat(page.ok()).as("страница %d", index).isTrue();
                assertThat(page.status()).isEqualTo(200);
                assertThat(page.title()).isEqualTo(VacancyPageServer.expectedTitle(index));
                assertThat(page.worker().virtual()).isFalse();
                assertThat(page.worker().name()).isNotBlank();
            }
            assertThat(site.requestCount()).isEqualTo(5);
            assertThat(client.requestCount()).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("Задержка страницы действительно применяется")
    void pageDelayIsApplied() throws IOException {
        try (VacancyPageServer site = VacancyPageServer.start(120L);
                PageVacancyClient client = new PageVacancyClient(1_000L, 5_000L)) {
            long start = System.nanoTime();
            PageLoad page = client.fetch(site.urls(1).get(0), 0);
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

            assertThat(page.ok()).isTrue();
            assertThat(elapsedMillis).isGreaterThanOrEqualTo(100L);
            assertThat(page.durationMillis()).isGreaterThanOrEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("Некорректные параметры отвергаются, после закрытия порт больше не отвечает")
    void invalidParametersAndClose() throws IOException {
        assertThatThrownBy(() -> VacancyPageServer.start(-1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VacancyPageServer.expectedTitle(-1)).isInstanceOf(IllegalArgumentException.class);

        VacancyPageServer site = VacancyPageServer.start(0L);
        String url;
        try (PageVacancyClient client = new PageVacancyClient(1_000L, 1_000L)) {
            url = site.urls(1).get(0);
            assertThat(client.fetch(url, 0).ok()).isTrue();
        }
        assertThatThrownBy(() -> site.urls(-1)).isInstanceOf(IllegalArgumentException.class);
        site.close();

        try (PageVacancyClient client = new PageVacancyClient(500L, 1_000L)) {
            PageLoad page = client.fetch(url, 0);
            assertThat(page.ok()).isFalse();
            assertThat(page.status()).isZero();
            assertThat(page.error()).isNotBlank();
        }
    }
}
