package ru.tsu.tpm.vacancyparser.hw03.parallel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Три операции из текста задания — фильтрация, преобразование, агрегация — каждая в двух вариантах
 * исполнения: последовательном ({@code stream()}) и параллельном ({@code parallelStream()}).
 *
 * <p><b>Обе ветви — один и тот же Stream API.</b> Различается только источник потока, поэтому
 * сравнение честное: если бы последовательная ветвь была переписана на цикл «ради скорости», она
 * измеряла бы уже другой код, и вывод «последовательный быстрее» ничего не говорил бы о разнице
 * между последовательным и параллельным исполнением.
 *
 * <p><b>Параллельная ветвь — ровно {@code parallelStream()}.</b> Ни своего пула, ни
 * {@code CompletableFuture}, ни настройки параллелизма: измеряется поведение общего
 * {@code ForkJoinPool.commonPool()} из коробки, включая его проигрыш на машине с малым числом
 * процессоров. Управление собственным пулом — предмет ДЗ 8, а не подмена объекта измерения здесь.
 *
 * <p><b>Каждая операция применяется к исходному списку</b> — так, как перечислены операции в тексте
 * задания («операции над этим списком»). Поэтому сумма агрегации — это сумма элементов исходного
 * списка, а не результата предыдущих операций.
 */
public final class StreamOperations {

    private StreamOperations() {
    }

    // ---- фильтрация: только чётные элементы ------------------------------------------------

    /** Число чётных элементов, последовательно. */
    public static long filterEvenSequential(List<Integer> data) {
        return data.stream().filter(StreamOperations::isEven).count();
    }

    /** Число чётных элементов, параллельно (общий пул). */
    public static long filterEvenParallel(List<Integer> data) {
        return data.parallelStream().filter(StreamOperations::isEven).count();
    }

    // ---- преобразование: умножение на 2 -----------------------------------------------------

    /** Каждый элемент, умноженный на 2, последовательно. */
    public static List<Integer> doubleSequential(List<Integer> data) {
        return data.stream().map(StreamOperations::doubled).collect(Collectors.toList());
    }

    /** Каждый элемент, умноженный на 2, параллельно (общий пул). */
    public static List<Integer> doubleParallel(List<Integer> data) {
        return data.parallelStream().map(StreamOperations::doubled).collect(Collectors.toList());
    }

    // ---- агрегация: сумма ---------------------------------------------------------------

    /**
     * Сумма элементов, последовательно.
     *
     * <p>Считается в {@code long}: сумма миллиона значений вплоть до 2 000 000 не помещается в
     * {@code int}, и переполнение было бы незаметной ошибкой результата.
     */
    public static long sumSequential(List<Integer> data) {
        return data.stream().mapToLong(Integer::longValue).sum();
    }

    /** Сумма элементов, параллельно (общий пул). */
    public static long sumParallel(List<Integer> data) {
        return data.parallelStream().mapToLong(Integer::longValue).sum();
    }

    private static boolean isEven(Integer value) {
        return value % 2 == 0;
    }

    private static Integer doubled(Integer value) {
        return value * 2;
    }
}
