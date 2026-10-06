package ru.tsu.tpm.vacancyparser.hw06.atomics;

import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;

/**
 * Остановка рабочего потока по {@code volatile}-признаку с ограниченным ожиданием завершения.
 *
 * <p>Порядок действий: запустить {@link StoppableWorker}, дать ему поработать, установить признак и
 * дождаться завершения через {@code join()} <b>с тайм-аутом</b>. Тайм-аут обязателен: {@code volatile}
 * гарантирует видимость, но не время реакции, и даже корректный поток может быть вытеснен. Если
 * поток не завершился за отведённое время, снимается дамп потоков, а вызывающий код получает признак
 * «не уложились» — вместо бесконечного зависания демонстрации.
 */
public final class FlagStopScenario {

    /** Предел ожидания завершения рабочего потока. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 2_000L;

    /** Сколько поток успевает поработать до установки признака. */
    public static final long WORK_WINDOW_MILLIS = 50L;

    /** Имя рабочего потока — по нему демонстрация проверяет, что потоков не осталось. */
    public static final String WORKER_NAME = "hw06-flag-worker";

    private FlagStopScenario() {
    }

    /**
     * Итог остановки рабочего потока.
     *
     * @param stoppedInTime завершился ли поток в пределах тайм-аута
     * @param waitNanos      сколько заняло ожидание завершения
     * @param iterations     число выполненных итераций
     * @param dump           дамп потоков, если в срок не уложились; иначе {@code null}
     */
    public record StopOutcome(boolean stoppedInTime, long waitNanos, long iterations, String dump) {

        /** Время ожидания в миллисекундах — для вывода. */
        public long waitMillis() {
            return waitNanos / 1_000_000L;
        }
    }

    /** Запустить рабочий поток, остановить его признаком и дождаться завершения с тайм-аутом. */
    public static StopOutcome runStoppable(long timeoutMillis) throws InterruptedException {
        StoppableWorker worker = new StoppableWorker();
        Thread thread = new Thread(worker, WORKER_NAME);
        thread.setDaemon(true);
        thread.start();
        worker.awaitStarted(1_000L);
        Thread.sleep(WORK_WINDOW_MILLIS);

        long start = System.nanoTime();
        worker.finish();
        thread.join(timeoutMillis);
        long waitNanos = System.nanoTime() - start;

        if (thread.isAlive()) {
            return new StopOutcome(false, waitNanos, worker.iterations(), ThreadDump.capture());
        }
        return new StopOutcome(true, waitNanos, worker.iterations(), null);
    }
}
