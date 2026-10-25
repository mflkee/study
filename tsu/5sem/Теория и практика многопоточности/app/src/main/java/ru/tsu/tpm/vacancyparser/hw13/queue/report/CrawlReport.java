package ru.tsu.tpm.vacancyparser.hw13.queue.report;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.PageResult;

/**
 * Итог одного прогона «производители—потребители».
 *
 * <p>Записывает главное, что нужно проверить и показать: сколько задач положено и взято, сколько
 * страниц обработано успешно, как глубоко заполнялась очередь, сколько раз производитель ждал места и
 * за какое время всё прошло. {@link #lossless()} и {@link #duplicatePages()} — это проверка
 * корректности конвейера, а не украшение отчёта: очередь без потерь и без двойной обработки и есть
 * цель задания.
 *
 * @param label              название варианта (например, «очередь на 4»)
 * @param requested          сколько адресов подано
 * @param produced           сколько задач положили производители
 * @param consumed           сколько задач взяли потребители
 * @param succeeded          сколько страниц обработано успешно
 * @param failed             сколько страниц обработано с известным сбоем (например, 404)
 * @param maxQueueDepth      максимальная наблюдённая глубина очереди
 * @param queueCapacity      ёмкость очереди
 * @param producers          число производителей
 * @param consumers          число потребителей
 * @param blockedPuts        сколько раз {@code put} ждал освобождения места
 * @param blockedPutMillis   суммарное время ожидания в {@code put}
 * @param durationMillis     длительность прогона
 * @param stoppedWithinTimeout все ли потоки завершились в отведённое время
 * @param results            результаты обработки (в порядке завершения)
 */
public record CrawlReport(
        String label,
        int requested,
        int produced,
        int consumed,
        int succeeded,
        int failed,
        int maxQueueDepth,
        int queueCapacity,
        int producers,
        int consumers,
        long blockedPuts,
        double blockedPutMillis,
        double durationMillis,
        boolean stoppedWithinTimeout,
        List<PageResult> results) {

    public CrawlReport {
        results = List.copyOf(results);
    }

    /** Пропускная способность, страниц в секунду. */
    public double throughputPerSecond() {
        return durationMillis <= 0 ? 0.0 : consumed * 1_000.0 / durationMillis;
    }

    /** Ни одна задача не потерялась: положено ровно столько, сколько взято. */
    public boolean lossless() {
        return produced == consumed && produced == requested;
    }

    /** Сколько разных страниц реально обработано (дубликатов быть не должно). */
    public int distinctPages() {
        Set<Integer> pages = new HashSet<>();
        results.forEach(result -> pages.add(result.pageNumber()));
        return pages.size();
    }

    /** Были ли страницы обработаны повторно. */
    public boolean duplicatePages() {
        return distinctPages() != results.size();
    }

    /** Строка итога для консоли. */
    public String reportLine() {
        return String.format(
                Locale.ROOT,
                "%s: подано %d, положено %d, взято %d, успешно %d, сбоев %d, "
                        + "глубина очереди %d из %d, блокировок put %d (%.1f мс), %.3f мс, %.1f стр./с",
                label, requested, produced, consumed, succeeded, failed,
                maxQueueDepth, queueCapacity, blockedPuts, blockedPutMillis, durationMillis,
                throughputPerSecond());
    }
}
