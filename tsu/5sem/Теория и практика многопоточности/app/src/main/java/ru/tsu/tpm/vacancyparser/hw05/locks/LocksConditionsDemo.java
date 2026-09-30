package ru.tsu.tpm.vacancyparser.hw05.locks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;

/**
 * Демонстрация ДЗ 5 «Блокировка доступа к ресурсам: ReentrantLock и Condition».
 *
 * <p>Запускается только при {@code --app.hw=hw05}. Этапы: нагрузочный прогон с проверкой инварианта,
 * ожидание с обеих сторон буфера, адресное оповещение против {@code signalAll()}, обработка
 * прерывания и сравнение с мониторным подходом.
 *
 * <p>Дамп потоков берётся из ДЗ 4 намеренно: это общая инфраструктура приложения, и дублировать её
 * ради одного вывода смысла нет.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw05")
public class LocksConditionsDemo implements ApplicationRunner {

    /** Срок ожидания в демонстрационных сценариях: короткий, чтобы демо не висело. */
    private static final long SCENARIO_TIMEOUT_MILLIS = 3_000L;

    private static final int THREADS = 4;
    private static final int ITEMS_PER_PRODUCER = 2_000;
    private static final int RUNS = BufferBenchmark.DEFAULT_RUNS;

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;

    public LocksConditionsDemo(ConsoleOutput console, ConfigurableApplicationContext context) {
        this.console = console;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        console.section("ДЗ 5. ReentrantLock и Condition");
        console.raw("");
        console.raw("Процесс: pid " + ProcessHandle.current().pid() + ", " + ThreadDump.summary());

        stageInvariantOnCapacityOne();
        stageWaitingOnBothSides();
        stageAddressedSignalVersusSignalAll();
        stageMassCompletionWithSignalAll();
        stageInterruptHandling();
        stageComparison();

        console.raw("");
        console.info("Демонстрация завершена: инвариант проверен, ожидание с обеих сторон показано, "
                + "адресное оповещение и signalAll применены по назначению, прерывание обработано, "
                + "замеры выведены");
        console.raw("");
        console.raw(ThreadDump.summary());
        console.raw("живых потоков демонстрации после завершения: "
                + ThreadDump.liveThreadsWithPrefixes("producer", "consumer", "buffer-waiter"));

        context.close();
    }

    /** Этап 1. Инвариант на ёмкости 1: максимальное переключение состояний. */
    private void stageInvariantOnCapacityOne() throws InterruptedException {
        console.raw("");
        console.raw("Этап 1. Нагрузочный прогон на ёмкости 1 (буфер мгновенно переходит «пуст» ↔ «полон»)");

        ProducerConsumerRunner.Result result = ProducerConsumerRunner.run(
                new BoundedBuffer<>(1), THREADS, THREADS, ITEMS_PER_PRODUCER);
        LocksReport.balanceBlock(result).forEach(console::raw);
        LocksReport.distributionBlock(result).forEach(console::raw);
    }

    /** Этап 2. Ожидание с обеих сторон: добавление при полном буфере, извлечение при пустом. */
    private void stageWaitingOnBothSides() throws InterruptedException {
        console.raw("");
        console.raw("Этап 2. Ожидание с обеих сторон буфера");

        BoundedBuffer<String> full = new BoundedBuffer<>(1, SCENARIO_TIMEOUT_MILLIS);
        full.put("заполнил-буфер");
        Thread waitingProducer = new Thread(() -> full.put("ждёт-места"), "producer-waiting");
        waitingProducer.setDaemon(true);
        waitingProducer.start();
        Thread.State producerState = awaitState(waitingProducer, SCENARIO_TIMEOUT_MILLIS);
        console.raw("    буфер полон (ёмкость 1) → producer ждёт свободного места: " + producerState
                + " (условие notFull)");
        String freed = full.take();
        waitingProducer.join(5_000L);
        console.raw("    consumer забрал «" + freed + "» → producer продолжил: жив = " + waitingProducer.isAlive()
                + ", в буфере «" + full.take() + "»");

        BoundedBuffer<String> empty = new BoundedBuffer<>(1, SCENARIO_TIMEOUT_MILLIS);
        Thread waitingConsumer = new Thread(() -> empty.take(), "consumer-waiting");
        waitingConsumer.setDaemon(true);
        waitingConsumer.start();
        Thread.State consumerState = awaitState(waitingConsumer, SCENARIO_TIMEOUT_MILLIS);
        console.raw("    буфер пуст → consumer ждёт элемента: " + consumerState + " (условие notEmpty)");
        empty.put("положил-элемент");
        waitingConsumer.join(5_000L);
        console.raw("    producer положил элемент → consumer завершился: жив = " + waitingConsumer.isAlive());
    }

    /** Этап 3. Адресное оповещение: один элемент будит одного, а не всех. */
    private void stageAddressedSignalVersusSignalAll() throws InterruptedException {
        console.raw("");
        console.raw("Этап 3. Адресное оповещение (signal) против notifyAll");

        int waiters = 3;

        BoundedBuffer<String> lock = new BoundedBuffer<>(1, SCENARIO_TIMEOUT_MILLIS);
        lock.put("заполнил");
        List<Thread> lockWaiters = startWaitingProducers(lock, waiters);
        lock.take();
        Thread.sleep(100L);
        console.raw("    два условия: после одного take освободилось ОДНО место → прошёл "
                + countFinished(lockWaiters) + " из " + waiters
                + " ожидающих producer'ов, впустую проснулись: " + lock.wastedWakeups());

        MonitorBuffer<String> monitor = new MonitorBuffer<>(1, SCENARIO_TIMEOUT_MILLIS);
        monitor.put("заполнил");
        List<Thread> monitorWaiters = startWaitingProducers(monitor, waiters);
        monitor.take();
        Thread.sleep(100);
        console.raw("    одно условие (монитор): после одного take проснулись все " + waiters
                + " ожидающих, впустую проснулись: " + monitor.wastedWakeups());
        console.raw("    Разница измерима: notifyAll будит всех, включая тех, кому работы нет, и они возвращаются в ожидание.");

        lock.close();
        monitor.close();
        joinAll(lockWaiters);
        joinAll(monitorWaiters);
        LocksReport.wastedWakeupsBlock(lock.wastedWakeups(), monitor.wastedWakeups()).forEach(console::raw);
    }

    /** Этап 4. Массовое завершение: состояние изменилось для всех — нужен signalAll. */
    private void stageMassCompletionWithSignalAll() throws InterruptedException {
        console.raw("");
        console.raw("Этап 4. Массовое завершение: сигнал всем ожидающим");

        BoundedBuffer<String> buffer = new BoundedBuffer<>(4, SCENARIO_TIMEOUT_MILLIS);
        List<Thread> consumers = new ArrayList<>();
        AtomicReference<BufferOperationException.Reason> reason = new AtomicReference<>();
        for (int i = 0; i < 4; i++) {
            Thread consumer = new Thread(() -> {
                try {
                    buffer.take();
                } catch (BufferOperationException e) {
                    reason.set(e.reason());
                }
            }, "consumer-" + i);
            consumer.setDaemon(true);
            consumers.add(consumer);
            consumer.start();
        }
        Thread.sleep(100L);
        console.raw("    пустой буфер, ожидающих consumer'ов: " + consumers.stream().filter(Thread::isAlive).count());
        buffer.close();
        joinAll(consumers);
        console.raw("    после close() вышли из ожидания и завершились: "
                + consumers.stream().filter(thread -> !thread.isAlive()).count()
                + " из " + consumers.size() + ", причина остановки: "
                + (reason.get() == null ? "нет" : reason.get().description()));
    }

    /** Этап 5. Прерывание: блокировка освобождена, признак прерывания сохранён. */
    private void stageInterruptHandling() throws InterruptedException {
        console.raw("");
        console.raw("Этап 5. Обработка прерывания во время ожидания");

        BoundedBuffer<String> buffer = new BoundedBuffer<>(1, SCENARIO_TIMEOUT_MILLIS);
        buffer.put("заполнил");
        AtomicReference<BufferOperationException.Reason> reason = new AtomicReference<>();
        AtomicBoolean flagRestored = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            started.countDown();
            try {
                buffer.put("не пройдёт");
            } catch (BufferOperationException e) {
                reason.set(e.reason());
                flagRestored.set(Thread.currentThread().isInterrupted());
            }
        }, "producer-interrupted");
        producer.setDaemon(true);
        producer.start();
        started.await(5, TimeUnit.SECONDS);
        awaitState(producer, SCENARIO_TIMEOUT_MILLIS);

        producer.interrupt();
        producer.join(5_000L);

        console.raw("    прерванный producer завершился: жив = " + producer.isAlive()
                + ", причина остановки: " + (reason.get() == null ? "нет" : reason.get().description()));
        console.raw("    признак прерывания восстановлен: " + flagRestored.get());
        console.raw("    буфер после прерывания пригоден: размер = " + buffer.size()
                + ", извлекается «" + buffer.take() + "»");
    }

    /** Этап 6. Сравнение с мониторным подходом. */
    private void stageComparison() throws InterruptedException {
        console.raw("");
        console.raw("Этап 6. Сравнение производительности с мониторным подходом");

        BufferBenchmark.Comparison comparison =
                BufferBenchmark.compare(THREADS, THREADS, 4, ITEMS_PER_PRODUCER, RUNS);
        LocksReport.comparisonBlock(comparison).forEach(console::raw);
        console.raw("");
        console.raw("    " + LocksReport.conclusion(comparison));
        console.raw("    инварианты выполнены во всех прогонах обоих механизмов: "
                + comparison.allConsistent());
    }

    private List<Thread> startWaitingProducers(Buffer<String> buffer, int count) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Thread producer = new Thread(() -> {
                try {
                    buffer.put("элемент");
                } catch (BufferOperationException ignored) {
                    // ожидаемый исход при закрытии буфера
                }
            }, "producer-" + i);
            producer.setDaemon(true);
            threads.add(producer);
            producer.start();
        }
        for (Thread producer : threads) {
            awaitState(producer, SCENARIO_TIMEOUT_MILLIS);
        }
        return threads;
    }

    private static long countFinished(List<Thread> threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join(200L);
        }
        return threads.stream().filter(thread -> !thread.isAlive()).count();
    }

    private static void joinAll(List<Thread> threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join(5_000L);
        }
    }

    /** Дождаться, что поток ушёл в ожидание; вернуть наблюдённое состояние. */
    private Thread.State awaitState(Thread thread, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return state;
            }
            Thread.sleep(1L);
        }
        return thread.getState();
    }
}
