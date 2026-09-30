package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Гонка за один ключ: несколько потоков одновременно предъявляют элементы с одинаковым ключом.
 *
 * <p>Опасность, которую проверяет тест, — двойная обработка. Если бы проверка «уже обрабатывали?» и
 * пометка ключа выполнялись двумя разными критическими секциями, два потока могли бы одновременно
 * увидеть ключ необработанным и оба взяться за работу. Здесь проверка и пометка происходят в одной
 * секции, поэтому победитель обязан быть ровно один.
 */
class DuplicateRaceTest {

    private static final int THREADS = 12;

    @RepeatedTest(10)
    @DisplayName("Один ключ, много потоков: обработку получает ровно один экземпляр")
    void onlyOneThreadWinsTheKey() throws InterruptedException {
        DataCollector collector = new DataCollector();
        AtomicInteger accepted = new AtomicInteger();
        List<String> rejections = new CopyOnWriteArrayList<>();

        ConcurrentLoad.runIndexed(THREADS, index -> {
            Item item = new Item("vacancy-42", "снимок страницы из потока " + index);
            if (collector.collectItem(item)) {
                accepted.incrementAndGet();
            } else {
                rejections.add("поток " + index);
            }
        });

        assertThat(accepted.get())
                .as("ровно один поток должен получить право обработки")
                .isEqualTo(1);
        assertThat(rejections)
                .as("все остальные попытки отвергнуты")
                .hasSize(THREADS - 1);
        assertThat(collector.collectedCount()).isEqualTo(1);
        assertThat(collector.processedCount())
                .as("счётчик обработанных равен единице: дубликат не увеличил его")
                .isEqualTo(1);
        assertThat(collector.processedKeyCount()).isEqualTo(1);
        assertThat(collector.invariantViolations()).isEmpty();
    }

    @Test
    @DisplayName("Схема «проверить, затем обработать» двумя секциями тоже не даёт двойной обработки")
    void checkThenActStillYieldsSingleWinner() throws InterruptedException {
        DataCollector collector = new DataCollector();
        AtomicInteger processed = new AtomicInteger();

        ConcurrentLoad.runIndexed(THREADS, index -> {
            String key = "vacancy-7";
            // Проверка отдельной секцией — так делать не нужно, и тест показывает почему:
            // ответ «ещё не обработан» не гарантирует, что обработку получишь именно ты.
            if (!collector.isAlreadyProcessed(key)) {
                if (collector.collectItem(new Item(key, "текст " + index))) {
                    processed.incrementAndGet();
                }
            }
        });

        assertThat(processed.get())
                .as("обработать элемент можно только через атомарный collectItem, "
                        + "поэтому победитель по-прежнему один")
                .isEqualTo(1);
        assertThat(collector.processedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Разные ключи обрабатываются параллельно, ни один не теряется")
    void differentKeysAreAllAccepted() throws InterruptedException {
        DataCollector collector = new DataCollector();

        ConcurrentLoad.runIndexed(THREADS, index -> collector.collectItem(
                new Item("vacancy-" + index, "текст " + index)));

        assertThat(collector.collectedCount()).isEqualTo(THREADS);
        assertThat(collector.processedCount()).isEqualTo(THREADS);
        assertThat(collector.processedKeyCount()).isEqualTo(THREADS);
        assertThat(collector.invariantViolations()).isEmpty();
    }
}
