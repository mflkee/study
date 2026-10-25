package ru.tsu.tpm.vacancyparser.hw13.queue.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Извлечение {@code <title>} из HTML — «полезная нагрузка» потребителя.
 *
 * <p>Разбор намеренно простой: регулярное выражение по заголовку, а не полноценный HTML-парсер.
 * Задание требует «скачать страницу и извлечь заголовок», и лишняя зависимость здесь ничего не
 * доказывает. Регистр тега, атрибуты и переводы строк внутри заголовка учитываются; сущности
 * раскрываются, лишние пробелы схлопываются.
 */
public final class TitleExtractor {

    private static final Pattern TITLE =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private TitleExtractor() {
    }

    /** Извлечь заголовок; пустая строка, если тега нет. */
    public static String extract(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Matcher matcher = TITLE.matcher(html);
        if (!matcher.find()) {
            return "";
        }
        return normalize(matcher.group(1));
    }

    /** Раскрыть базовые сущности и схлопнуть пробелы. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        return text.replaceAll("\\s+", " ").trim();
    }

    /** Проверка «страница пригодна»: из HTML получен непустой заголовок. */
    public static boolean hasTitle(String html) {
        return !extract(html).isEmpty();
    }
}
