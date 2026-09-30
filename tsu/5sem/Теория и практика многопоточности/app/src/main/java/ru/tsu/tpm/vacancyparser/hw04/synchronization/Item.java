package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import java.util.Objects;

/**
 * Элемент, который потоки складывают в разделяемое состояние сбора.
 *
 * <p>Домен — парсер вакансий: ключ элемента — это идентификатор вакансии (ссылка), payload —
 * собранный текст. Ключ важен тем, что именно по нему принимается решение «обрабатывали уже или
 * нет»: одна и та же вакансия может встретиться нескольким потокам одновременно, и обработать её
 * обязан ровно один.
 *
 * <p><b>Равенство определяется только ключом.</b> Это не деталь, а требование к модели: два
 * элемента с одинаковым ключом — это одна и та же вакансия, даже если текст разный (например,
 * страницу перечитали в другой момент). Если бы равенство учитывало payload, дубликаты не
 * схлопывались бы в {@code Set}, и проверка «уже обработан» перестала бы работать.
 *
 * @param key     идентификатор элемента; по нему сравниваются элементы
 * @param payload полезная нагрузка; в сравнении не участвует
 */
public record Item(String key, String payload) {

    public Item {
        Objects.requireNonNull(key, "ключ элемента обязателен");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Item item && key.equals(item.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key;
    }
}
