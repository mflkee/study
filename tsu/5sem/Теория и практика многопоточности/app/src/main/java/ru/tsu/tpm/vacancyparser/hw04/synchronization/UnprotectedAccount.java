package ru.tsu.tpm.vacancyparser.hw04.synchronization;

/**
 * Счёт без защиты — эталон для сравнения производительности и демонстрации дефекта.
 *
 * <p>Существует только ради сравнения: показать, что даёт синхронизация по времени и что теряется
 * без неё. Как рабочая модель счёта не используется: результат зависит от планировщика и машины.
 *
 * <p>Изменение баланса — чтение, сложение и запись. Между чтением и записью другой поток успевает
 * прочитать то же значение, и одно из изменений теряется.
 */
public final class UnprotectedAccount {

    private final String id;
    private long balance;

    public UnprotectedAccount(String id, long balance) {
        this.id = id;
        this.balance = balance;
    }

    public String id() {
        return id;
    }

    /** Изменить баланс без какой-либо защиты. */
    public void add(long delta) {
        balance += delta;
    }

    public long balance() {
        return balance;
    }

    @Override
    public String toString() {
        return id;
    }
}
