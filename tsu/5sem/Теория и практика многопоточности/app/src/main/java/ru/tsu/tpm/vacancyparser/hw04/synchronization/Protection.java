package ru.tsu.tpm.vacancyparser.hw04.synchronization;

/**
 * Переключатель режима защиты для сравнения производительности.
 *
 * <p>Один и тот же харнесс выполняется в двух режимах: с защитой и без неё. Меняется только
 * стратегия счётчика, поэтому обе ветви исполняют одинаковую нагрузку и отличаются ровно наличием
 * синхронизации — иначе сравнение было бы некорректным.
 */
public enum Protection {

    /** Защита включена: монитор на каждом обращении к общему состоянию. */
    ON("с защитой (synchronized)"),

    /** Защита выключена: та же нагрузка без синхронизации, для сравнения и демонстрации дефекта. */
    OFF("без защиты (race condition)");

    private final String label;

    Protection(String label) {
        this.label = label;
    }

    /** Новый счётчик в выбранном режиме защиты. */
    public Counter newCounter() {
        return this == ON ? new SynchronizedCounter() : new UnsynchronizedCounter();
    }

    /** Как режим называется в выводе. */
    public String label() {
        return label;
    }
}
