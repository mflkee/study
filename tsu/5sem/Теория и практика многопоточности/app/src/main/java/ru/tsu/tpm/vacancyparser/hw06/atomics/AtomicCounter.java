package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.concurrent.atomic.AtomicInteger;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.Counter;

/**
 * Потокобезопасный счётчик на {@link AtomicInteger}.
 *
 * <p>Увеличение выполняется атомарной операцией «прочитать — прибавить — записать» как единое целое,
 * поэтому обновления не теряются даже при большом числе потоков. В отличие от {@code volatile}-поля
 * (см. {@link VolatileCounterBroken}) атомарный тип закрывает <b>всю</b> составную операцию, а не
 * только видимость её отдельных шагов.
 *
 * <p>Реализует контракт {@link Counter} из ДЗ 4 намеренно: так один и тот же инвариант «N потоков →
 * ровно N увеличений» и один и тот же харнесс сравнения применяются к монитору, к дефектному
 * {@code volatile}-счётчику и к этому классу — меняется только механизм.
 */
public final class AtomicCounter implements Counter {

    private final AtomicInteger value = new AtomicInteger();

    @Override
    public void increment() {
        value.incrementAndGet();
    }

    @Override
    public long value() {
        return value.get();
    }

    /** Увеличить и вернуть новое значение — атомарно. */
    public int incrementAndGet() {
        return value.incrementAndGet();
    }

    /** Текущее значение без внешней синхронизации. */
    public int get() {
        return value.get();
    }
}
