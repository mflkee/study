package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Сверка множеств добавленного и извлечённого: ничего не потеряно, ничего не продублировано.
 */
class NoLossNoDuplicationTest {

    @Test
    @DisplayName("Множества добавленных и извлечённых совпадают, дубликатов нет")
    @Timeout(120)
    void setsMatchWithoutDuplicates() throws InterruptedException {
        ProducerConsumerRunner.Result result =
                ProducerConsumerRunner.run(new BoundedBuffer<>(3), 5, 5, 400);

        Set<String> taken = new LinkedHashSet<>(result.taken());

        assertThat(result.taken())
                .as("каждый идентификатор среди извлечённых встречается ровно один раз")
                .hasSize(taken.size());
        assertThat(taken)
                .as("множество извлечённых совпадает с множеством добавленных")
                .containsExactlyInAnyOrderElementsOf(result.expectedItems());
    }

    @Test
    @DisplayName("Мониторная реализация даёт тот же результат по множествам")
    @Timeout(120)
    void monitorImplementationGivesSameElements() throws InterruptedException {
        ProducerConsumerRunner.Result lockRun =
                ProducerConsumerRunner.run(new BoundedBuffer<>(2), 3, 3, 300);
        ProducerConsumerRunner.Result monitorRun =
                ProducerConsumerRunner.run(new MonitorBuffer<>(2), 3, 3, 300);

        assertThat(new LinkedHashSet<>(monitorRun.taken()))
                .as("различается только время, результат обязан совпасть")
                .isEqualTo(new LinkedHashSet<>(lockRun.taken()));
        assertThat(monitorRun.taken()).doesNotHaveDuplicates();
    }
}
