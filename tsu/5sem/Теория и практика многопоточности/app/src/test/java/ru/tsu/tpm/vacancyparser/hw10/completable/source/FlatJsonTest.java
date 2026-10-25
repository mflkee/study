package ru.tsu.tpm.vacancyparser.hw10.completable.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Разбор плоского JSON-ответа заглушки: значения, экранирование, ошибки.
 */
class FlatJsonTest {

    @Test
    @DisplayName("Строки, числа, логические значения и null разбираются")
    void parsesFlatObject() {
        Map<String, String> values = FlatJson.parseObject(
                "{\"id\":\"v-01\",\"title\":\"Java \\\"разработчик\\\"\",\"rating\":4.7,"
                        + "\"count\":12,\"active\":true,\"note\":null}");

        assertThat(FlatJson.string(values, "id")).isEqualTo("v-01");
        assertThat(FlatJson.string(values, "title")).isEqualTo("Java \"разработчик\"");
        assertThat(FlatJson.doubleValue(values, "rating")).isEqualTo(4.7);
        assertThat(FlatJson.intValue(values, "count")).isEqualTo(12);
        assertThat(FlatJson.booleanValue(values, "active")).isTrue();
        assertThat(values).containsKey("note");
        assertThat(values.get("note")).isNull();
    }

    @Test
    @DisplayName("Пустой объект допустим, отсутствующее поле — ошибка с именем поля")
    void emptyObjectAndMissingField() {
        assertThat(FlatJson.parseObject("{}")).isEmpty();
        assertThat(FlatJson.parseObject("  {  }  ")).isEmpty();

        assertThatThrownBy(() -> FlatJson.string(Map.of(), "id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    @DisplayName("Некорректный JSON отвергается, а не разбирается частично")
    void rejectsBrokenJson() {
        assertThatThrownBy(() -> FlatJson.parseObject(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlatJson.parseObject("{\"id\":}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("значение");
        assertThatThrownBy(() -> FlatJson.parseObject("{\"id\":\"v-01\""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlatJson.parseObject("{\"id\":[1,2]}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Числовое поле с нечисловым значением даёт понятную ошибку")
    void numberFieldTypeMismatch() {
        Map<String, String> values = FlatJson.parseObject("{\"salary\":\"много\"}");
        assertThatThrownBy(() -> FlatJson.intValue(values, "salary"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salary");
    }
}
