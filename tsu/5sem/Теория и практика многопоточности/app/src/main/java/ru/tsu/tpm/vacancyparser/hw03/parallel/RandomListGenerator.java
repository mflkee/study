package ru.tsu.tpm.vacancyparser.hw03.parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Генератор входного списка для сравнения последовательного и параллельного исполнения.
 *
 * <p>Состав данных фиксируется намеренно: **ровно половина** значений чётная. Тогда ожидаемый
 * результат фильтрации известен заранее («половина размера»), и в отчёте можно сверить фактический
 * результат с ожидаемым, а не гадать, сколько чётных попалось случайно. Диапазон значений —
 * {@code [0, 2 * size)}, то есть на верхней границе значений в два раза больше, чем элементов.
 *
 * <p>Способ получить ровно половину чётных: генерируются отдельно чётные ({@code 2 * r}) и
 * нечётные ({@code 2 * r + 1}) значения, затем список перемешивается. Перемешивание обязательно —
 * иначе чётные собрались бы в начале списка, и параллельные листы (leaves) получили бы неравные
 * доли работы, что исказило бы замер.
 *
 * <p>Класс чистый и статический: список создаётся **до** всех замеров и передаётся в замер
 * снаружи, поэтому генерация не попадает в измеряемый интервал.
 */
public final class RandomListGenerator {

    private RandomListGenerator() {
    }

    /**
     * Сколько значений в списке размера {@code size} должно оказаться чётными.
     *
     * <p>Половина с округлением вверх: при чётном размере это ровно {@code size / 2}
     * (для 1 000 000 — 500 000). Округление вверх выбрано для определённости на нечётных размерах.
     */
    public static int evenCount(int size) {
        return (size + 1) / 2;
    }

    /** Верхняя граница значений (не включая): {@code [0, upperBound)}. */
    public static int upperBound(int size) {
        return 2 * size;
    }

    /** Создать список заданного размера со случайными значениями в {@code [0, 2 * size)}. */
    public static List<Integer> generate(int size) {
        return generate(size, upperBound(size), ThreadLocalRandom.current());
    }

    /**
     * Создать список заданного размера, беря случайные числа из переданного источника.
     *
     * <p>Источник вынесен в параметр, чтобы тест мог подставить воспроизводимую случайность.
     */
    public static List<Integer> generate(int size, RandomGenerator random) {
        return generate(size, upperBound(size), random);
    }

    /**
     * Создать список заданного размера со значениями в {@code [0, upperBound)}.
     *
     * <p>Граница должна быть положительной и чётной: тогда пары «чётное {@code 2 * r} — нечётное
     * {@code 2 * r + 1}» накрывают диапазон без дыр, и ровное соотношение половин сохраняется.
     */
    public static List<Integer> generate(int size, int upperBound, RandomGenerator random) {
        if (size < 0) {
            throw new IllegalArgumentException("размер списка не может быть отрицательным");
        }
        if (size == 0) {
            return List.of();
        }
        if (upperBound < 2 || upperBound % 2 != 0) {
            throw new IllegalArgumentException(
                    "верхняя граница диапазона должна быть положительной и чётной, получено: " + upperBound);
        }

        int evens = evenCount(size);
        int odds = size - evens;

        // r пробегает [0, upperBound / 2), поэтому значения накрывают весь диапазон [0, upperBound).
        int half = upperBound / 2;
        List<Integer> values = new ArrayList<>(size);
        for (int i = 0; i < evens; i++) {
            values.add(2 * random.nextInt(half));
        }
        for (int i = 0; i < odds; i++) {
            values.add(2 * random.nextInt(half) + 1);
        }
        shuffle(values, random);
        return values;
    }

    /**
     * Перемешать список алгоритмом Фишера — Йетса.
     *
     * <p>Своя реализация вместо {@code Collections.shuffle} нужна потому, что та принимает только
     * {@code java.util.Random}, а здесь источник случайности — более общий {@code RandomGenerator}.
     */
    private static void shuffle(List<Integer> values, RandomGenerator random) {
        for (int i = values.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Integer tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
    }
}
