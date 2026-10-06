package ru.tsu.tpm.vacancyparser.hw06.atomics;

/**
 * Отдельный процесс, демонстрирующий дефект видимости {@link NonVolatileFlagWorker}.
 *
 * <p>Зачем отдельный процесс, а не поток внутри демонстрации: «зомби»-поток нельзя ни прервать, ни
 * корректно остановить из другого потока (он не проверяет ни признак, ни прерывание). Внутри одной
 * JVM он остался бы крутиться до самого выхода и искажал бы замеры производительности, идущие следом.
 * Вынесенный в дочерний процесс, он живёт ровно столько, сколько нужно, и умирает вместе с ним.
 *
 * <p>Вывод намеренно машинночитаемый: строка {@code NON_VOLATILE_STOPPED=false} означает, что поток
 * не увидел установленный флаг — это и есть подтверждение дефекта.
 */
public final class NonVolatileStopProcess {

    /** Сколько поток работает до установки признака — время на то, чтобы JIT скомпилировал цикл. */
    static final long WARMUP_MILLIS = 300L;

    /** Сколько ждать завершения после установки признака. */
    static final long JOIN_MILLIS = 2_000L;

    static final String STOPPED_MARKER = "NON_VOLATILE_STOPPED=";
    static final String ITERATIONS_MARKER = "NON_VOLATILE_ITERATIONS=";

    private NonVolatileStopProcess() {
    }

    public static void main(String[] args) throws InterruptedException {
        NonVolatileFlagWorker worker = new NonVolatileFlagWorker();
        Thread thread = new Thread(worker, "hw06-nonvolatile-worker");
        thread.setDaemon(true);
        thread.start();
        worker.awaitStarted(1_000L);

        Thread.sleep(WARMUP_MILLIS);
        worker.finish();
        thread.join(JOIN_MILLIS);

        System.out.println(STOPPED_MARKER + !thread.isAlive());
        System.out.println(ITERATIONS_MARKER + worker.iterations());
        System.out.flush();
        // «Зомби»-поток — демоновый, поэтому выход не блокирует; System.exit делает это явным.
        System.exit(0);
    }
}
