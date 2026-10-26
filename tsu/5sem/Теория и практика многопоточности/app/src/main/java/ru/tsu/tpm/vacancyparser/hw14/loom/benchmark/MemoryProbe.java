package ru.tsu.tpm.vacancyparser.hw14.loom.benchmark;

/**
 * Приблизительный замер занятой кучи.
 *
 * <p>{@code Runtime.totalMemory() - freeMemory()} — это «сколько кучи сейчас занято», включая мусор,
 * который ещё не собрал GC. Между двумя замерами сборка может произойти или не произойти, поэтому
 * разница — <b>оценка</b>, а не точное потребление: она годится, чтобы показать порядок величин
 * (единицы и десятки мегабайт на десятки тысяч виртуальных потоков), но не для сравнения «до
 * килобайта». Принудительный {@code System.gc()} в замер не входит намеренно: он искажает время
 * прогона, ради которого замер и делается.
 */
public final class MemoryProbe {

    private MemoryProbe() {
    }

    /** Занятая куча в байтах на момент вызова. */
    public static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /** Разница занятой кучи; может быть отрицательной, если GC отработал между замерами. */
    public static long deltaBytes(long before) {
        return usedHeapBytes() - before;
    }

    /** Приблизительный прирост в мегабайтах с одним знаком после запятой. */
    public static String describeDelta(long deltaBytes) {
        if (deltaBytes <= 0L) {
            return "0.0 МиБ (GC отработал во время прогона — замер приблизительный)";
        }
        return String.format(java.util.Locale.ROOT, "%.1f МиБ", deltaBytes / (1024.0 * 1024.0));
    }
}
