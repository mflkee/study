package ru.tsu.tpm.vacancyparser.hw06.atomics;

import ru.tsu.tpm.vacancyparser.hw04.synchronization.Counter;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.SynchronizedCounter;

/**
 * Три варианта одного и того же счётчика для сравнительного замера.
 *
 * <p>Нагрузка, число потоков и число операций у всех вариантов совпадают — иначе разница во времени
 * объяснялась бы объёмом работы, а не механизмом. Мониторный вариант берётся из ДЗ 4 намеренно:
 * это уже написанная и проверенная база сравнения, дублировать её нет смысла.
 */
public enum CounterVariant {

    /** Монитор: {@code synchronized}-счётчик из ДЗ 4 — блокирующий способ. */
    MONITOR("монитор (synchronized, ДЗ 4)") {
        @Override
        public Counter newCounter() {
            return new SynchronizedCounter();
        }
    },

    /** Учебный дефект: {@code volatile} без атомарности. */
    VOLATILE_BROKEN("volatile-счётчик без атомарности (дефект)") {
        @Override
        public Counter newCounter() {
            return new VolatileCounterBroken();
        }
    },

    /** Атомарный счётчик без блокировки. */
    ATOMIC("AtomicInteger (lock-free)") {
        @Override
        public Counter newCounter() {
            return new AtomicCounter();
        }
    };

    private final String label;

    CounterVariant(String label) {
        this.label = label;
    }

    /** Новый экземпляр счётчика этого варианта. */
    public abstract Counter newCounter();

    /** Как вариант называется в выводе. */
    public String label() {
        return label;
    }

    /** Допустимо ли для варианта терять обновления (только у учебного дефекта). */
    public boolean losesUpdatesByDesign() {
        return this == VOLATILE_BROKEN;
    }
}
