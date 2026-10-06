package ru.tsu.tpm.vacancyparser.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор текста дампа потоков на предмет взаимной блокировки.
 *
 * <p>JVM, обнаружив цикл, печатает отдельную секцию: строку-маркер {@code Found one Java-level
 * deadlock}, перечень потоков цикла и указание, какой ресурс поток ожидает и кем он удерживается.
 * Разбор опирается на эти устойчивые маркеры, а не на номера строк и время запуска, поэтому работает
 * на дампах разных запусков. Секция ищется независимо от порядка потоков в остальном дампе.
 *
 * <p>Если такой секции нет, это означает «взаимная блокировка не подтверждена» — не ошибка разбора.
 */
public final class ThreadDumpParser {

    private static final Pattern MARKER =
            Pattern.compile("^Found\\s+(one|\\d+)\\s+Java-level\\s+deadlock", Pattern.MULTILINE);
    private static final Pattern SECTION_END =
            Pattern.compile("^(Java stack information for the threads listed above:|Found\\s+\\d+\\s+deadlock)");
    private static final Pattern THREAD_HEADER = Pattern.compile("^\"([^\"]+)\"\\s*:.*");
    private static final Pattern WAIT_KEYWORD =
            Pattern.compile("\\b(?:waiting to lock|waiting for|parking to wait for)\\s+(.+)$");
    private static final Pattern HELD_BY = Pattern.compile("which is held by\\s+\"([^\"]+)\"");

    /**
     * Ожидание одного ресурса одним потоком.
     *
     * @param thread   имя ожидающего потока
     * @param resource описание ресурса, который поток ожидает
     * @param heldBy   имя потока, который этот ресурс удерживает ({@code null}, если не указано)
     */
    public record Wait(String thread, String resource, String heldBy) {
    }

    /**
     * Результат разбора.
     *
     * @param detected    найдена ли секция обнаружения взаимной блокировки
     * @param threadNames имена потоков, входящих в цикл (в порядке появления)
     * @param waits       ожидания ресурсов внутри секции
     * @param rawSection  выделенная секция как есть (пусто, если не найдена)
     */
    public record DeadlockReport(boolean detected, List<String> threadNames, List<Wait> waits, String rawSection) {

        public DeadlockReport {
            threadNames = List.copyOf(threadNames);
            waits = List.copyOf(waits);
        }

        /** Вывод одной строкой для отчёта и демонстрации. */
        public String summary() {
            if (!detected) {
                return "взаимная блокировка не обнаружена";
            }
            return "взаимная блокировка обнаружена: потоки " + String.join(", ", threadNames);
        }
    }

    private ThreadDumpParser() {
    }

    /** Разобрать текст дампа. */
    public static DeadlockReport parse(String dumpText) {
        if (dumpText == null) {
            return new DeadlockReport(false, List.of(), List.of(), "");
        }

        String[] lines = dumpText.split("\\R", -1);
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (MARKER.matcher(lines[i]).find()) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return new DeadlockReport(false, List.of(), List.of(), "");
        }

        int end = lines.length;
        for (int i = start + 1; i < lines.length; i++) {
            if (SECTION_END.matcher(lines[i]).find()) {
                end = i;
                break;
            }
        }

        List<String> section = new ArrayList<>();
        for (int i = start; i < end; i++) {
            section.add(lines[i]);
        }
        String rawSection = String.join(System.lineSeparator(), section);

        // Поток и его ожидание: имя потока задаёт блок, следующий за ним — что он ждёт.
        Map<String, Wait> waitsByThread = new LinkedHashMap<>();
        String currentThread = null;
        for (int i = start + 1; i < end; i++) {
            Matcher header = THREAD_HEADER.matcher(lines[i]);
            if (header.matches()) {
                currentThread = header.group(1);
                continue;
            }
            if (currentThread == null) {
                continue;
            }
            Matcher keyword = WAIT_KEYWORD.matcher(lines[i]);
            if (keyword.find()) {
                String resource = keyword.group(1).trim().replaceAll("[,\\s]+$", "");
                String heldBy = null;
                // Имя держателя может быть как в этой же строке, так и в следующей.
                for (int j = i; j < Math.min(i + 2, end); j++) {
                    Matcher held = HELD_BY.matcher(lines[j]);
                    if (held.find()) {
                        heldBy = held.group(1);
                        break;
                    }
                }
                waitsByThread.put(currentThread, new Wait(currentThread, resource, heldBy));
            }
        }

        List<String> names = new ArrayList<>(waitsByThread.keySet());
        List<Wait> waits = new ArrayList<>(waitsByThread.values());
        return new DeadlockReport(true, names, waits, rawSection);
    }
}
