package ru.tsu.tpm.vacancyparser.hw03.parallel;

/**
 * Параметры демонстрации ДЗ 3.
 *
 * <p>Значения по умолчанию — из текста задания: список ровно из 1 000 000 чисел и 5 повторов.
 * Параметры выносятся наружу, потому что отладка с полным объёмом мучительна: на один прогон
 * приходится 3 операции × 2 варианта × (1 прогрев + 5 повторов) = 36 проходов по миллиону
 * элементов. Фактически применённые значения печатаются в выводе, чтобы уменьшённый размер не
 * остался незамеченным.
 *
 * @param size       размер списка; по умолчанию 1 000 000, как требует текст задания
 * @param upperBound верхняя граница значений, не включая; по умолчанию {@code 2 * size}
 * @param repeats    число учитываемых повторов на каждый вариант операции
 * @param warmupRuns число прогревочных прогонов на каждый вариант операции
 */
public record BenchmarkConfig(int size, int upperBound, int repeats, int warmupRuns) {

    /** Размер из текста задания. */
    public static final int DEFAULT_SIZE = 1_000_000;

    /** Число повторов по умолчанию. */
    public static final int DEFAULT_REPEATS = 5;

    /** Прогрев по умолчанию. */
    public static final int DEFAULT_WARMUP = 1;

    public BenchmarkConfig {
        if (size < 0) {
            throw new IllegalArgumentException("размер списка не может быть отрицательным");
        }
        if (upperBound < 2 || upperBound % 2 != 0) {
            throw new IllegalArgumentException("верхняя граница значений должна быть положительной и чётной");
        }
        if (repeats < 1) {
            throw new IllegalArgumentException("нужен хотя бы один повтор");
        }
        if (warmupRuns < 0) {
            throw new IllegalArgumentException("число прогревочных прогонов не может быть отрицательным");
        }
    }

    /** Параметры из текста задания. */
    public static BenchmarkConfig defaults() {
        return new BenchmarkConfig(
                DEFAULT_SIZE,
                RandomListGenerator.upperBound(DEFAULT_SIZE),
                DEFAULT_REPEATS,
                DEFAULT_WARMUP);
    }

    /**
     * Параметры с уменьшённым размером списка и производной границей значений — для быстрой отладки.
     *
     * <p>Граница значений не берётся «на глаз», а выводится из размера: диапазон обязан остаться
     * вдвое шире числа элементов, иначе значения начнут повторяться и состав данных изменится.
     */
    public static BenchmarkConfig ofSize(int size) {
        return new BenchmarkConfig(size, RandomListGenerator.upperBound(size), DEFAULT_REPEATS, DEFAULT_WARMUP);
    }

    /** Описание диапазона значений так, как оно печатается в условиях замера. */
    public String rangeDescription() {
        return "0.." + (upperBound - 1);
    }
}
