package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Основы буфера: ёмкость, порядок извлечения и жёсткая граница по размеру.
 */
class BoundedBufferBasicsTest {

    private static final long TIMEOUT_MILLIS = 2_000L;

    @Test
    @DisplayName("Буфер создаётся с заданной ёмкостью, пуст и сообщает размер")
    void newBufferIsEmptyWithDeclaredCapacity() {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(3);

        assertThat(buffer.capacity()).isEqualTo(3);
        assertThat(buffer.size()).isZero();
        assertThat(buffer.isEmpty()).isTrue();
        assertThat(buffer.isFull()).isFalse();
        assertThat(buffer.isClosed()).isFalse();
    }

    @Test
    @DisplayName("Заведомо неверная ёмкость отвергается, а не даёт нерабочий объект")
    void invalidCapacityIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BoundedBuffer<String>(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new BoundedBuffer<String>(-5));
        assertThatIllegalArgumentException().isThrownBy(() -> new MonitorBuffer<String>(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new MonitorBuffer<String>(-5));

        assertThat(new BoundedBuffer<String>(1).capacity())
                .as("единица — минимальная допустимая ёмкость")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Один элемент: put и take проходят, размер меняется предсказуемо")
    void singlePutAndTake() {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(2, TIMEOUT_MILLIS);

        buffer.put("вакансия-1");
        assertThat(buffer.size()).isEqualTo(1);
        assertThat(buffer.isEmpty()).isFalse();

        assertThat(buffer.take()).isEqualTo("вакансия-1");
        assertThat(buffer.size()).isZero();
        assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Порядок извлечения FIFO: три добавления в буфер ёмкости 2 дают порядок добавления")
    @Timeout(20)
    void extractionFollowsInsertionOrderAndCapacityIsHardLimit() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(2, TIMEOUT_MILLIS);

        buffer.put("первый");
        buffer.put("второй");
        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.isFull()).isTrue();

        // Третье добавление не увеличивает размер сверх ёмкости: места нет, поэтому вызов уходит
        // в ожидание, а не расширяет буфер.
        Thread third = new Thread(() -> buffer.put("третий"), "producer-third");
        third.setDaemon(true);
        third.start();
        Thread.sleep(100L);
        assertThat(buffer.size())
                .as("размер не превышает ёмкость, пока третий элемент ждёт места")
                .isEqualTo(2);
        assertThat(third.isAlive()).as("третий добавление ждёт свободного места").isTrue();

        List<String> extracted = new ArrayList<>();
        extracted.add(buffer.take());
        third.join(5_000L);
        assertThat(third.isAlive())
                .as("как только место освободилось, ожидавшее добавление прошло")
                .isFalse();
        extracted.add(buffer.take());
        extracted.add(buffer.take());

        assertThat(extracted).containsExactly("первый", "второй", "третий");
        assertThat(buffer.size()).isZero();
    }

    @Test
    @DisplayName("Размер никогда не превышает ёмкость при параллельных добавлениях")
    @Timeout(30)
    void sizeNeverExceedsCapacity() throws InterruptedException {
        int capacity = 2;
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(capacity, TIMEOUT_MILLIS);
        List<Thread> producers = new ArrayList<>();

        for (int p = 0; p < 8; p++) {
            int base = p * 10;
            Thread producer = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    buffer.offer(base + i, TIMEOUT_MILLIS);
                }
            }, "producer-" + p);
            producer.setDaemon(true);
            producers.add(producer);
            producer.start();
        }

        int consumed = 0;
        while (consumed < 80) {
            Integer item = buffer.poll(TIMEOUT_MILLIS);
            if (item == null) {
                break;
            }
            // Размер читается под нагрузкой: если бы граница ёмкости нарушалась, это было бы
            // видно именно здесь. Сравнивать isFull() с size() двумя отдельными вызовами нельзя —
            // между вызовами производители успевают добавить элементы, и это было бы артефактом
            // измерения, а не дефектом буфера.
            assertThat(buffer.size())
                    .as("размер не превышает ёмкость даже в момент конкуренции")
                    .isLessThanOrEqualTo(capacity);
            consumed++;
        }

        for (Thread producer : producers) {
            producer.join(10_000L);
        }

        assertThat(consumed).as("все добавленные элементы дошли до потребителя").isEqualTo(80);
        assertThat(buffer.size()).isLessThanOrEqualTo(capacity);
    }

    @Test
    @DisplayName("Элемент null отвергается: null означает «ничего не извлечено»")
    void nullItemIsRejected() {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, TIMEOUT_MILLIS);

        assertThatNullPointerException().isThrownBy(() -> buffer.put(null));
        assertThatNullPointerException().isThrownBy(() -> buffer.offer(null, TIMEOUT_MILLIS));
        assertThat(buffer.size()).isZero();
    }
}
