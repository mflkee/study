package ru.tsu.tpm.vacancyparser.hw14.loom.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * «Паспорт» потока: как отличить виртуальный поток от платформенного и увидеть carrier.
 */
class ThreadProbeTest {

    @Test
    @DisplayName("Виртуальный поток виден как виртуальный и сообщает свой carrier")
    void virtualThreadReportsCarrier() throws InterruptedException {
        AtomicReference<ThreadProbe> probe = new AtomicReference<>();
        Thread thread = Thread.ofVirtual()
                .name("hw14-test-vt-0")
                .start(() -> probe.set(ThreadProbe.of(Thread.currentThread())));
        thread.join(5_000L);

        ThreadProbe captured = probe.get();
        assertThat(captured).isNotNull();
        assertThat(captured.virtual()).isTrue();
        assertThat(captured.name()).isEqualTo("hw14-test-vt-0");
        assertThat(captured.mounted()).isTrue();
        assertThat(captured.carrier()).contains("ForkJoinPool");
        assertThat(captured.carrier()).contains("worker");
    }

    @Test
    @DisplayName("Платформенный поток не виртуальный и carrier у него нет")
    void platformThreadHasNoCarrier() throws InterruptedException {
        AtomicReference<ThreadProbe> probe = new AtomicReference<>();
        Thread thread = new Thread(
                () -> probe.set(ThreadProbe.of(Thread.currentThread())), "hw14-test-platform-0");
        thread.start();
        thread.join(5_000L);

        ThreadProbe captured = probe.get();
        assertThat(captured).isNotNull();
        assertThat(captured.virtual()).isFalse();
        assertThat(captured.name()).isEqualTo("hw14-test-platform-0");
        assertThat(captured.carrier()).isEmpty();
        assertThat(captured.mounted()).isFalse();
        assertThat(captured.label()).contains("платформенный");
        assertThatThrownBy(() -> ThreadProbe.of(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
