package ru.tsu.tpm.vacancyparser.hw04.synchronization;

/**
 * Счётчик под защитой монитора: изменение и чтение — {@code synchronized}-методы.
 *
 * <p>Монитор гарантирует, что пара «прочитать — увеличить — записать» выполняется как единая
 * атомарная операция, поэтому ни одно обновление не теряется. Плата — синхронизация на каждом
 * обращении: при большом числе потоков это заметно на времени.
 */
public final class SynchronizedCounter implements Counter {

    private long value;

    @Override
    public synchronized void increment() {
        value++;
    }

    @Override
    public synchronized long value() {
        return value;
    }
}
