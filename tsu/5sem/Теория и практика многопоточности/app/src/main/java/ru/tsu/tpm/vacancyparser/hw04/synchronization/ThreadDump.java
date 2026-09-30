package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;

/**
 * Снимок потоков процесса и проверка на взаимную блокировку.
 *
 * <p>Нужен в двух местах: демонстрация печатает дамп, если уложиться в лимит времени не удалось
 * (вместо того чтобы висеть), а проверка ДЗ 4 снимает дамп с живого процесса и убеждается, что
 * групп потоков, взаимно ожидающих мониторы, нет.
 *
 * <p>Определение блокировки — не «на глаз по стекам», а вопрос к виртуальной машине:
 * {@link ThreadMXBean#findDeadlockedThreads()} находит циклы ожидания мониторов и синхронизаторов.
 * Если метод вернул потоки — взаимная блокировка есть, и это не предположение, а факт от JVM.
 */
public final class ThreadDump {

    private ThreadDump() {
    }

    /** Есть ли в процессе взаимно заблокированные потоки. */
    public static boolean hasDeadlock() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] deadlocked = bean.findDeadlockedThreads();
        if (deadlocked == null || deadlocked.length == 0) {
            deadlocked = bean.findMonitorDeadlockedThreads();
        }
        return deadlocked != null && deadlocked.length > 0;
    }

    /** Имена потоков, участвующих во взаимной блокировке; пусто, если её нет. */
    public static String deadlockedThreadNames() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] ids = bean.findDeadlockedThreads();
        if (ids == null || ids.length == 0) {
            ids = bean.findMonitorDeadlockedThreads();
        }
        if (ids == null || ids.length == 0) {
            return "";
        }
        ThreadInfo[] infos = bean.getThreadInfo(ids, 8);
        StringBuilder names = new StringBuilder();
        for (ThreadInfo info : infos) {
            if (info == null) {
                continue;
            }
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(info.getThreadName());
        }
        return names.toString();
    }

    /** Текстовый дамп всех потоков со стеками — то, что обычно смотрят через {@code jstack}. */
    public static String capture() {
        StringBuilder dump = new StringBuilder();
        dump.append("Дамп потоков: всего ").append(Thread.activeCount()).append(" активных");
        dump.append(", взаимная блокировка: ").append(hasDeadlock() ? "ОБНАРУЖЕНА" : "не обнаружена");
        dump.append(System.lineSeparator());

        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            dump.append("  \"").append(thread.getName()).append("\" state=").append(thread.getState());
            dump.append(System.lineSeparator());
            for (StackTraceElement element : entry.getValue()) {
                dump.append("      at ").append(element).append(System.lineSeparator());
            }
        }
        return dump.toString();
    }

    /** Одной строкой: сколько потоков и есть ли блокировка — для краткого вывода. */
    public static String summary() {
        return "потоков в процессе: " + Thread.activeCount()
                + ", взаимная блокировка: " + (hasDeadlock() ? "ОБНАРУЖЕНА" : "не обнаружена");
    }

    /**
     * Живые потоки, чьи имена начинаются с одного из префиксов.
     *
     * <p>Нужно, чтобы подтвердить фактом, а не словами, что потоки демонстрации к моменту проверки
     * действительно завершились: пустой список — они все закончили работу.
     */
    public static List<String> liveThreadsWithPrefixes(String... prefixes) {
        List<String> alive = new ArrayList<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            for (String prefix : prefixes) {
                if (thread.getName().startsWith(prefix) && thread.isAlive()) {
                    alive.add(thread.getName() + " (" + thread.getState() + ")");
                }
            }
        }
        Collections.sort(alive);
        return alive;
    }
}
