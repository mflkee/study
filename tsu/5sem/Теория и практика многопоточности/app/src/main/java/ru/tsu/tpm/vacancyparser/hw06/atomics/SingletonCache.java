package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Singleton-кэш на {@link AtomicReference} с объединением проверки и создания в одну атомарную
 * операцию.
 *
 * <p>{@code AtomicReference} делает атомарными <b>отдельные</b> чтение и запись, но не пару
 * «прочитал — решил — записал». Наивная проверка «если пусто — создать и записать» — это
 * check-then-act: несколько потоков одновременно видят {@code null}, каждый создаёт свой экземпляр, и
 * в кэше остаётся случайный, а остальные вызывающие получают разные объекты (см.
 * {@link BrokenSingletonCache}).
 *
 * <p>Здесь создание превращено в состязание: поток создаёт кандидата и пытается установить его
 * атомарно через {@code compareAndSet}. Выигравший сохраняет значение, проигравший <b>перечитывает</b>
 * уже установленное и возвращает его — своё создание отбрасывается. В результате значение
 * сохраняется ровно один раз, и все потоки получают ссылку на один и тот же экземпляр.
 *
 * <p>Плата за явный {@code compareAndSet} видна и измеряется: фабрику вызывают все потоки, увидевшие
 * {@code null}, поэтому {@link #factoryInvocations()} может быть больше единицы, хотя успешное
 * создание всегда одно. Альтернатива {@link UpdateAndGetCache} вызывает фабрику только у победителя,
 * и это её преимущество.
 *
 * @param <T> тип хранимого значения
 */
public final class SingletonCache<T> implements SingletonValue<T> {

    private final AtomicReference<T> value = new AtomicReference<>();
    private final AtomicInteger creations = new AtomicInteger();
    private final AtomicInteger factoryInvocations = new AtomicInteger();

    @Override
    public T get(Supplier<T> factory) {
        T current = value.get();
        if (current != null) {
            return current;
        }

        T candidate = factory.get();
        factoryInvocations.incrementAndGet();
        if (value.compareAndSet(null, candidate)) {
            creations.incrementAndGet();
            return candidate;
        }
        // Гонку проиграли: установлено чужое значение — возвращаем именно его, а не своё создание.
        return value.get();
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
