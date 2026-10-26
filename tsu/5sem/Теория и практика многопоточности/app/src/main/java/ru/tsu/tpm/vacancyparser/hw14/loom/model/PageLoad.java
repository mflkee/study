package ru.tsu.tpm.vacancyparser.hw14.loom.model;

import java.util.Objects;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.ThreadProbe;

/**
 * Результат загрузки одной страницы вакансии.
 *
 * <p>Запись неизменяемая: её складывают в общий список из разных потоков, и после прогона она уже
 * не меняется. Вместе с заголовком сохраняется «паспорт» потока, который выполнил запрос
 * ({@link ThreadProbe}): по нему видно, был ли поток виртуальным и на каком carrier-потоке он
 * работал. Это позволяет проверить главное утверждение ДЗ не на словах, а по фактическим данным.
 *
 * @param url           адрес страницы
 * @param pageNumber    номер страницы (детерминирует заголовок)
 * @param status        код ответа HTTP; {@code 0} — сбой транспорта
 * @param title         извлечённый заголовок; пусто при сбое
 * @param error         описание сбоя; {@code null}, если страница загружена
 * @param durationNanos длительность запроса
 * @param worker        поток, выполнивший запрос
 */
public record PageLoad(
        String url,
        int pageNumber,
        int status,
        String title,
        String error,
        long durationNanos,
        ThreadProbe worker) {

    public PageLoad {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(worker, "worker");
    }

    /** Успешная загрузка с извлечённым заголовком. */
    public static PageLoad success(
            String url, int pageNumber, int status, String title, long durationNanos, ThreadProbe worker) {
        return new PageLoad(url, pageNumber, status, title, null, durationNanos, worker);
    }

    /** Сбой загрузки: код ответа или описание ошибки транспорта. */
    public static PageLoad failure(
            String url, int pageNumber, int status, String error, long durationNanos, ThreadProbe worker) {
        return new PageLoad(url, pageNumber, status, "", Objects.requireNonNull(error, "error"), durationNanos, worker);
    }

    /** Страница загружена и заголовок извлечён. */
    public boolean ok() {
        return error == null && status == 200 && title != null && !title.isBlank();
    }

    /** Длительность запроса в миллисекундах. */
    public double durationMillis() {
        return durationNanos / 1_000_000.0;
    }
}
