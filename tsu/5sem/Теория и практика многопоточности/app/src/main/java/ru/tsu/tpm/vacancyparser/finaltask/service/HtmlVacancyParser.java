package ru.tsu.tpm.vacancyparser.finaltask.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.finaltask.model.ParsedVacancy;

/**
 * Разбор страницы вакансии без внешних библиотек: регулярные выражения по HTML и JSON-LD.
 *
 * <p>Порядок разбора повторяет то, что реально есть на страницах сайтов вакансий:
 * <ol>
 *   <li>JSON-LD {@code JobPosting} — машинно-читаемое описание (hh.ru, SuperJob отдают его);</li>
 *   <li>атрибуты {@code data-qa} — разметка hh.ru;</li>
 *   <li>общие {@code <h1>}, {@code <title>}, {@code og:title} и {@code meta} — как последний резерв.</li>
 * </ol>
 *
 * <p>Парсер намеренно не тянет Jsoup: для учебной задачи достаточно регэкспов, а лишняя зависимость
 * увеличивает поверхность отказа при проверке. Разбор проверяется юнит-тестом на строке HTML.
 *
 * <p>Класс без состояния, поэтому его можно вызывать из любого потока пула одновременно.
 */
@Component
public class HtmlVacancyParser {

    /** Максимальная длина сохраняемых требований (поле H2 — 4000 символов). */
    static final int REQUIREMENTS_LIMIT = 2000;

