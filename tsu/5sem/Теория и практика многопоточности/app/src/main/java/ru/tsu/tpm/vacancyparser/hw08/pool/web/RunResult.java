package ru.tsu.tpm.vacancyparser.hw08.pool.web;

import java.util.List;

/**
 * Итог прогона по всему списку адресов.
 *
 * <p>Записи хранятся в порядке входного списка адресов (результат пишется по индексу адреса), поэтому
 * сводка детерминирована и не зависит от того, в каком порядке завершились запросы.
 *
 * @param results       результат по каждому адресу в порядке списка
 * @param runNanos      суммарное время прогона (от подачи первой задачи до последнего результата)
 * @param rejectionCount сколько раз сработал обработчик отказа за прогон
 */
public record RunResult(List<RequestResult> results, long runNanos, long rejectionCount) {

    public RunResult {
        results = List.copyOf(results);
    }

    /** Число записей — должно совпадать с числом адресов. */
    public int size() {
        return results.size();
    }

    /** Результат по индексу. */
    public RequestResult at(int index) {
        return results.get(index);
    }

    /** Сумма времён ответов (время каждого запроса по отдельности). */
    public long sumResponseNanos() {
        return results.stream().mapToLong(RequestResult::responseNanos).sum();
    }

    /** Суммарное время прогона в миллисекундах. */
    public double runMillis() {
        return runNanos / 1_000_000.0;
    }

    /** Сумма времён ответов в миллисекундах. */
    public double sumResponseMillis() {
        return sumResponseNanos() / 1_000_000.0;
    }
}
