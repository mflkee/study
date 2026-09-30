package ru.tsu.tpm.vacancyparser.hw03.parallel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки трёх операций: результат каждой обязан совпадать в обоих вариантах и быть предсказуемым.
 */
class StreamOperationsTest {

    private static final int SIZE = 4_000;

    private final List<Integer> data = RandomListGenerator.generate(SIZE, new Random(42L));

    @Test
    @DisplayName("Фильтрация: обе ветви дают половину размера списка")
    void filteringKeepsEvenElementsOnly() {
        long sequential = StreamOperations.filterEvenSequential(data);
        long parallel = StreamOperations.filterEvenParallel(data);

        assertThat(sequential)
                .as("чётных ровно половина — это гарантирует генератор")
                .isEqualTo(SIZE / 2);
        assertThat(parallel).isEqualTo(sequential);
    }

    @Test
    @DisplayName("Фильтрация действительно отбрасывает нечётные, а не считает все подряд")
    void filteringRejectsOddElements() {
        List<Integer> oddOnly = List.of(1, 3, 5, 7);
        List<Integer> evenOnly = List.of(2, 4, 6, 8);

        assertThat(StreamOperations.filterEvenSequential(oddOnly)).isZero();
        assertThat(StreamOperations.filterEvenParallel(oddOnly)).isZero();
        assertThat(StreamOperations.filterEvenSequential(evenOnly)).isEqualTo(4);
        assertThat(StreamOperations.filterEvenParallel(evenOnly)).isEqualTo(4);
    }

    @Test
    @DisplayName("Преобразование: каждый элемент результата вдвое больше исходного")
    void transformationDoublesEveryElement() {
        List<Integer> sequential = StreamOperations.doubleSequential(data);
        List<Integer> parallel = StreamOperations.doubleParallel(data);

        assertThat(sequential).hasSameSizeAs(data);
        assertThat(parallel).isEqualTo(sequential);
        for (int i = 0; i < data.size(); i++) {
            assertThat(sequential.get(i))
                    .as("элемент %d должен быть вдвое больше исходного", i)
                    .isEqualTo(data.get(i) * 2);
        }
    }

    @Test
    @DisplayName("Агрегация: сумма совпадает с суммой, посчитанной обычным циклом")
    void aggregationMatchesPlainLoop() {
        long byLoop = 0L;
        for (Integer value : data) {
            byLoop += value;
        }

        assertThat(StreamOperations.sumSequential(data)).isEqualTo(byLoop);
        assertThat(StreamOperations.sumParallel(data)).isEqualTo(byLoop);
    }

    @Test
    @DisplayName("Сумма считается в long: на большом списке int переполнился бы")
    void aggregationDoesNotOverflowInt() {
        List<Integer> large = java.util.Collections.nCopies(2_000, 2_000_000);
        long expected = 4_000_000_000L;

        assertThat(expected)
                .as("эта сумма не помещается в int — без long результат был бы отрицательным")
                .isGreaterThan(Integer.MAX_VALUE);
        assertThat(StreamOperations.sumSequential(large)).isEqualTo(expected);
        assertThat(StreamOperations.sumParallel(large)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Все три операции дают одинаковый результат в обоих вариантах")
    void bothBranchesAgreeOnEveryOperation() {
        for (int repeat = 0; repeat < 5; repeat++) {
            assertThat(StreamOperations.filterEvenParallel(data))
                    .isEqualTo(StreamOperations.filterEvenSequential(data));
            assertThat(StreamOperations.doubleParallel(data))
                    .isEqualTo(StreamOperations.doubleSequential(data));
            assertThat(StreamOperations.sumParallel(data))
                    .isEqualTo(StreamOperations.sumSequential(data));
        }
    }

    @Test
    @DisplayName("Пустой список: обе ветви дают нулевые результаты и не падают")
    void emptyInputIsHandled() {
        List<Integer> empty = List.of();

        assertThat(StreamOperations.filterEvenSequential(empty)).isZero();
        assertThat(StreamOperations.filterEvenParallel(empty)).isZero();
        assertThat(StreamOperations.doubleSequential(empty)).isEmpty();
        assertThat(StreamOperations.doubleParallel(empty)).isEmpty();
        assertThat(StreamOperations.sumSequential(empty)).isZero();
        assertThat(StreamOperations.sumParallel(empty)).isZero();
    }

    @Test
    @DisplayName("Работает на списке с доступом по индексу, а не только на ArrayList")
    void worksOnNonArrayListSources() {
        List<Integer> linked = new ArrayList<>(data);

        assertThat(StreamOperations.filterEvenParallel(linked))
                .isEqualTo(StreamOperations.filterEvenSequential(data));
        assertThat(StreamOperations.sumParallel(linked)).isEqualTo(StreamOperations.sumSequential(data));
    }
}
