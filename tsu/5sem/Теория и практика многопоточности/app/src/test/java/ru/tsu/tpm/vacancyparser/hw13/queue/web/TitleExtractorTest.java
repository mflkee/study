package ru.tsu.tpm.vacancyparser.hw13.queue.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Извлечение заголовка: регистр, атрибуты, переводы строк, сущности, отсутствие тега.
 */
class TitleExtractorTest {

    @Test
    @DisplayName("Простой заголовок извлекается")
    void simpleTitle() {
        assertThat(TitleExtractor.extract("<html><head><title>Вакансия №1</title></head></html>"))
                .isEqualTo("Вакансия №1");
    }

    @Test
    @DisplayName("Регистр тега, атрибуты и переводы строк не мешают")
    void tolerantToMarkup() {
        assertThat(TitleExtractor.extract("<HTML><HEAD><TITLE lang=\"ru\">\n  Java-разработчик \n</TITLE>"))
                .isEqualTo("Java-разработчик");
        assertThat(TitleExtractor.extract("<title data-x='1'>A   B</title>")).isEqualTo("A B");
    }

    @Test
    @DisplayName("Сущности раскрываются, пробелы схлопываются")
    void decodesEntities() {
        assertThat(TitleExtractor.extract("<title>Java &amp; Kotlin &quot;pro&quot;</title>"))
                .isEqualTo("Java & Kotlin \"pro\"");
        assertThat(TitleExtractor.extract("<title>&lt;b&gt;ставка&lt;/b&gt;&nbsp;100</title>"))
                .isEqualTo("<b>ставка</b> 100");
    }

    @Test
    @DisplayName("Без тега заголовка результат пустой, а не исключение")
    void missingTitle() {
        assertThat(TitleExtractor.extract("<html><body>нет заголовка</body></html>")).isEmpty();
        assertThat(TitleExtractor.extract("")).isEmpty();
        assertThat(TitleExtractor.extract(null)).isEmpty();
        assertThat(TitleExtractor.hasTitle("<html><body>без тега</body></html>")).isFalse();
        assertThat(TitleExtractor.hasTitle("<title>Есть</title>")).isTrue();
    }

    @Test
    @DisplayName("Нормализация пустого значения безопасна")
    void normalizeEmpty() {
        assertThat(TitleExtractor.normalize(null)).isEmpty();
        assertThat(TitleExtractor.normalize("   ")).isEmpty();
        assertThat(TitleExtractor.normalize("  a\t\nb  ")).isEqualTo("a b");
    }
}
