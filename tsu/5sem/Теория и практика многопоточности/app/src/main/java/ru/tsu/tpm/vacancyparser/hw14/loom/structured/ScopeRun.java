package ru.tsu.tpm.vacancyparser.hw14.loom.structured;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Итог одного опыта со {@link java.util.concurrent.StructuredTaskScope}.
 *
 * <p>Главное, что нужно показать отчёту, — не «сколько было задач», а <b>чем закончилась группа</b>:
 * все подзадачи дошли до конца, одна упала и остальные были отменены, или истёк срок и скоуп снял
 * всех. Поэтому в записи три счётчика ({@code succeeded}, {@code failed}, {@code cancelled}) и
 * строка {@code outcome}; их сумма всегда равна числу подзадач.
 *
 * @param label            название опыта
 * @param policy           политика скоупа ({@code ShutdownOnFailure} / {@code ShutdownOnSuccess})
 * @param tasks            сколько подзадач запущено
 * @param succeeded        сколько вернуло результат
 * @param failed           сколько завершилось исключением
 * @param cancelled        сколько было отменено (прервано) до результата
 * @param wallNanos        время опыта: от открытия скоупа до выхода из блока {@code try}
 * @param peakConcurrentTasks пик одновременно работавших подзадач
 * @param virtualExecutions подзадач, исполнявшихся на виртуальных потоках
 * @param platformExecutions подзадач, исполнявшихся на платформенных потоках
 * @param outcome          чем закончилось: {@code успех}, {@code исключение}, {@code таймаут}, {@code прервано}
 * @param exceptionType    тип исключения, которым закончился опыт; {@code null} при успехе
 * @param exceptionMessage текст исключения; {@code null} при успехе
 * @param results          результаты, которые успели получить до отмены/сбоя
 */
public record ScopeRun(
        String label,
        String policy,
        int tasks,
        int succeeded,
        int failed,
        int cancelled,
        long wallNanos,
        int peakConcurrentTasks,
        int virtualExecutions,
        int platformExecutions,
        String outcome,
        String exceptionType,
        String exceptionMessage,
        List<String> results) {

    public ScopeRun {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(outcome, "outcome");
        results = List.copyOf(results);
    }

    /** Время опыта в миллисекундах. */
    public double wallMillis() {
        return wallNanos / 1_000_000.0;
    }

    /** Все подзадачи получили результат, потерь и отмен нет. */
    public boolean allSucceeded() {
        return succeeded == tasks && failed == 0 && cancelled == 0;
    }

    /** Счётчики сходятся с числом подзадач: ни одна не потерялась. */
    public boolean settled() {
        return succeeded + failed + cancelled == tasks;
    }

    /** Доля отменённых подзадач, проценты. */
    public double cancelledPercent() {
        if (tasks == 0) {
            return 0.0;
        }
        return 100.0 * cancelled / tasks;
    }

    /** Опыт закончился исключением или таймаутом. */
    public boolean endedWithError() {
        return exceptionType != null;
    }

    /** Строка для журнала. */
    public String summaryLine() {
        return String.format(
                Locale.ROOT,
                "%s (%s): подзадач %d, с результатом %d, с исключением %d, отменено %d, %.1f мс, "
                        + "пик одновременных %d, итог: %s",
                label, policy, tasks, succeeded, failed, cancelled, wallMillis(),
                peakConcurrentTasks, outcome);
    }

    /** Строка про исключение — для раздела «обработка ошибок». */
    public String exceptionLine() {
        if (exceptionType == null) {
            return label + ": исключений нет";
        }
        return String.format(
                Locale.ROOT,
                "%s: %s — «%s»",
                label, exceptionType, exceptionMessage == null ? "(без текста)" : exceptionMessage);
    }
}
