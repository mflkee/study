package ru.tsu.tpm.vacancyparser.hw07.deadlock.deadlock;

import java.util.List;
import ru.tsu.tpm.vacancyparser.common.ProcessInfo;
import ru.tsu.tpm.vacancyparser.common.ThreadDumpParser;
import ru.tsu.tpm.vacancyparser.common.ThreadDumpTool;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;

/**
 * Полный сценарий взаимной блокировки: воспроизвести, подтвердить, снять дамп, разобрать, освободить.
 *
 * <p>После подтверждения взаимного ожидания сценарий выдерживает окно наблюдения, снимает дамп
 * потоков средствами JDK и разбирает его, а затем прерывает потоки и убеждается, что они завершились.
 * Так сценарий совместим и с {@code ApplicationRunner}, и с {@code mvn test}: он не виснет, а
 * заканчивается за предсказуемое время.
 */
public final class DeadlockScenario {

    /** Имя сценария для имени файла дампа и выбора в демонстрации. */
    public static final String NAME = "deadlock";

    /**
     * Итог сценария.
     *
     * @param deadlocked        зафиксирована ли JVM взаимная блокировка
     * @param jvmThreadNames    имена заблокированных потоков по данным JVM
     * @param dumpCaptured      удалось ли снять дамп
     * @param dumpPath          путь к сохранённому дампу
     * @param report            результат разбора дампа
     * @param released          завершились ли потоки после освобождения
     * @param holdMillis        длительность удержания состояния блокировки
     * @param liveThreads       живые потоки сценария после освобождения (ожидается пусто)
     */
    public record Outcome(
            boolean deadlocked,
            String jvmThreadNames,
            boolean dumpCaptured,
            String dumpPath,
            ThreadDumpParser.DeadlockReport report,
            boolean released,
            long holdMillis,
            List<String> liveThreads) {

        public Outcome {
            liveThreads = List.copyOf(liveThreads);
        }
    }

    private DeadlockScenario() {
    }

    /** Выполнить сценарий: взаимная блокировка удерживается {@code holdMillis}, затем снимается. */
    public static Outcome run(long holdMillis) throws InterruptedException {
        long pid = ProcessInfo.currentPid();
        DeadlockRig rig = new DeadlockRig();
        rig.start();
        boolean firstLocks = rig.awaitFirstLocks(2_000L);
        rig.proceedToSecondLock();
        boolean deadlocked = firstLocks && rig.awaitDeadlock(5_000L);
        String jvmNames = rig.deadlockedThreadNames();

        Thread.sleep(Math.max(0L, holdMillis));

        ThreadDumpTool.CapturedDump dump = ThreadDumpTool.capture(pid, NAME, ThreadDumpTool.timestampLabel());
        ThreadDumpParser.DeadlockReport report = ThreadDumpParser.parse(dump.text());

        boolean released = rig.release();
        List<String> live = ThreadDump.liveThreadsWithPrefixes(DeadlockRig.THREAD_A, DeadlockRig.THREAD_B);

        return new Outcome(deadlocked, jvmNames, dump.captured(), dump.file() == null ? "" : dump.file().toString(),
                report, released, holdMillis, live);
    }

    /** Имя сценария для файла дампа. */
    public static String name() {
        return NAME;
    }
}
