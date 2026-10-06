package ru.tsu.tpm.vacancyparser.hw07.deadlock.livelock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Моделирование livelock: потоки активно повторяют попытку, но не продвигаются.
 *
 * <p>Два потока встречаются на раундовом барьере и решают исход раунда. В конфигурации livelock оба
 * каждый раунд уступают друг другу — попытки растут, успешных итераций нет; потоки при этом активны и
 * не удерживают блокировок, чем livelock и отличается от deadlock (взаимное ожидание) и starvation
 * (жертва ждёт, пока другие получают ресурс). В конфигурации «норма» столкновения разрешаются по
 * очереди, и успешные итерации происходят — это и есть предотвращение через детерминированный порядок.
 *
 * <p>Сценарий ограничен окном наблюдения и всегда завершается: потоки останавливаются по признаку, а
 * барьер учитывает остановку, чтобы никто не остался ждать.
 */
public final class LivelockScenario {

    /** Имя сценария для файла дампа и выбора в демонстрации. */
    public static final String NAME = "livelock";

    /** Префикс имён потоков сценария. */
    public static final String THREAD_PREFIX = "Livelock-";

    private static final int WORKERS = 2;

    /**
     * Итог сценария.
     *
     * @param config            какая конфигурация выполнялась
     * @param windowMillis      окно наблюдения
     * @param attemptsPerWorker попытки каждого потока
     * @param successes         успешно завершённые итерации
     * @param verdict           вердикт детектора отсутствия продвижения
     * @param threadStates      состояния потоков во время окна
     * @param elapsedNanos      фактическая длительность
     */
    public record Outcome(
            LivelockConfig config,
            long windowMillis,
            List<Long> attemptsPerWorker,
            long successes,
            LivelockDetector.Verdict verdict,
            List<String> threadStates,
            long elapsedNanos) {

        public Outcome {
            attemptsPerWorker = List.copyOf(attemptsPerWorker);
            threadStates = List.copyOf(threadStates);
        }

        /** Суммарное число попыток. */
        public long totalAttempts() {
            return attemptsPerWorker.stream().mapToLong(Long::longValue).sum();
        }
    }

    private LivelockScenario() {
    }

    /** Выполнить сценарий с заданной конфигурацией в течение окна наблюдения. */
    public static Outcome run(LivelockConfig config, long windowMillis) throws InterruptedException {
        AtomicBoolean stop = new AtomicBoolean();
        RoundBarrier barrier = new RoundBarrier(WORKERS);
        List<AtomicLong> attempts = new ArrayList<>(WORKERS);
        AtomicLong successes = new AtomicLong();
        List<Thread> threads = new ArrayList<>(WORKERS);

        for (int i = 0; i < WORKERS; i++) {
            int index = i;
            AtomicLong counter = new AtomicLong();
            attempts.add(counter);
            Thread worker = new Thread(() -> loop(config, stop, barrier, counter, successes, index), THREAD_PREFIX + (index + 1));
            worker.setDaemon(true);
            threads.add(worker);
        }

        long start = System.nanoTime();
        threads.forEach(Thread::start);
        Thread.sleep(Math.max(20L, windowMillis / 4));
        List<String> states = new ArrayList<>();
        for (Thread worker : threads) {
            states.add(worker.getName() + "=" + worker.getState());
        }
        Thread.sleep(Math.max(0L, windowMillis - (System.nanoTime() - start) / 1_000_000L));

        stop.set(true);
        for (Thread worker : threads) {
            worker.join(5_000L);
        }
        long elapsedNanos = System.nanoTime() - start;

        long total = attempts.stream().mapToLong(AtomicLong::get).sum();
        LivelockDetector.Verdict verdict = LivelockDetector.evaluate(total, successes.get());

        return new Outcome(config, windowMillis, attempts.stream().map(AtomicLong::get).toList(),
                successes.get(), verdict, states, elapsedNanos);
    }

    private static void loop(
            LivelockConfig config,
            AtomicBoolean stop,
            RoundBarrier barrier,
            AtomicLong attempts,
            AtomicLong successes,
            int index) {
        while (!stop.get()) {
            attempts.incrementAndGet();
            long round = barrier.meet(stop::get);
            if (round < 0) {
                return;
            }
            boolean winner = config == LivelockConfig.NORMAL && Math.floorMod(round, WORKERS) == index;
            if (winner) {
                successes.incrementAndGet();
            } else {
                backoff(stop);
            }
        }
    }

    private static void backoff(AtomicBoolean stop) {
        for (int i = 0; i < 200 && !stop.get(); i++) {
            Thread.onSpinWait();
        }
    }
}
