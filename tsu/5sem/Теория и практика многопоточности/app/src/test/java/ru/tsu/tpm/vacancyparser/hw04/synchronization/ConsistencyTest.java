package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Согласованность разделяемого состояния после нагрузки: список, счётчик и множество ключей
 * обязаны сходиться между собой.
 *
 * <p>Проверка идёт через {@link DataCollector#invariantViolations()}. Отдельно проверяется, что
 * эта проверка не «пустая»: на намеренно рассогласованном состоянии она обязана выдать нарушения —
 * иначе зелёный тест ничего не доказывал бы.
 */
class ConsistencyTest {

    private static final int THREADS = 8;
    private static final int ITEMS_PER_THREAD = 500;

    @Test
    @DisplayName("После многопоточного сбора все три величины согласованы")
    void stateIsConsistentAfterConcurrentCollection() throws InterruptedException {
        DataCollector collector = new DataCollector();

        ConcurrentLoad.runIndexed(THREADS, index -> {
            for (int i = 0; i < ITEMS_PER_THREAD; i++) {
                collector.collectItem(new Item("vacancy-" + index + "-" + i, "текст"));
            }
        });

        assertThat(collector.invariantViolations()).isEmpty();
        assertThat(collector.collectedCount()).isEqualTo(THREADS * ITEMS_PER_THREAD);
        assertThat(collector.processedCount()).isEqualTo(THREADS * ITEMS_PER_THREAD);
        assertThat(collector.processedKeyCount()).isEqualTo(THREADS * ITEMS_PER_THREAD);
        assertThat(collector.collected())
                .as("каждый собранный элемент на месте, дубликатов нет")
                .hasSize(THREADS * ITEMS_PER_THREAD)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Смешанная нагрузка: сбор, учёт и чтение одновременно не рассогласуют состояние")
    void mixedLoadKeepsStateConsistent() throws InterruptedException {
        DataCollector collector = new DataCollector();

        ConcurrentLoad.runIndexed(THREADS, index -> {
            for (int i = 0; i < ITEMS_PER_THREAD; i++) {
                collector.collectItem(new Item("v-" + index + "-" + i, "текст"));
                // Читатели работают параллельно писателям: без защиты они увидели бы
                // промежуточное состояние — например, элемент уже в списке, но ключ ещё не помечен.
                collector.collectedCount();
                collector.processedCount();
                collector.processedKeyCount();
                collector.isAlreadyProcessed("v-" + index + "-" + i);
            }
        });

        assertThat(collector.invariantViolations()).isEmpty();
    }

    @Test
    @DisplayName("Проверка инварианта не пустая: рассогласование обнаруживается")
    void invariantCheckCatchesDesynchronization() {
        DataCollector counterOnly = new DataCollector();
        counterOnly.incrementProcessed();

        assertThat(counterOnly.invariantViolations())
                .as("счётчик увеличен в обход сбора — расхождение обязано быть найдено")
                .isNotEmpty()
                .allSatisfy(violation -> assertThat(violation).contains("не равен"));

        DataCollector collectedOnly = new DataCollector();
        collectedOnly.collectItem(new Item("v-1", "текст"));

        assertThat(collectedOnly.invariantViolations())
                .as("согласованный сбор нарушений не даёт")
                .isEmpty();

        DataCollector empty = new DataCollector();

        assertThat(empty.invariantViolations())
                .as("пустое состояние согласовано по определению")
                .isEmpty();
        assertThat(empty.processedCount()).isZero();
        assertThat(empty.collectedCount()).isZero();
        assertThat(empty.processedKeyCount()).isZero();
    }

    @Test
    @DisplayName("Описания нарушений перечисляют каждое расхождение по имени")
    void violationsDescribeEveryMismatch() {
        DataCollector desynchronized = new DataCollector();
        desynchronized.incrementProcessed();
        desynchronized.incrementProcessed();

        List<String> violations = desynchronized.invariantViolations();

        assertThat(violations)
                .as("список пуст (0), счётчик равен 2, ключей нет (0): нарушены два равенства — "
                        + "«список = счётчик» и «счётчик = ключи», а «ключи = список» держится само собой")
                .hasSize(2);
        assertThat(String.join("; ", violations))
                .contains("размер списка 0 не равен счётчику 2")
                .contains("счётчик 2 не равен числу уникальных ключей 0");
    }
}
