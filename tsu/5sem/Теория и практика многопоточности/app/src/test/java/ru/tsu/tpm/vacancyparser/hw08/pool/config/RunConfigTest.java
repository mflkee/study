package ru.tsu.tpm.vacancyparser.hw08.pool.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Конфигурация прогона: значения по умолчанию и проверка целостности.
 */
class RunConfigTest {

    @Test
    @DisplayName("По умолчанию: около 20 адресов, ограниченная очередь и ненулевые таймауты")
    void defaultsAreSensible() {
        RunConfig config = RunConfig.from(new Hw08Properties());

        assertThat(config.mode()).isEqualTo(WorkMode.LOCAL);
        assertThat(config.urls()).hasSize(20);
        assertThat(config.queueCapacity()).isPositive();
        assertThat(config.connectTimeoutMillis()).isPositive();
        assertThat(config.requestTimeoutMillis()).isPositive();
        assertThat(config.shutdownTimeoutMillis()).isPositive();
        assertThat(config.coreSize()).isPositive();
        assertThat(config.maxSize()).isGreaterThanOrEqualTo(config.coreSize());
    }

    @Test
    @DisplayName("Настройки печатаются с фактическими значениями")
    void settingsLinesContainFacts() {
        RunConfig config = RunConfig.from(new Hw08Properties());

        String text = String.join("\n", config.settingsLines());
        assertThat(text)
                .contains("режим работы: local")
                .contains("адресов в списке: 20")
                .contains("размер пула (минимум/максимум): " + config.coreSize() + "/" + config.maxSize())
                .contains("ёмкость очереди: " + config.queueCapacity())
                .contains("таймаут запроса: " + config.requestTimeoutMillis() + " мс");
    }

    @Test
    @DisplayName("Корректные значения проходят проверку, withUrls меняет только список")
    void validConfigPasses() {
        RunConfig config = new RunConfig(WorkMode.LOCAL, List.of("http://a"), 2, 4, 5, 1000L, 500L, 1000L, 5000L);

        assertThat(config.withUrls(List.of("http://a", "http://b")).urls()).containsExactly("http://a", "http://b");
        assertThat(config.withUrls(List.of("http://a", "http://b")).coreSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("Некорректные значения отвергаются с понятным сообщением")
    void invalidValuesAreRejected() {
        assertThatThrownBy(() -> new RunConfig(WorkMode.LOCAL, List.of(), 2, 4, 5, 1L, 1L, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("список адресов пуст");

        assertThatThrownBy(() -> new RunConfig(WorkMode.LOCAL, List.of("http://a"), 5, 3, 5, 1L, 1L, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть меньше минимального");

        assertThatThrownBy(() -> new RunConfig(WorkMode.LOCAL, List.of("http://a"), 2, 4, 0, 1L, 1L, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ёмкость очереди");

        assertThatThrownBy(() -> new RunConfig(WorkMode.LOCAL, List.of("http://a"), 2, 4, 5, 1L, 0L, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("таймаут соединения");

        assertThatThrownBy(() -> new RunConfig(WorkMode.LOCAL, List.of("http://a"), 2, 4, 5, 1L, 1L, -5L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("таймаут запроса");
    }

    @Test
    @DisplayName("Режим разбирается по имени, неизвестный отвергается")
    void modeIsParsed() {
        assertThat(WorkMode.from("LOCAL")).isEqualTo(WorkMode.LOCAL);
        assertThat(WorkMode.from(" mixed ")).isEqualTo(WorkMode.MIXED);
        assertThatThrownBy(() -> WorkMode.from("fast"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("неизвестный режим");
    }
}
