package ru.tsu.tpm.vacancyparser.hw12.collections.cache;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Ключ истечения срока жизни для {@link java.util.concurrent.DelayQueue}.
 *
 * <p>Очередь задержки отдаёт элемент только после того, как его срок наступил, и делает это без
 * опроса: поток-уборщик спит внутри {@code poll(timeout)}, а не крутится в цикле. Сравнение — по
 * времени истечения, поэтому ближайшие к истечению записи извлекаются первыми.
 *
 * @param vacancyId      идентификатор вакансии
 * @param sequence       версия записи, к которой относится ключ
 * @param expiresAtNanos момент истечения срока жизни
 */
record ExpiringKey(String vacancyId, long sequence, long expiresAtNanos) implements Delayed {

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(expiresAtNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        if (other == this) {
            return 0;
        }
        long difference = expiresAtNanos - ((ExpiringKey) other).expiresAtNanos;
        return difference < 0 ? -1 : (difference > 0 ? 1 : 0);
    }
}