    private static final Pattern JSON_LD_BLOCK = Pattern.compile(
            "<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_TAG = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z_:.-]+)\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern TIME_DATETIME = Pattern.compile(
            "<time\\b[^>]*datetime\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern H1 = Pattern.compile("<h1\\b[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TITLE_TAG = Pattern.compile("<title\\b[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("<(script|style)\\b[^>]*>.*?</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0]+");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");
    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d\\s\\u00A0]*)");

    /** Разобрать страницу и вернуть вакансию (без сохранения). */
    public ParsedVacancy parse(String url, String html) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("не указан адрес страницы");
        }
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("пустой HTML: " + url);
        }

        String jsonLd = jsonLdBlock(html);

        String title = firstNonBlank(
                jsonString(jsonLd, "title"),
                text(qaBlock(html, "vacancy-title")),
                text(firstGroup(H1, html)),
                metaContent(html, "og:title"),
                text(firstGroup(TITLE_TAG, html)));
        String company = firstNonBlank(
                jsonNestedString(jsonLd, "hiringOrganization", "name"),
                text(qaBlock(html, "vacancy-company-name")));
        String salaryText = firstNonBlank(
                text(qaBlock(html, "vacancy-salary")),
                text(qaBlock(html, "vacancy-salary-compensation-type-net")));
        Integer salary = firstNonNull(
                jsonSalaryValue(jsonLd),
                parseSalary(salaryText));
        String city = firstNonBlank(
                text(qaBlock(html, "vacancy-view-location")),
                text(qaBlock(html, "vacancy-view-raw-address")),
                jsonNestedString(jsonLd, "jobLocation", "addressLocality"),
                metaContent(html, "place:location:locality"));
        String requirements = firstNonBlank(
                text(qaBlock(html, "vacancy-description")),
                metaContent(html, "description"));
        LocalDate publishedAt = firstNonNull(
                parseDate(jsonString(jsonLd, "datePosted")),
                parseDate(metaContent(html, "article:published_time")),
                parseDate(firstGroup(TIME_DATETIME, html)));

        return new ParsedVacancy(
                url.trim(),
                title,
                company,
                salary,
                salaryText == null ? null : truncate(salaryText, 255),
                city == null ? null : truncate(city, 255),
                requirements == null ? null : truncate(requirements, REQUIREMENTS_LIMIT),
                publishedAt);
    }

    /**
     * Извлечь число из текста зарплаты: «от 150 000 ₽» → 150000, «200 000 – 300 000 ₽» → 200000.
     *
     * <p>«По договорённости» цифр не содержит — возвращается {@code null}, а не ноль: ноль исказил бы
     * сортировку и статистику.
     */
    public static Integer parseSalary(String salaryText) {
        if (salaryText == null || salaryText.isBlank()) {
            return null;
        }
        Matcher matcher = NUMBER.matcher(salaryText);
        if (!matcher.find()) {
            return null;
        }
        String digits = matcher.group(1).replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 9) {
            return null;
        }
        return Integer.valueOf(digits);
    }

    /**
     * Превратить фрагмент HTML в обычный текст: убрать теги, раскрыть сущности, схлопнуть пробелы.
     */
    public static String text(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return null;
        }
        String withoutScripts = SCRIPT_OR_STYLE.matcher(fragment).replaceAll(" ");
        String spaced = withoutScripts
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("(?i)</(p|div|li|tr|h[1-6])>", " ");
        String plain = TAG.matcher(spaced).replaceAll(" ");
        String decoded = decodeEntities(plain);
        String collapsed = SPACES.matcher(decoded).replaceAll(" ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }

    /** Найти содержимое элемента по атрибуту {@code data-qa} (разметка hh.ru). */
    static String qaBlock(String html, String qa) {
        Pattern pattern = Pattern.compile(
                "<([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*data-qa\\s*=\\s*[\"']" + Pattern.quote(qa) + "[\"'][^>]*>(.*?)</\\1>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(2) : null;
    }

    private static String jsonLdBlock(String html) {
        Matcher matcher = JSON_LD_BLOCK.matcher(html);
        String first = null;
        while (matcher.find()) {
            String block = matcher.group(1);
            if (first == null) {
                first = block;
            }
            if (block.contains("JobPosting")) {
                return block;
            }
        }
        return first;
    }

    private static String jsonString(String json, String field) {
        if (json == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? unescapeJson(matcher.group(1)) : null;
    }

    /**
     * Строковое поле вложенного JSON-объекта: {@code "hiringOrganization": {"name": "..."}}.
     *
     * <p>Полноценный JSON-парсер здесь не нужен и не хочется: окно поиска ограничено 800 символами
     * после открывающей скобки объекта, чего достаточно для схемы {@code JobPosting}.
     */
    private static String jsonNestedString(String json, String parent, String child) {
        if (json == null) {
            return null;
        }
        Pattern parentPattern = Pattern.compile("\"" + Pattern.quote(parent) + "\"\\s*:\\s*\\{");
        Matcher parentMatcher = parentPattern.matcher(json);
        if (!parentMatcher.find()) {
            return null;
        }
        int start = parentMatcher.end();
        String tail = json.substring(start, Math.min(json.length(), start + 800));
        Pattern childPattern = Pattern.compile(
                "\"" + Pattern.quote(child) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher childMatcher = childPattern.matcher(tail);
        return childMatcher.find() ? unescapeJson(childMatcher.group(1)) : null;
    }

    private static Integer jsonSalaryValue(String json) {
        if (json == null) {
            return null;
        }
        Pattern parentPattern = Pattern.compile("\"baseSalary\"\\s*:\\s*\\{");
        Matcher parentMatcher = parentPattern.matcher(json);
        if (!parentMatcher.find()) {
            return null;
        }
        int start = parentMatcher.end();
        String tail = json.substring(start, Math.min(json.length(), start + 800));
        Matcher value = Pattern.compile("\"value\"\\s*:\\s*\"?(\\d+)\"?").matcher(tail);
        if (!value.find()) {
            return null;
        }
        try {
            return Integer.valueOf(value.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Значение {@code <meta property="..." content="...">} независимо от порядка атрибутов. */
    private static String metaContent(String html, String property) {
        Matcher meta = META_TAG.matcher(html);
        while (meta.find()) {
            String tag = meta.group();
            Matcher attribute = ATTRIBUTE.matcher(tag);
            String name = null;
            String content = null;
            while (attribute.find()) {
                String key = attribute.group(1).toLowerCase(Locale.ROOT);
                if (key.equals("property") || key.equals("name") || key.equals("itemprop")) {
                    name = attribute.group(2);
                } else if (key.equals("content")) {
                    content = attribute.group(2);
                }
            }
            if (content != null && name != null && name.equalsIgnoreCase(property)) {
                return text(content);
            }
        }
        return null;
    }

    private static String firstGroup(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(matcher.groupCount()) : null;
    }

    private static String decodeEntities(String value) {
        Matcher matcher = ENTITY.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = switch (token.toLowerCase(Locale.ROOT)) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                case "ndash" -> "–";
                case "mdash" -> "—";
                case "laquo" -> "«";
                case "raquo" -> "»";
                case "rub" -> "₽";
                case "hellip" -> "…";
                default -> null;
            };
            if (replacement == null) {
                replacement = token.startsWith("#") ? decodeNumericEntity(token) : matcher.group();
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String decodeNumericEntity(String token) {
        try {
            int codePoint = token.startsWith("#x") || token.startsWith("#X")
                    ? Integer.parseInt(token.substring(2), 16)
                    : Integer.parseInt(token.substring(1));
            return new String(Character.toChars(codePoint));
        } catch (RuntimeException e) {
            return token;
        }
    }

    private static String unescapeJson(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (current != '\\' || i + 1 >= raw.length()) {
                out.append(current);
                continue;
            }
            char next = raw.charAt(++i);
            switch (next) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 < raw.length()) {
                        try {
                            out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            out.append("\\u");
                        }
                    } else {
                        out.append("\\u");
                    }
                }
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        String datePart = trimmed.length() >= 10 ? trimmed.substring(0, 10) : trimmed;
        try {
            return LocalDate.parse(datePart);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String truncate(String value, int limit) {
        String trimmed = value.trim();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        int cut = trimmed.lastIndexOf(' ', limit);
        if (cut < limit / 2) {
            cut = limit;
        }
        return trimmed.substring(0, cut).trim() + "…";
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
