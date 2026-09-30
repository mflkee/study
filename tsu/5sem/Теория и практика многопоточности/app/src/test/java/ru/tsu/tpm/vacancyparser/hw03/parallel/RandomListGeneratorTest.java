package ru.tsu.tpm.vacancyparser.hw03.parallel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки входных данных: размер, диапазон, случайность и ровно половина чётных.
 */
class RandomListGeneratorTest {

    /** Меньший размер для тестов: свойства те же, а выполняется заметно быстрее. */
    private static final int SIZE = 10_000;

    @Test
    @DisplayName("Размер списка совпадает с заданным")
    void listSizeMatchesRequested() {
        List<Integer> data = RandomListGenerator.generate(SIZE);

        assertThat(data).hasSize(SIZE);
        assertThat(RandomListGenerator.generate(BenchmarkConfig.DEFAULT_SIZE))
                .as("размер по умолчанию — миллион, как требует текст задания")
                .hasSize(BenchmarkConfig.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("Доля чётных — ровно половина, а не «примерно половина»")
    void exactlyHalfOfValuesAreEven() {
        List<Integer> data = RandomListGenerator.generate(SIZE);

        long evens = data.stream().filter(value -> value % 2 == 0).count();

        assertThat(evens)
                .as("на чётном размере ровно половина; на нечётном — половина с округлением вверх")
                .isEqualTo(RandomListGenerator.evenCount(SIZE))
                .isEqualTo(SIZE / 2);
    }

    @Test
    @DisplayName("Все значения попадают в заявленный диапазон")
    void allValuesAreWithinDeclaredRange() {
        List<Integer> data = RandomListGenerator.generate(SIZE);

        assertThat(data).allSatisfy(value -> assertThat(value)
                .isGreaterThanOrEqualTo(0)
                .isLessThan(RandomListGenerator.upperBound(SIZE)));
    }

    @Test
    @DisplayName("Значения случайны: два списка различаются, порядок не монотонный")
    void valuesAreRandom() {
        List<Integer> first = RandomListGenerator.generate(SIZE);
        List<Integer> second = RandomListGenerator.generate(SIZE);

        assertThat(first)
                .as("два независимых списка одинакового размера не могут совпасть поэлементно")
                .isNotEqualTo(second);

        boolean sorted = true;
        for (int i = 1; i < first.size() && sorted; i++) {
            sorted = first.get(i - 1) <= first.get(i);
        }
        assertThat(sorted).as("перемешивание обязательно: иначе чётные собрались бы в начале").isFalse();

        Set<Integer> distinct = first.stream().collect(Collectors.toSet());
        assertThat(distinct.size())
                .as("значений в диапазоне вдвое больше, чем элементов, поэтому повторов мало")
                .isGreaterThan(SIZE / 2);
    }

    @Test
    @DisplayName("Одинаковый источник случайности даёт одинаковый список — замер воспроизводим")
    void sameSourceProducesSameList() {
        List<Integer> first = RandomListGenerator.generate(SIZE, new Random(1L));
        List<Integer> second = RandomListGenerator.generate(SIZE, new Random(1L));

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("Размер границы диапазона проверяется: граница должна быть чётной и положительной")
    void upperBoundIsValidated() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomListGenerator.generate(SIZE, 1, new Random(1L)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomListGenerator.generate(SIZE, 0, new Random(1L)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomListGenerator.generate(-1, 4, new Random(1L)));
    }

    @Test
    @DisplayName("Пустой список допустим и не падает")
    void emptyListIsAllowed() {
        assertThat(RandomListGenerator.generate(0)).isEmpty();
    }
}
