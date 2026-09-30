package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки начального состояния сборщика и различения нового элемента от уже обработанного.
 */
class DataCollectorStateTest {

    @Test
    @DisplayName("У нового сборщика состояние пусто и наблюдаемо извне")
    void newCollectorIsEmptyAndObservable() {
        DataCollector collector = new DataCollector();

        assertThat(collector.collected()).isEmpty();
        assertThat(collector.collectedCount()).isZero();
        assertThat(collector.processedCount()).isZero();
        assertThat(collector.processedKeyCount()).isZero();
        assertThat(collector.isReady()).isFalse();
        assertThat(collector.wastedWakeups()).isZero();
    }

    @Test
    @DisplayName("Первый элемент по ключу принимается, повторный — отвергается без изменения состояния")
    void firstItemIsAcceptedAndDuplicateIsRejected() {
        DataCollector collector = new DataCollector();
        Item first = new Item("v-1", "первое чтение");
        Item duplicate = new Item("v-1", "повторное чтение");

        assertThat(collector.collectItem(first)).as("новый ключ принимается").isTrue();
        assertThat(collector.collectItem(duplicate))
                .as("повторный элемент по тому же ключу отвергается")
                .isFalse();

        assertThat(collector.collectedCount())
                .as("состояние не выросло: принят ровно один элемент")
                .isEqualTo(1);
        assertThat(collector.processedCount())
                .as("счётчик не вырос на отвергнутом элементе")
                .isEqualTo(1);
        assertThat(collector.processedKeyCount()).isEqualTo(1);
        assertThat(collector.isAlreadyProcessed("v-1")).isTrue();
        assertThat(collector.isAlreadyProcessed("v-2")).isFalse();
        assertThat(collector.collected()).containsExactly(first);
    }

    @Test
    @DisplayName("Разные ключи принимаются независимо")
    void differentKeysAreAcceptedIndependently() {
        DataCollector collector = new DataCollector();

        assertThat(collector.collectItem(new Item("v-1", "a"))).isTrue();
        assertThat(collector.collectItem(new Item("v-2", "b"))).isTrue();
        assertThat(collector.collectItem(new Item("v-2", "c"))).isFalse();

        assertThat(collector.collectedCount()).isEqualTo(2);
        assertThat(collector.processedCount()).isEqualTo(2);
        assertThat(collector.processedKeyCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Счётчик обработанных увеличивается на единицу за вызов")
    void incrementProcessedAddsExactlyOne() {
        DataCollector collector = new DataCollector();

        for (int i = 0; i < 1_000; i++) {
            collector.incrementProcessed();
        }

        assertThat(collector.processedCount()).isEqualTo(1_000);
        assertThat(collector.collectedCount())
                .as("примитив счётчика не трогает список и множество ключей")
                .isZero();
        assertThat(collector.processedKeyCount()).isZero();
    }

    @Test
    @DisplayName("Список собранных элементов отдаётся копией: в обход защиты состояние не изменить")
    void collectedSnapshotIsDefensive() {
        DataCollector collector = new DataCollector();
        collector.collectItem(new Item("v-1", "текст"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> collector.collected().add(new Item("v-2", "чужая правка")))
                .as("копия неизменяема")
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(collector.collectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Пустой элемент не принимается")
    void nullItemIsRejected() {
        DataCollector collector = new DataCollector();

        assertThatNullPointerException().isThrownBy(() -> collector.collectItem(null));
        assertThat(collector.collectedCount()).isZero();
    }
}
