package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки доменного типа: равенство определяется ключом, полезная нагрузка в сравнении не участвует.
 */
class ItemTest {

    @Test
    @DisplayName("Элементы с одинаковым ключом равны, с разными — нет")
    void equalityIsByKey() {
        Item first = new Item("vacancy-1", "текст первой версии");
        Item second = new Item("vacancy-1", "текст второй версии");
        Item other = new Item("vacancy-2", "тот же текст");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(other);
        assertThat(first).isNotEqualTo(null);
        assertThat(first).isNotEqualTo("vacancy-1");
    }

    @Test
    @DisplayName("Разная нагрузка при одинаковом ключе не создаёт второй элемент в множестве")
    void payloadDoesNotAffectSetMembership() {
        Set<Item> items = new HashSet<>();
        items.add(new Item("v-1", "первое чтение страницы"));
        items.add(new Item("v-1", "повторное чтение страницы"));
        items.add(new Item("v-2", "другая вакансия"));

        assertThat(items)
                .as("ключ определяет элемент: повторное чтение той же вакансии — не новый элемент")
                .hasSize(2);
    }

    @Test
    @DisplayName("Ключ обязателен")
    void keyIsRequired() {
        assertThatNullPointerException().isThrownBy(() -> new Item(null, "текст"));
        assertThat(new Item("v-1", null).payload()).isNull();
        assertThat(new Item("v-1", null)).isEqualTo(new Item("v-1", "текст"));
    }

    @Test
    @DisplayName("В выводе элемент показывается ключом")
    void toStringShowsKey() {
        assertThat(new Item("vacancy-42", "текст")).hasToString("vacancy-42");
    }
}
