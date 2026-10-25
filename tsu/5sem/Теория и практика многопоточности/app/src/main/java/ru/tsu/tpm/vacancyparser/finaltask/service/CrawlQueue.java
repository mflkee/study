package ru.tsu.tpm.vacancyparser.finaltask.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.stereotype.Component;

/**
 * Потокобезопасная очередь адресов на обход.
 *
 * <p>Основа — {@link LinkedBlockingQueue}: producers (HTTP-запрос {@code POST /final/posts}, старт
 * приложения, расписание) и consumer (обход) работают одновременно и не должны видеть «полузаписанный»
 * список. Дополнительно ведётся {@link ConcurrentHashMap} адресов, ожидающих обработки: он отсекает
 * дубликаты в очереди, но не мешает поставить тот же адрес снова после того, как он обработан — иначе
 * периодический обход не смог бы обновлять данные.
 *
 * <p>Метод {@link #offerAll(Collection)} возвращает число принятых адресов; вызывающий код по разнице
 * с размером входа видит, сколько было дубликатов или некорректных значений.
 */
@Component
public class CrawlQueue {

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, Boolean> pending = new ConcurrentHashMap<>();

    /**
     * Поставить набор адресов в очередь.
     *
     * @return сколько адресов реально добавлено (без дубликатов и пустых строк)
     */
    public int offerAll(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return 0;
        }
        int accepted = 0;
        for (String url : new LinkedHashSet<>(urls)) {
            if (offer(url)) {
                accepted++;
            }
        }
        return accepted;
    }

    /** Поставить один адрес. Повтор до обработки игнорируется. */
    public boolean offer(String url) {
        String normalized = normalize(url);
        if (normalized == null) {
            return false;
        }
        if (pending.putIfAbsent(normalized, Boolean.TRUE) != null) {
            return false;
        }
        if (!queue.offer(normalized)) {
            pending.remove(normalized);
            return false;
        }
        return true;
    }

    /**
     * Забрать не более {@code max} адресов.
     *
     * <p>Адрес снимается с учёта ожидающих сразу при извлечении: пока он обрабатывается, его можно
     * поставить в очередь снова, и следующий цикл расписания обновит данные.
     */
    public List<String> pollBatch(int max) {
        int limit = Math.max(0, max);
        List<String> batch = new ArrayList<>(Math.min(limit, queue.size()));
        for (int i = 0; i < limit; i++) {
            String url = queue.poll();
            if (url == null) {
                break;
            }
            pending.remove(url);
            batch.add(url);
        }
        return batch;
    }

    /** Сколько адресов ждёт обработки. */
    public int size() {
        return queue.size();
    }

    /** Очистить очередь (например, перед повторной проверкой). */
    public void clear() {
        queue.clear();
        pending.clear();
    }

    /** Поддерживаются только HTTP(S): file:// и прочие схемы не должны попадать в загрузчик. */
    public static boolean isSupported(String url) {
        String normalized = normalize(url);
        return normalized != null;
    }

    private static String normalize(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null;
        }
        return trimmed;
    }
}
