package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * <b>Учебный неверный вариант:</b> singleton-кэш с раздельной проверкой и созданием (check-then-act).
 *
 * <p>Здесь проверка «кэш пуст» и запись значения — две отдельные операции с {@link AtomicReference}.
 * Каждая из них атомарна по отдельности, но между ними есть окно: пока один поток создаёт своё
 * значение, другой успевает прочитать {@code null} и создаёт своё. Оба записывают, побеждает
 * последний — и разные потоки получают ссылки на <b>разные</b> экземпляры, хотя должны были получить
 * один. Счётчик {@link #creations()} это и показывает: он больше единицы.
 *
 * <p>Класс существует только ради проверки чувствительности и демонстрации дефекта; в рабочем коде не
 * используется.
 *
 * @param <T> тип хранимого значения
 */
public final class BrokenSingletonCache<T> implements SingletonValue<T> {

    private final AtomicReference<T> value = new AtomicReference<>();
    private final AtomicInteger creations = new AtomicInteger();

    @Override
    public T get(Supplier<T> factory) {
        T current = value.get();
        if (current != null) {
            return current;
        }
        T created = factory.get();
        creations.incrementAndGet();
        value.set(created);
        return created;
    }

    @Override
    public int creations() {
        return creations.get();
    }

    @Override
    public int factoryInvocations() {
        return creations.get();
    }

    /** Текущее значение кэша или {@code null}, если он ещё пуст. */
    public T peek() {
        return value.get();
    }
}
