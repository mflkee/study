package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Альтернативная корректная реализация singleton-кэша через {@code updateAndGet}.
 *
 * <p>{@code updateAndGet} применяет функцию к текущему значению и атомарно записывает результат,
 * повторяя попытку при проигранной гонке. Функция вызывается только для пустого кэша; если при
 * повторной попытке значение уже не {@code null}, возвращается оно, а фабрика больше не вызывается.
 * Отсюда преимущество перед явным {@code compareAndSet} из {@link SingletonCache}: впустую созданный
 * кандидат возникает реже.
 *
 * <p>Успешное создание фиксируется точно: функция сохранена в локальный «ящик», и после возврата
 * {@code updateAndGet} сравнивается ссылка. Совпала — значит, именно это создание и оказалось в кэше
 * (созданный объект уникален, и увидеть его как результат можно, только если он сохранён). Не совпала —
 * поток проиграл гонку, и его кандидат отброшен.
 *
 * @param <T> тип хранимого значения
 */
public final class UpdateAndGetCache<T> implements SingletonValue<T> {

    private final AtomicReference<T> value = new AtomicReference<>();
    private final AtomicInteger creations = new AtomicInteger();
    private final AtomicInteger factoryInvocations = new AtomicInteger();

    @Override
    public T get(Supplier<T> factory) {
        Object[] createdHere = new Object[1];
        T result = value.updateAndGet(current -> {
            if (current != null) {
                return current;
            }
            T candidate = factory.get();
            factoryInvocations.incrementAndGet();
            createdHere[0] = candidate;
            return candidate;
        });
        if (result == createdHere[0]) {
            creations.incrementAndGet();
        }
        return result;
    }

    @Override
    public int creations() {
        return creations.get();
    }

    @Override
    public int factoryInvocations() {
        return factoryInvocations.get();
    }

    /** Текущее значение кэша или {@code null}, если он ещё пуст. */
    public T peek() {
        return value.get();
    }
}
