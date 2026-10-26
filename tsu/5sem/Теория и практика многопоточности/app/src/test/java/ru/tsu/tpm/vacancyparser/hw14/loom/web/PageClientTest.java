package ru.tsu.tpm.vacancyparser.hw14.loom.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.PageLoad;
import ru.tsu.tpm.vacancyparser.hw14.loom.server.VacancyPageServer;

/**
 * Загрузчик страниц: извлечение заголовка и поведение при недоступном сайте.
 */
class PageClientTest {

    @Test
    @DisplayName("Заголовок извлекается из тега с атрибутами, регистром и сущностями")
    void titleExtractionHandlesMarkupVariants() {
        assertAll(
                () -> assertThat(PageVacancyClient.HtmlTitle.extract("<title>Java</title>")).isEqualTo("Java"),
                () -> assertThat(PageVacancyClient.HtmlTitle.extract(
                                "<html><head><TITLE data-x=\"1\">\n  Backend\n  Java \n</TITLE></head></html>"))
                        .isEqualTo("Backend Java"),
                () -> assertThat(PageVacancyClient.HtmlTitle.extract("<title>A &amp; B &lt;C&gt;</title>"))
                        .isEqualTo("A & B <C>"),
                () -> assertThat(PageVacancyClient.HtmlTitle.extract("<title><b>Жирный</b> текст</title>"))
                        .isEqualTo("Жирный текст"),
                () -> assertThat(PageVacancyClient.HtmlTitle.extract("<html>без заголовка</html>")).isEmpty(),
                () -> assertThat(PageVacancyClient.HtmlTitle.extract(null)).isEmpty());
    }

    @Test
    @DisplayName("Недоступный сайт — это результат со сбоем, а не исключение")
    void transportFailureBecomesResult() throws IOException {
        VacancyPageServer site = VacancyPageServer.start(0L);
        String url = site.urls(1).get(0);
        site.close();

        try (PageVacancyClient client = new PageVacancyClient(500L, 1_000L)) {
            PageLoad page = client.fetch(url, 0);
            assertThat(page.ok()).isFalse();
            assertThat(page.status()).isZero();
            assertThat(page.pageNumber()).isZero();
            assertThat(page.title()).isEmpty();
            assertThat(page.error()).isNotBlank();
            assertThat(page.worker().virtual()).isFalse();
        }

        assertThatThrownBy(() -> new PageVacancyClient(0L, 1_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageVacancyClient(1_000L, -5L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
