package ru.tsu.tpm.vacancyparser.hw07.deadlock.deadlock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;

/**
 * Стенд взаимной блокировки: детерминированное воспроизведение и гарантированное освобождение.
 */
class DeadlockRigTest {

    @Test
    @Timeout(60)
    @DisplayName("После барьера первый ресурс удерживают оба потока, взаимного ожидания ещё нет")
    void firstResourceHeldByEachThreadBeforeDeadlock() throws InterruptedException {
        DeadlockRig rig = new DeadlockRig();
        rig.start();
        try {
            assertThat(rig.awaitFirstLocks(2_000L)).isTrue();
            assertThat(rig.bothFirstResourcesHeld())
                    .as("resourceA удерживает поток A, resourceB — поток B")
                    .isTrue();
            assertThat(ThreadDump.hasDeadlock())
                    .as("до второго захвата цикла ожидания быть не может")
                    .isFalse();
        } finally {
            assertThat(rig.release()).as("потоки освобождены").isTrue();
        }
        assertThat(rig.threads()).allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
    }

    @Test
    @Timeout(60)
    @DisplayName("Второй захват даёт взаимное ожидание, JVM называет оба потока")
    void secondLockReachesDeadlockConfirmedByJvm() throws InterruptedException {
        DeadlockRig rig = new DeadlockRig();
        rig.start();
        boolean released;
        try {
            rig.awaitFirstLocks(2_000L);
            rig.proceedToSecondLock();
            assertThat(rig.awaitDeadlock(5_000L))
                    .as("в течение 5 секунд JVM обязана распознать цикл между Deadlock-A и Deadlock-B")
                    .isTrue();
            assertThat(rig.deadlockedThreadNames())
                    .contains(DeadlockRig.THREAD_A)
                    .contains(DeadlockRig.THREAD_B);
        } finally {
            released = rig.release();
        }
        assertThat(released).as("после прерывания оба потока завершаются").isTrue();
        assertThat(rig.threads()).allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
        assertThat(ThreadDump.hasDeadlock())
                .as("после освобождения взаимной блокировки в процессе не остаётся")
                .isFalse();
    }

    @Test
    @Timeout(180)
    @DisplayName("20 прогонов подряд: взаимное ожидание достигается и снимается в каждом")
    void deadlockReproducesInEveryRun() throws InterruptedException {
        for (int run = 1; run <= 20; run++) {
            DeadlockRig rig = new DeadlockRig();
            rig.start();
            boolean ready = rig.awaitFirstLocks(2_000L);
            rig.proceedToSecondLock();
            boolean deadlocked = ready && rig.awaitDeadlock(5_000L);
            boolean released = rig.release();

            assertThat(deadlocked)
                    .as("прогон %d: взаимное ожидание обязано достигаться детерминированно", run)
                    .isTrue();
            assertThat(released)
                    .as("прогон %d: потоки обязаны быть освобождены", run)
                    .isTrue();
        }
    }
}
