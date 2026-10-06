package ru.tsu.tpm.vacancyparser.hw06.atomics;

import ru.tsu.tpm.vacancyparser.hw04.synchronization.Counter;

/**
 * <b>Учебный неверный вариант:</b> счётчик на {@code volatile}-поле, обновляемый через {@code count++}.
 *
 * <p>Класс существует только ради демонстрации ключевого различия: {@code volatile} даёт <b>видимость</b>
 * (запись сразу становится видна другим потокам) и запрещает переупорядочивание, но <b>не даёт
 * атомарности</b> составной операции. {@code count++} — это три шага: прочитать, прибавить, записать.
 * Два потока могут прочитать одно и то же значение, и одно из обновлений потеряется — ровно так же,
 * как без {@code volatile}. Именно поэтому для счётчика нужен {@link AtomicCounter}.
 *
 * <p>Никогда не используется как рабочая реализация: итог зависит от планировщика и не воспроизводим
 * по определению.
 */
public final class VolatileCounterBroken implements Counter {

    /** Значение объявлено {@code volatile}, но составная операция над ним не атомарна. */
    private volatile int count;

    @Override
    public void increment() {
        count++;
    }

    @Override
    public long value() {
        return count;
    }
}
