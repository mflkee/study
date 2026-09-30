package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

/**
 * Демонстрация ДЗ 4 «Синхронизация потоков».
 *
 * <p>Запускается только при {@code --app.hw=hw04} и идёт по этапам задания: сбор данных и защита от
 * гонок, ожидание с уведомлением, предотвращение взаимных блокировок, замеры производительности.
 *
 * <p>Вся демонстрация выполняется в ограниченном по времени окне: если защита где-то не сработает и
 * поток зависнет, процесс не станет ждать его вечно — печатается дамп потоков, и приложение
 * завершается с ненулевым кодом. Это и есть страховка, которой требует задание.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw04")
public class ThreadSynchronizationDemo implements ApplicationRunner {

    /** Предел времени на всю демонстрацию: демонстрация не имеет права висеть дольше. */
    static final long TIME_LIMIT_MILLIS = 120_000L;

    /** Код выхода, если демонстрация не уложилась в лимит времени. */
    static final int TIME_LIMIT_EXIT_CODE = 3;

    private static final int THREADS = 8;
    private static final int ITEMS_PER_THREAD = 20_000;
    private static final int TRANSFERS_PER_THREAD = 5_000;
    private static final int OPERATIONS_PER_THREAD = SyncBenchmark.DEFAULT_OPERATIONS;
    private static final int RUNS = SyncBenchmark.DEFAULT_RUNS;

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;

    public ThreadSynchronizationDemo(ConsoleOutput console, ConfigurableApplicationContext context) {
        this.console = console;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        console.section("ДЗ 4. Синхронизация потоков");
        console.raw("");
        console.raw("Предел времени на демонстрацию: " + TIME_LIMIT_MILLIS + " мс");
        console.raw("Процесс: pid " + ProcessHandle.current().pid() + ", " + ThreadDump.summary());

        TimeBoxedExecution.Outcome outcome = TimeBoxedExecution.run(TIME_LIMIT_MILLIS, this::runStages);

        console.raw("");
        if (outcome.completed()) {
            console.info("Демонстрация завершена: гонки защищены, ожидание и уведомление показаны, "
                    + "взаимные блокировки предотвращены, замеры выведены");
            console.raw("");
            console.raw(ThreadDump.summary());
            console.raw("живых потоков демонстрации после завершения: "
                    + ThreadDump.liveThreadsWithPrefixes(
                            "load-", "taker-", "readiness-waiter", "account-holder", "time-boxed-task"));
            context.close();
            return;
        }

        console.warn("демонстрация не уложилась в " + TIME_LIMIT_MILLIS + " мс — снимаю дамп потоков");
        console.raw(ThreadDump.capture());
        context.close();
        System.exit(TIME_LIMIT_EXIT_CODE);
    }

    private void runStages() {
        try {
            stageCollectionAndRaces();
            stageWaitingAndNotification();
            stageDeadlockPrevention();
            stageMeasurements();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("демонстрация прервана", e);
        }
    }

    /** Этап 1. Сбор данных и защита от гонок. */
    private void stageCollectionAndRaces() throws InterruptedException {
        console.raw("");
        console.raw("Этап 1. Сбор данных и защита от гонок (инвариант счётчика)");

        SyncBenchmark.MixedLoadOutcome mixed = SyncBenchmark.runMixedLoad(THREADS, ITEMS_PER_THREAD, 0);
        console.raw(String.format(
                "    %d потоков × %d элементов: собрано %d, счётчик %d, уникальных ключей согласованы: %s",
                mixed.threads(),
                mixed.itemsPerThread(),
                mixed.collectedCount(),
                mixed.counterActual(),
                mixed.invariantViolations().isEmpty() ? "да" : "НЕТ"));

        DataCollector collector = new DataCollector();
        AtomicInteger accepted = new AtomicInteger();
        SyncBenchmark.startThreads(12, index -> {
            if (collector.collectItem(new Item("vacancy-42", "снимок " + index))) {
                accepted.incrementAndGet();
            }
        });
        console.raw("    гонка за один ключ из 12 потоков: принят " + accepted.get()
                + " элемент, счётчик " + collector.processedCount());

        console.raw("");
        console.raw("    Незащищённый вариант — тот же счётчик без синхронизации:");
        SyncBenchmark.CounterComparison comparison = SyncBenchmark.compareCounters(THREADS, 20_000, 1);
        for (SyncBenchmark.CounterRun run : comparison.runs()) {
            if (run.exact()) {
                continue;
            }
            console.raw(String.format(
                    "        %s: счётчик %d из %d — потеряно обновлений: %d",
                    run.protection(), run.value(), run.expected(), run.lost()));
        }
    }

    /** Этап 2. Ожидание и уведомление. */
    private void stageWaitingAndNotification() throws InterruptedException {
        console.raw("");
        console.raw("Этап 2. Ожидание (wait) и уведомление (notify / notifyAll)");

        DataCollector readiness = new DataCollector();
        CountDownLatch parked = new CountDownLatch(1);
        Thread waiter = new Thread(() -> {
            try {
                parked.countDown();
                readiness.awaitReady();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "readiness-waiter");
        waiter.setDaemon(true);
        waiter.start();
        parked.await(5, TimeUnit.SECONDS);
        Thread.sleep(50L);
        console.raw("    ожидающий поток до уведомления: " + waiter.getState());
        readiness.markReady();
        waiter.join(5_000L);
        console.raw("    после notifyAll(): " + waiter.getState());

        DataCollector queue = new DataCollector();
        AtomicInteger taken = new AtomicInteger();
        List<Thread> takers = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Thread taker = new Thread(() -> {
                try {
                    if (queue.takeItem(300L) != null) {
                        taken.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "taker-" + i);
            taker.setDaemon(true);
            takers.add(taker);
            taker.start();
        }
        Thread.sleep(50L);
        queue.collectItem(new Item("vacancy-1", "текст"));
        for (Thread taker : takers) {
            taker.join(5_000L);
        }
        console.raw("    notify() при двух ожидающих и одном элементе: забрал элемент "
                + taken.get() + " поток, впустую проснулись " + queue.wastedWakeups());
    }

    /** Этап 3. Предотвращение взаимных блокировок. */
    private void stageDeadlockPrevention() throws InterruptedException {
        console.raw("");
        console.raw("Этап 3. Предотвращение взаимных блокировок");

        Account first = new Account("acc-1", 1_000_000);
        Account second = new Account("acc-2", 1_000_000);
        long before = first.balance() + second.balance();
        TransferService service = new TransferService(2_000L);

        SyncBenchmark.startThreads(THREADS, index -> {
            for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
                try {
                    if (index % 2 == 0) {
                        service.transfer(first, second, 1L);
                    } else {
                        service.transfer(second, first, 1L);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });

        console.raw(String.format(
                "    встречные переводы, %d потоков × %d переводов: сумма балансов %d -> %d (%s)",
                THREADS,
                TRANSFERS_PER_THREAD,
                before,
                first.balance() + second.balance(),
                before == first.balance() + second.balance() ? "сохранена" : "НАРУШЕНА"));
        List<String> log = service.acquisitionLog();
        console.raw("    записей в журнале порядка захвата: " + log.size()
                + ", различных порядков: " + log.stream().distinct().count()
                + " (порядок всегда по возрастанию идентификатора)");

        Account busy = new Account("acc-3", 1_000);
        Account other = new Account("acc-4", 1_000);
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            try {
                if (busy.tryLock(5_000L)) {
                    occupied.countDown();
                    release.await(5_000L, TimeUnit.MILLISECONDS);
                    busy.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "account-holder");
        holder.setDaemon(true);
        holder.start();
        occupied.await(5, TimeUnit.SECONDS);

        TransferService impatient = new TransferService(150L);
        long startedAt = System.nanoTime();
        TransferService.TransferResult result = impatient.transfer(busy, other, 100L);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        release.countDown();
        holder.join(5_000L);

        console.raw("    попытка перевода при занятом счёте: успех = " + result.success()
                + ", причина = " + result.failure()
                + ", ожидание " + elapsedMillis + " мс (тайм-аут 150 мс)");
        console.raw("    взаимная блокировка: " + (ThreadDump.hasDeadlock() ? "ОБНАРУЖЕНА" : "не обнаружена"));
    }

    /** Этап 4. Замеры производительности. */
    private void stageMeasurements() throws InterruptedException {
        console.raw("");
        console.raw("Этап 4. Тестирование и производительность");

        SyncBenchmark.MixedLoadOutcome mixed =
                SyncBenchmark.runMixedLoad(THREADS, ITEMS_PER_THREAD, TRANSFERS_PER_THREAD);
        SynchronizationReport.mixedLoadBlock(mixed).forEach(console::raw);

        SyncBenchmark.CounterComparison counters =
                SyncBenchmark.compareCounters(THREADS, OPERATIONS_PER_THREAD, RUNS);
        SynchronizationReport.counterComparisonBlock(counters).forEach(console::raw);
        console.raw("    " + SynchronizationReport.synchronizationCost(counters));

        SyncBenchmark.TransferComparison transfers =
                SyncBenchmark.compareTransfers(THREADS, 5_000, RUNS);
        SynchronizationReport.transferComparisonBlock(transfers).forEach(console::raw);
    }
}
