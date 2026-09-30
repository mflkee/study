package ru.tsu.tpm.vacancyparser.hw01.basics;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

/**
 * Сервис запуска демонстрационных потоков ДЗ 1.
 *
 * <p>Создаёт потоки обоих типов — наследованием от {@link Thread} и через {@link Runnable},
 * — присваивает им доменные имена и выдаёт порядковые номера из общего
 * {@link AtomicInteger}-счётчика.
 */
@Service
public class ThreadBasicsService {

    /** Доменное имя потока-счётчика. */
    public static final String COUNTER_WORKER = "CounterWorker";
    /** Доменное имя потока-журнализатора. */
    public static final String LOGGER_THREAD = "LoggerThread";

    /** Сколько ждать, пока оба потока дошли до барьера. */
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(10);

    private final ConsoleOutput console;
    private final AtomicInteger ordinals = new AtomicInteger();

    public ThreadBasicsService(ConsoleOutput console) {
        this.console = console;
    }

    /**
     * Создать и запустить оба демонстрационных потока.
     *
     * <p>Метод не возвращает управление, пока оба потока не дошли до барьера
     * {@link WorkerGroup#gate()}. Без этого ожидания строки «начал работу» могли бы
     * напечататься уже после заголовка списка активных потоков, и вывод в отчёте
     * получался бы невоспроизводимым по порядку строк.
     */
    public WorkerGroup startWorkers() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch gate = new CountDownLatch(1);

        CountingTask counting = new CountingTask(
                COUNTER_WORKER, ordinals.incrementAndGet(), console, ready, gate);
        LoggingTask logging = new LoggingTask(
                ordinals.incrementAndGet(), console, ready, gate);
        Thread loggerThread = new Thread(logging, LOGGER_THREAD);

        console.raw("Созданы потоки:");
        console.item(COUNTER_WORKER + " — задача №" + counting.ordinal() + " (наследование от Thread)");
        console.item(LOGGER_THREAD + " — задача №" + logging.ordinal() + " (реализация Runnable)");
        console.raw("");

        counting.start();
        loggerThread.start();

        if (!ready.await(READY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Демонстрационные потоки не начали работу за "
                    + READY_TIMEOUT.toMillis() + " мс");
        }

        return new WorkerGroup(List.of(counting, loggerThread), counting, logging, gate);
    }

    /** Вывести потоки, активные в данный момент, с их состояниями. */
    public void printActiveThreads() {
        List<Thread> active = Thread.getAllStackTraces().keySet().stream()
                .sorted(Comparator.comparing(Thread::getName))
                .toList();

        console.raw("Всего активных потоков: " + active.size());
        active.forEach(thread -> console.raw("    " + thread.getName() + " — " + thread.getState()));
        console.raw("");
    }

    /** Вывести состояния только демонстрационных потоков — полезно после {@code join()}. */
    public void printWorkerStates(WorkerGroup group) {
        console.raw("Состояние демонстрационных потоков после join():");
        group.threads().forEach(thread -> console.raw("    " + thread.getName() + " — " + thread.getState()));
        console.raw("");
    }

    /**
     * Запущенные потоки и общий барьер, удерживающий их до открытия.
     *
     * @param threads запущенные потоки в порядке создания
     * @param gate    барьер, общий для обеих задач
     */
    public record WorkerGroup(
            List<Thread> threads,
            CountingTask counting,
            LoggingTask logging,
            CountDownLatch gate) {

        /** Отпустить потоки — с этого момента они выполняют свою работу. */
        public void openGate() {
            gate.countDown();
        }

        /** Дождаться завершения обоих потоков. */
        public void joinAll() throws InterruptedException {
            for (Thread thread : threads) {
                thread.join();
            }
        }

        /** Проверить, что все потоки группы завершились. */
        public boolean allTerminated() {
            return threads.stream().noneMatch(Thread::isAlive);
        }
    }
}
