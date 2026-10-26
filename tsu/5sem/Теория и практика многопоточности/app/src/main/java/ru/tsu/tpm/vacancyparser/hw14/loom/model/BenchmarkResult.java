package ru.tsu.tpm.vacancyparser.hw14.loom.model;

import java.util.Locale;

/**
 * Итог одного прогона: сколько задач выполнено, за какое время и на каких потоках.
 *
 * <p>Набор полей подобран под вопрос ДЗ: «чем виртуальные потоки отличаются от платформенного пула
 * на блокирующем IO». Поэтому кроме времени сохраняются:
 *
 * <ul>
 *   <li>{@code peakConcurrentTasks} — пик одновременно выполнявшихся задач (для пула он равен размеру
 *       пула, для виртуальных потоков — числу задач, реально стартовавших вместе);</li>
 *   <li>{@code workerThreads} — сколько разных потоков участвовало (у пула — размер пула, у
 *       виртуальных потоков с именованными потоками — по числу задач);</li>
 *   <li>{@code virtualExecutions}/{@code platformExecutions} — доказательство, что работа шла именно
 *       виртуальными или именно платформенными потоками;</li>
 *   <li>{@code platformThreadsPeakDelta} — прирост пика платформенных потоков JVM
 *       ({@code ThreadMXBean}); виртуальные потоки в этот счётчик не входят, и это тоже часть
 *       вывода: «тысячи потоков» не означают тысячи потоков ОС;</li>
 *   <li>{@code heapDeltaBytes} — приблизительная разница занятой кучи до и после прогона; замер
 *       делается без принудительного {@code System.gc()}, поэтому это оценка, а не точное значение.</li>
 * </ul>
 *
 * @param label                   название прогона
 * @param tasks                   сколько задач подано
 * @param succeeded               сколько завершилось успешно
 * @param failed                  сколько завершилось исключением
 * @param cancelled               сколько не дождались и были отменены по таймауту
 * @param wallTimeNanos           время от подачи первой задачи до завершения последней
 * @param peakConcurrentTasks     пик одновременно выполнявшихся задач
 * @param workerThreads           число разных потоков-исполнителей
 * @param virtualExecutions       исполнений на виртуальных потоках
 * @param platformExecutions      исполнений на платформенных потоках
 * @param platformThreadsPeakDelta прирост пика платформенных потоков JVM
 * @param heapDeltaBytes          приблизительный прирост занятой кучи
 */
public record BenchmarkResult(
        String label,
        int tasks,
        int succeeded,
        int failed,
        int cancelled,
        long wallTimeNanos,
        int peakConcurrentTasks,
        int workerThreads,
        int virtualExecutions,
        int platformExecutions,
        int platformThreadsPeakDelta,
        long heapDeltaBytes) {

    /** Время прогона в миллисекундах. */
    public double wallMillis() {
        return wallTimeNanos / 1_000_000.0;
    }

    /** Пропускная способность: задач в секунду. */
    public double throughputPerSecond() {
        if (wallTimeNanos <= 0L) {
            return 0.0;
        }
        return succeeded * 1_000_000_000.0 / wallTimeNanos;
    }

    /** Все поданные задачи дошли до конца (успех или ожидаемый сбой), отменённых нет. */
    public boolean completed() {
        return cancelled == 0 && succeeded + failed == tasks;
    }

    /** Прогон шёл на виртуальных потоках (ни одной задачи на платформенном потоке). */
    public boolean virtualOnly() {
        return virtualExecutions > 0 && platformExecutions == 0;
    }

    /** Прогон шёл на платформенных потоках (ни одной задачи на виртуальном). */
    public boolean platformOnly() {
        return platformExecutions > 0 && virtualExecutions == 0;
    }

    /** Строка для журнала. */
    public String summaryLine() {
        return String.format(
                Locale.ROOT,
                "%s: задач %d (вернули результат %d, исключений %d, отменено %d), %.1f мс, %.1f задач/с, "
                        + "пик одновременных %d, потоков-исполнителей %d, прирост потоков JVM %d",
                label, tasks, succeeded, failed, cancelled, wallMillis(), throughputPerSecond(),
                peakConcurrentTasks, workerThreads, platformThreadsPeakDelta);
    }
}
