package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.function.Supplier;

/**
 * Контракт singleton-кэша: вернуть существующее значение или создать единственное.
 *
 * <p>Общий тип нужен, чтобы один и тот же прогон гонки применялся к корректным реализациям
 * ({@link SingletonCache}, {@link UpdateAndGetCache}) и к учебному дефектному варианту
 * ({@link BrokenSingletonCache}).
 *
 * @param <T> тип хранимого значения
 */
public interface SingletonValue<T> {

    /** Вернуть значение из кэша, создав его фабрикой, если кэш пуст. */
    T get(Supplier<T> factory);

    /** Сколько раз значение было успешно сохранено в кэше. */
    int creations();

    /** Сколько раз вызывалась фабрика (в том числе впустую у проигравших гонку). */
    int factoryInvocations();
}
