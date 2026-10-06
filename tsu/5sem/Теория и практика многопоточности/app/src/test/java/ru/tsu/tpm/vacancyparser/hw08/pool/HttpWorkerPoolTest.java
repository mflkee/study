package ru.tsu.tpm.vacancyparser.hw08.pool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw08.pool.config.RunConfig;
import ru.tsu.tpm.vacancyparser.hw08.pool.config.WorkMode;

/**
 * Ручная настройка пула: параметры, имена потоков, обработчик отказа, достижимость максимума, остановка.
 */
class HttpWorkerPoolTest {

    private static RunConfig config(int core, int max, int queue, long shutdownMillis) {
        return new RunConfig(
                WorkMode.LOCAL, List.of("http://local"), core, max, queue, 1_000L, 500L, 1_000L, shutdownMillis);
    }

    @Test
    @DisplayName("Фактические параметры пула совпадают с переданными")
    void parametersMatchConfiguration() {
        HttpWorkerPool pool = HttpWorkerPool.create(config(3, 5, 7, 1_000L));
        try {
            ThreadPoolExecutor executor = pool.executor();
            assertThat(executor.getCorePoolSize()).isEqualTo(3);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(5);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(7);
            assertThat(executor.getKeepAliveTime(TimeUnit.MILLISECONDS)).isEqualTo(1_000L);
            assertThat(pool.poolSize()).as("потоки ядра созданы заранее").isEqualTo(3);
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Имена потоков доменные и уникальные, без технических шаблонов")
    void threadNamesAreDomainSpecific() throws InterruptedException {
        HttpWorkerPool pool = HttpWorkerPool.create(config(4, 4, 4, 1_000L));
        try {
            CountDownLatch ready = new CountDownLatch(4);
            CountDownLatch release = new CountDownLatch(1);
            ConcurrentLinkedQueue<String> names = new ConcurrentLinkedQueue<>();
            for (int i = 0; i < 4; i++) {
                pool.executor().execute(() -> {
                    names.add(Thread.currentThread().getName());
                    ready.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            assertThat(names).hasSize(4);
            assertThat(names).allSatisfy(name -> {
                assertThat(name).startsWith(NamedThreadFactory.PREFIX).isNotBlank();
                assertThat(name).doesNotContain("pool-").doesNotContain("Thread-");
            });
            assertThat(Set.copyOf(names)).as("имена уникальны").hasSize(4);
        } finally {
            pool.close();
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("При переполнении очередь задача выполняется вызывающим потоком, счётчик растёт")
    void rejectionHandlerRunsTaskOnCaller() {
        HttpWorkerPool pool = HttpWorkerPool.create(config(1, 1, 1, 2_000L));
        AtomicInteger completed = new AtomicInteger();
        for (int i = 0; i < 12; i++) {
            pool.executor().execute(() -> {
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                completed.incrementAndGet();
            });
        }
        assertThat(pool.rejectionCount())
                .as("обработчик отказа обязан сработать")
                .isPositive();
        assertThat(pool.rejectionHandlerName()).isEqualTo(CountingCallerRunsPolicy.NAME);

        HttpWorkerPool.ShutdownOutcome outcome = pool.shutdown();
        // Даже отказы не теряют задачи: после остановки все они выполнены.
        assertThat(outcome.terminated()).isTrue();
        assertThat(completed).hasValue(12);
    }

    @Test
    @Timeout(60)
    @DisplayName("Ограниченная очередь делает максимум достижимым, неограниченная — нет")
    void boundedQueueLetsPoolGrow() throws InterruptedException {
        HttpWorkerPool bounded = HttpWorkerPool.create(config(2, 6, 2, 1_000L));
        try {
            for (int i = 0; i < 20; i++) {
                bounded.executor().execute(HttpWorkerPoolTest::sleepBriefly);
            }
            assertThat(bounded.executor().getLargestPoolSize())
                    .as("с ограниченной очередью пул расширяется выше ядра")
                    .isGreaterThan(2);
        } finally {
            bounded.close();
        }

        HttpWorkerPool unbounded = HttpWorkerPool.createUnbounded(config(2, 6, 2, 1_000L));
        try {
            for (int i = 0; i < 20; i++) {
                unbounded.executor().execute(HttpWorkerPoolTest::sleepBriefly);
            }
            assertThat(unbounded.executor().getLargestPoolSize())
                    .as("с неограниченной очередью пул остаётся на ядре")
                    .isLessThanOrEqualTo(2);
        } finally {
            unbounded.close();
        }
    }

    @Test
    @DisplayName("Успешная остановка: ожидание возвращает истину, предупреждений нет")
    void shutdownTerminatesWhenNoPendingTasks() {
        HttpWorkerPool pool = HttpWorkerPool.create(config(2, 4, 4, 2_000L));
        HttpWorkerPool.ShutdownOutcome outcome = pool.shutdown();

        assertThat(outcome.terminated()).isTrue();
        assertThat(outcome.pendingTasks()).isZero();
        assertThat(outcome.message()).contains("штатно");
    }

    @Test
    @Timeout(30)
    @DisplayName("Незавершённые задачи: предупреждение с числом и принудительное завершение")
    void shutdownWarnsAboutPendingTasks() throws InterruptedException {
        HttpWorkerPool pool = HttpWorkerPool.create(config(1, 1, 1, 200L));
        CountDownLatch started = new CountDownLatch(1);
        pool.executor().execute(() -> {
            started.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        started.await(5, TimeUnit.SECONDS);

        HttpWorkerPool.ShutdownOutcome outcome = pool.shutdown();

        assertThat(outcome.terminated()).isFalse();
        assertThat(outcome.pendingTasks()).isPositive();
        assertThat(outcome.message()).contains("принудительное завершение");
    }

    @Test
    @DisplayName("Настройки пула печатаются с обработчиком отказа и числом потоков")
    void settingsArePrinted() {
        RunConfig config = config(2, 4, 5, 1_000L);
        HttpWorkerPool pool = HttpWorkerPool.create(config);
        try {
            String text = String.join("\n", pool.settingsLines(config));
            assertThat(text)
                    .contains("минимальный размер пула (ядро): 2")
                    .contains("максимальный размер пула: 4")
                    .contains("ёмкость очереди задач: 5 из 5")
                    .contains(CountingCallerRunsPolicy.NAME)
                    .contains("потоков после создания пула: 2");
        } finally {
            pool.close();
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
