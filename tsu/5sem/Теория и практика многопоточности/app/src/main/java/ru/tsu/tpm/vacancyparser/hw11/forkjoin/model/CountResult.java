package ru.tsu.tpm.vacancyparser.hw11.forkjoin.model;

import java.util.Locale;

/**
 * Агрегат одной партии и всего подсчёта.
 *
 * <p>Складывается покомпонентно ({@link #plus}), поэтому рекурсивная задача может объединять
 * результаты подзадач в любом порядке — операция ассоциативна и коммутативна. Это обязательное
 * условие корректности Fork/Join: порядок завершения подзадач не определён.
 *
 * @param processed сколько записей просмотрено
 * @param matched   сколько записей подошло под критерий — основной ответ задания
 * @param remote    сколько подошедших вакансий удалённые
 * @param salarySum сумма зарплат подошедших вакансий
 * @param maxSalary максимальная зарплата среди подошедших
 */
public record CountResult(long processed, long matched, long remote, long salarySum, int maxSalary) {

    /** Пустой агрегат — нейтральный элемент сложения. */
    public static CountResult empty() {
        return new CountResult(0, 0, 0, 0, 0);
    }

    /** Сложить два агрегата. */
    public CountResult plus(CountResult other) {
        return new CountResult(
                processed + other.processed,
                matched + other.matched,
                remote + other.remote,
                salarySum + other.salarySum,
                Math.max(maxSalary, other.maxSalary));
    }

    /** Средняя зарплата подошедших вакансий. */
    public double averageSalary() {
        return matched == 0 ? 0.0 : salarySum / (double) matched;
    }

    /** Строка для консоли и отчёта. */
    public String reportLine() {
        return String.format(
                Locale.ROOT,
                "просмотрено %d, подошло %d, из них удалённых %d, сумма зарплат %d ₽, "
                        + "средняя %.1f ₽, максимум %d ₽",
                processed, matched, remote, salarySum, averageSalary(), maxSalary);
    }
}
