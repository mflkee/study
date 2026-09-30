package ru.tsu.tpm.vacancyparser.hw03.parallel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки единой точки входа замера: одинаковый вход, одинаковые результаты, и генерация не
 * участвует в измеряемом интервале.
 */
class BenchmarkServiceTest {

    /** Малый размер: свойства те же, а тесты быстрые. */
    private static final BenchmarkConfig SMALL = new BenchmarkConfig(20_000, 40_000, 2, 1);

    @Test
    @DisplayName("Обе ветви на одном входе дают идентичные результаты по всем трём операциям")
    void bothBranchesProduceIdenticalResults() {
        List<Integer> data = RandomListGenerator.generate(SMALL.size(), new Random(7L));

        BenchmarkOutcome outcome = new BenchmarkService(SMALL).compare(data);

        assertThat(outcome.sequentialResults().evenCount())
                .isEqualTo(outcome.parallelResults().evenCount())
                .isEqualTo(SMALL.size() / 2);
        assertThat(outcome.sequentialResults().doubled())
                .isEqualTo(outcome.parallelResults().doubled());
        assertThat(outcome.sequentialResults().sum()).isEqualTo(outcome.parallelResults().sum());
        assertThat(outcome.resultsMatch()).isTrue();
    }

    @Test
    @DisplayName("Замер охватывает три операции в порядке задания и оба варианта каждой")
    void measuresAllThreeOperationsInOrder() {
        List<Integer> data = RandomListGenerator.generate(SMALL.size(), new Random(11L));

        BenchmarkOutcome outcome = new BenchmarkService(SMALL).compare(data);

        assertThat(outcome.comparisons())
                .extracting(OperationComparison::name)
                .containsExactly("фильтрация", "преобразование", "агрегация");
        assertThat(outcome.comparisons()).allSatisfy(comparison -> {
            assertThat(comparison.resultsMatch()).isTrue();
            assertThat(comparison.sequential().repeats()).isEqualTo(SMALL.repeats());
            assertThat(comparison.parallel().repeats()).isEqualTo(SMALL.repeats());
            assertThat(comparison.sequential().average()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
            assertThat(comparison.parallel().average()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
        });
        assertThat(outcome.warmupRuns()).isEqualTo(SMALL.warmupRuns());
        assertThat(outcome.repeats()).isEqualTo(SMALL.repeats());
    }

    @Test
    @DisplayName("Измеряемая операция работает именно на переданном списке, а не на своём")
    void operationRunsOnTheSuppliedList() {
        CountingList data = new CountingList(RandomListGenerator.generate(SMALL.size(), new Random(3L)));

        new BenchmarkService(SMALL).compare(data);

        // Операции обязаны были обратиться к потокам именно этого объекта: если бы сервис
        // сгенерировал свой список, счётчики остались бы нулевыми.
        assertThat(data.sequentialStreamCalls())
                .as("последовательная ветвь работала на этом же списке")
                .isGreaterThan(0);
        assertThat(data.parallelStreamCalls())
                .as("параллельная ветвь работала на этом же списке")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("Генерация не может попасть в измеряемый интервал: сервис её не вызывает")
    void generationCannotEnterTheMeasuredInterval() {
        // Структурная проверка, а не замер: у сервиса нет ни способа задать размер, ни зависимости
        // от генератора, поэтому создать список внутри измеряемого участка ему нечем.
        assertThat(Stream.of(BenchmarkService.class.getDeclaredConstructors()))
                .as("ни один конструктор не принимает размер списка")
                .allSatisfy(constructor -> assertThat(parameterTypes(constructor))
                        .doesNotContain(int.class, Integer.class));
        assertThat(Stream.concat(
                        Stream.of(BenchmarkService.class.getDeclaredMethods()),
                        Stream.of(BenchmarkService.class.getDeclaredConstructors()))
                .map(member -> member instanceof Method method
                        ? method.getName()
                        : ((Constructor<?>) member).getName())
                .toList())
                .as("сервис не обращается к генератору")
                .noneSatisfy(name -> assertThat(name.toLowerCase()).contains("generate"));

        assertThat(Stream.of(BenchmarkService.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getType))
                .as("в полях сервиса нет ни генератора, ни конфигурации с генерацией")
                .doesNotContain(RandomListGenerator.class);
    }

    @Test
    @DisplayName("Время операции не растёт пропорционально времени генерации: генерация не внутри замера")
    void operationTimeDoesNotIncludeGeneration() {
        // Оценка на реальных измерениях: генерация миллиона случайных чисел дороже, чем одна
        // операция над готовым списком. Если бы генерация шла внутри участка, время операции
        // оказалось бы не меньше времени генерации.
        int size = 400_000;
        BenchmarkConfig config = new BenchmarkConfig(size, RandomListGenerator.upperBound(size), 1, 0);
        List<Integer> data = RandomListGenerator.generate(size);

        java.time.Duration generation = ru.tsu.tpm.vacancyparser.common.Timings.measure(
                () -> RandomListGenerator.generate(size));
        BenchmarkOutcome outcome = new BenchmarkService(config).compare(data);
        java.time.Duration fastestOperation = outcome.comparisons().stream()
                .flatMap(comparison -> Stream.of(
                        comparison.sequential().average(), comparison.parallel().average()))
                .min(java.time.Duration::compareTo)
                .orElseThrow();

        assertThat(fastestOperation)
                .as("самая быстрая операция (%s) должна быть заметно дешевле генерации (%s)",
                        fastestOperation, generation)
                .isLessThan(generation);
    }

    @Test
    @DisplayName("Нужен хотя бы один повтор, иначе статистики нет")
    void repeatsMustBePositive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BenchmarkConfig(100, 200, 0, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BenchmarkConfig(100, 200, 5, -1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BenchmarkConfig(100, 101, 5, 1));
    }

    @Test
    @DisplayName("Параметры по умолчанию соответствуют тексту задания: миллион и пять повторов")
    void defaultsMatchAssignment() {
        BenchmarkConfig defaults = BenchmarkConfig.defaults();

        assertThat(defaults.size()).isEqualTo(1_000_000);
        assertThat(defaults.repeats()).isEqualTo(5);
        assertThat(defaults.upperBound()).isEqualTo(2_000_000);
        assertThat(defaults.rangeDescription()).isEqualTo("0..1999999");
    }

    private static List<Class<?>> parameterTypes(Constructor<?> constructor) {
        return Arrays.asList(constructor.getParameterTypes());
    }

    /** Список, считающий обращения к своим потокам: доказывает, что работал именно он. */
    private static final class CountingList extends ArrayList<Integer> {

        private int sequentialStreamCalls;
        private int parallelStreamCalls;

        private CountingList(List<Integer> values) {
            super(values);
        }

        @Override
        public Stream<Integer> stream() {
            sequentialStreamCalls++;
            return super.stream();
        }

        @Override
        public Stream<Integer> parallelStream() {
            parallelStreamCalls++;
            return super.parallelStream();
        }

        private int sequentialStreamCalls() {
            return sequentialStreamCalls;
        }

        private int parallelStreamCalls() {
            return parallelStreamCalls;
        }
    }
}
