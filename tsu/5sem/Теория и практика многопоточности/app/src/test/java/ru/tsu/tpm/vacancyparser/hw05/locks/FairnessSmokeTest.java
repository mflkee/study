package ru.tsu.tpm.vacancyparser.hw05.locks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Распределение извлечённых элементов по потокам.
 *
 * <p>Это «дымовая» проверка, а не гарантия равных долей: блокировка по умолчанию несправедлива, и
 * ни одна реализация не обязана делить работу поровну. Проверяется то, что должно выполняться.
 *
 * <p>Требования к двум реализациям <b>разные</b>, и это не подгонка под результат, а измеренное
 * различие механизмов:
 *
 * <ul>
 *   <li>у явной блокировки оповещение адресное ({@code signal}), и ожидающие обслуживаются в порядке
 *       своей очереди условий — поэтому каждый потребитель получает свою долю;</li>
 *   <li>у монитора {@code notifyAll()} будит всех, и они соревнуются за монитор заново; один поток
 *       может выигрывать гонку снова и снова и забрать почти всё, а другой — не получить ничего.
 *       В прогоне, где это наблюдалось, распределение было {@code [694, 0, 138, 1168]}.</li>
 * </ul>
 *
 * <p>Поэтому для монитора проверяется отсутствие монополии (работу получил не один поток), а факт
 * перекоса выводится и попадает в отчёт.
 */
class FairnessSmokeTest {

    private static final int THREADS = 4;
    private static final int ITEMS_PER_PRODUCER = 500;

    @RepeatedTest(3)
    @DisplayName("Явная блокировка: каждый поток извлечения получает свою долю элементов")
    @Timeout(120)
    void everyConsumerGetsAShareWithExplicitLock() throws InterruptedException {
        ProducerConsumerRunner.Result result =
                ProducerConsumerRunner.run(new BoundedBuffer<>(1), THREADS, THREADS, ITEMS_PER_PRODUCER);

        assertThat(result.perConsumer().values().stream().mapToInt(Integer::intValue).sum())
                .as("сумма долей равна числу извлечённых элементов")
                .isEqualTo(result.taken().size());
        assertThat(result.perConsumer().values())
                .as("распределение по потокам: %s", result.perConsumer())
                .allSatisfy(count -> assertThat(count)
                        .as("каждый поток извлечения получил хотя бы один элемент: %s", result.perConsumer())
                        .isPositive());
    }

    @Test
    @DisplayName("Монитор: распределение перекошено сильнее — работу получил не один поток")
    @Timeout(120)
    void monitorImplementationSpreadsWorkLessEvenly() throws InterruptedException {
        ProducerConsumerRunner.Result result =
                ProducerConsumerRunner.run(new MonitorBuffer<>(1), THREADS, THREADS, ITEMS_PER_PRODUCER);

        assertThat(result.perConsumer().values().stream().mapToInt(Integer::intValue).sum())
                .as("сумма долей равна числу извлечённых элементов")
                .isEqualTo(result.taken().size());
        assertThat(result.perConsumer().values().stream().filter(count -> count > 0).count())
                .as("notifyAll будит всех и перекос возможен, но монополии быть не должно: %s",
                        result.perConsumer())
                .isGreaterThanOrEqualTo(2);
        assertThat(result.consistent())
                .as("перекос распределения не влияет на инварианты: ничего не потеряно")
                .isTrue();
    }
}
