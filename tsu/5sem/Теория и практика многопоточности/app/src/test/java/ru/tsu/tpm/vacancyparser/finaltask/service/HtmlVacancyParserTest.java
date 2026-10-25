package ru.tsu.tpm.vacancyparser.finaltask.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.finaltask.model.ParsedVacancy;

/**
 * Разбор HTML вакансии: JSON-LD, разметка {@code data-qa}, запасные варианты и зарплата.
 */
class HtmlVacancyParserTest {

    private final HtmlVacancyParser parser = new HtmlVacancyParser();

    private static final String FULL_PAGE = """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
            <meta charset="utf-8">
            <title>Java-разработчик — вакансия</title>
            <meta property="og:title" content="Java-разработчик">
            <meta property="article:published_time" content="2026-10-01T10:00:00+03:00">
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"JobPosting","title":"Java-разработчик",
             "datePosted":"2026-10-01",
             "hiringOrganization":{"@type":"Organization","name":"ООО «Ромашка»"},
             "jobLocation":{"@type":"Place","address":{"@type":"PostalAddress","addressLocality":"Москва"}},
             "baseSalary":{"@type":"MonetaryAmount","currency":"RUB",
                           "value":{"@type":"QuantitativeValue","value":150000}}}
            </script>
            </head>
            <body>
            <h1 data-qa="vacancy-title">Java-разработчик</h1>
            <a data-qa="vacancy-company-name" href="/employer/1000">ООО «Ромашка»</a>
            <span data-qa="vacancy-salary">от 150 000 ₽</span>
            <div data-qa="vacancy-view-location">Москва</div>
            <div data-qa="vacancy-description"><p>Java 21 &amp; Spring Boot</p><p>Опыт от 3 лет</p></div>
            </body>
            </html>
            """;

    @Test
    @DisplayName("JSON-LD и data-qa: извлекаются все поля задания")
    void parsesFullPage() {
        ParsedVacancy vacancy = parser.parse("http://127.0.0.1:1/vacancies/0", FULL_PAGE);

        assertThat(vacancy.url()).isEqualTo("http://127.0.0.1:1/vacancies/0");
        assertThat(vacancy.title()).isEqualTo("Java-разработчик");
        assertThat(vacancy.company()).isEqualTo("ООО «Ромашка»");
        assertThat(vacancy.salary()).isEqualTo(150_000);
        assertThat(vacancy.salaryText()).isEqualTo("от 150 000 ₽");
        assertThat(vacancy.city()).isEqualTo("Москва");
        assertThat(vacancy.publishedAt()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(vacancy.requirements()).contains("Java 21 & Spring Boot", "Опыт от 3 лет");
    }

    @Test
    @DisplayName("Без JSON-LD: берутся h1 и title, зарплата «по договорённости» — null")
    void fallsBackToPlainHtml() {
        String html = """
                <html><head><title>Python-разработчик</title></head>
                <body><h1>Python-разработчик</h1>
                <span data-qa="vacancy-salary">по договорённости</span></body></html>
                """;

        ParsedVacancy vacancy = parser.parse("http://example.local/1", html);

        assertThat(vacancy.title()).isEqualTo("Python-разработчик");
        assertThat(vacancy.salary()).isNull();
        assertThat(vacancy.company()).isNull();
        assertThat(vacancy.city()).isNull();
        assertThat(vacancy.publishedAt()).isNull();
    }

    @Test
    @DisplayName("Пустой или отсутствующий HTML — понятная ошибка, а не пустая вакансия")
    void rejectsEmptyHtml() {
        assertThatThrownBy(() -> parser.parse("http://example.local/1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("пустой HTML");
        assertThatThrownBy(() -> parser.parse(" ", FULL_PAGE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("адрес");
    }

    @Test
    @DisplayName("Зарплата: первое число диапазона, «по договорённости» — null")
    void parsesSalary() {
        assertThat(HtmlVacancyParser.parseSalary("от 150 000 ₽")).isEqualTo(150_000);
        assertThat(HtmlVacancyParser.parseSalary("200 000 – 300 000 ₽")).isEqualTo(200_000);
        assertThat(HtmlVacancyParser.parseSalary("по договорённости")).isNull();
        assertThat(HtmlVacancyParser.parseSalary(null)).isNull();
    }

    @Test
    @DisplayName("Текст: теги убраны, HTML-сущности раскрыты, пробелы схлопнуты")
    void convertsHtmlToText() {
        assertThat(HtmlVacancyParser.text("<p>Java&nbsp;21 &amp; Spring</p>\n<p>опыт</p>"))
                .isEqualTo("Java 21 & Spring опыт");
        assertThat(HtmlVacancyParser.text("&laquo;Java&raquo; &#8381;")).isEqualTo("«Java» ₽");
        assertThat(HtmlVacancyParser.text("   ")).isNull();
    }
}
