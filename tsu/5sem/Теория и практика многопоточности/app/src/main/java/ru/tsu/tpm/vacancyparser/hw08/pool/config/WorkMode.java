package ru.tsu.tpm.vacancyparser.hw08.pool.config;

import java.util.Locale;

/**
 * Режим работы прогона: откуда берутся адреса.
 *
 * <p>По умолчанию используется {@link #LOCAL}: список адресов формирует локальный сервер-заглушка,
 * поэтому демонстрация и тесты не зависят от внешней сети. Внешний режим включается явно, а
 * {@link #MIXED} объединяет оба источника в одном прогоне.
 */
public enum WorkMode {

    /** Только локальный сервер-заглушка. */
    LOCAL("local"),

    /** Только внешние адреса из конфигурации. */
    EXTERNAL("external"),

    /** Внешние адреса и локальная заглушка вместе. */
    MIXED("mixed");

    private final String label;

    WorkMode(String label) {
        this.label = label;
    }

    /** Как режим называется в конфигурации и выводе. */
    public String label() {
        return label;
    }

    /** Разобрать режим по имени без учёта регистра; неизвестное значение отвергается с пояснением. */
    public static WorkMode from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("режим работы не задан: ожидается local, external или mixed");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "неизвестный режим работы «" + value + "»: ожидается local, external или mixed");
        }
    }

    /** Нужен ли в этом режиме локальный сервер-заглушка. */
    public boolean usesLocalStub() {
        return this == LOCAL || this == MIXED;
    }

    /** Нужны ли в этом режиме внешние адреса из конфигурации. */
    public boolean usesExternalUrls() {
        return this == EXTERNAL || this == MIXED;
    }
}
