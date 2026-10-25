package ru.tsu.tpm.vacancyparser.hw10.completable.service;

import java.util.Locale;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.FinalResult;

/**
 * Преобразование объединённого результата — «тяжёлый» шаг конвейера ДЗ 10.
 *
 * <p>Шаг выполняется через {@code thenApplyAsync(..., executor)}: нормализация заголовка и расчёт
 * оценки не должны занимать поток, который доставил ответ, и не должны уходить в
 * {@code ForkJoinPool.commonPool}. В демонстрации к вычислению добавляется моделируемая пауза
 * ({@code app.hw10.transform-delay-millis}): на реальных данных это синтаксический разбор описания,
 * а здесь — способ сделать этап измеримым.
 */
public final class VacancyScoring {

    private VacancyScoring() {
    }

    /** Нормализовать заголовок: нижний регистр, только буквы/цифры, одиночные пробелы. */
    public static String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        String lower = title.toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        boolean previousSpace = false;
        for (int index = 0; index < lower.length(); index++) {
            char current = lower.charAt(index);
            if (Character.isLetterOrDigit(current)) {
                normalized.append(current);
                previousSpace = false;
            } else if (!previousSpace && normalized.length() > 0) {
                normalized.append(' ');
                previousSpace = true;
            }
        }
        return normalized.toString().trim();
    }

    /**
     * Оценка привлекательности: середина зарплатного диапазона с поправкой на рейтинг компании.
     * Заглушка компании снижает вес — данные неполные, и оценка должна это отражать.
     */
    public static double attractiveness(FinalResult result) {
        double factor = result.companyFallback() ? 0.75 : 0.9 + result.company().rating() / 20.0;
        double raw = result.middleSalary() / 1_000.0 * factor;
        return Math.round(raw * 100.0) / 100.0;
    }

    /**
     * Выполнить преобразование.
     *
     * @param result       объединённый результат двух источников
     * @param delayMillis  моделируемая длительность тяжёлого шага, миллисекунды
     */
    public static FinalResult transform(FinalResult result, long delayMillis) {
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return result.withAttractiveness(normalizeTitle(result.title()), attractiveness(result));
    }
}
