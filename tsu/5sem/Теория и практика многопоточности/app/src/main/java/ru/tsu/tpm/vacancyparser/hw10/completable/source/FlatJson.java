package ru.tsu.tpm.vacancyparser.hw10.completable.source;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Минимальный разбор «плоского» JSON-объекта.
 *
 * <p>Заглушка внешнего API отдаёт ответ в JSON, а тянуть в учебный проект библиотеку сериализации
 * ради двух коротких объектов незачем. Парсер понимает ровно то, что нужно: объект верхнего уровня,
 * значения — строка, число, {@code true}/{@code false}/{@code null}. Вложенных объектов и массивов нет
 * намеренно: неподдерживаемая конструкция должна падать явной ошибкой, а не тихо разбираться неверно.
 *
 * <p>Класс существует только для демонстрации ДЗ 10 и его тестов; в рабочем коде проекта разбор
 * HTML/JSON делают специализированные средства.
 */
public final class FlatJson {

    private FlatJson() {
    }

    /** Разобрать плоский JSON-объект в карту «ключ → строковое значение». */
    public static Map<String, String> parseObject(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("пустой JSON");
        }
        return new Parser(json).object();
    }

    /** Обязательное строковое поле. */
    public static String string(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("в ответе нет поля «" + key + "»");
        }
        return value;
    }

    /** Обязательное целое поле. */
    public static int intValue(Map<String, String> values, String key) {
        try {
            return Integer.parseInt(string(values, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("поле «" + key + "» не является целым: " + values.get(key));
        }
    }

    /** Обязательное дробное поле. */
    public static double doubleValue(Map<String, String> values, String key) {
        try {
            return Double.parseDouble(string(values, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("поле «" + key + "» не является числом: " + values.get(key));
        }
    }

    /** Логическое поле; отсутствие поля считается {@code false}. */
    public static boolean booleanValue(Map<String, String> values, String key) {
        return Boolean.parseBoolean(values.getOrDefault(key, "false"));
    }

    /** Пошаговый разбор с позицией — сообщения об ошибке указывают, где именно сломалось. */
    private static final class Parser {

        private final String text;
        private int position;

        private Parser(String text) {
            this.text = text;
        }

        private Map<String, String> object() {
            Map<String, String> values = new LinkedHashMap<>();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                position++;
                return values;
            }
            while (true) {
                skipWhitespace();
                String key = quoted();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                values.put(key, value());
                skipWhitespace();
                char next = next();
                if (next == '}') {
                    return values;
                }
                if (next != ',') {
                    throw error("ожидалась ',' или '}'");
                }
            }
        }

        private String value() {
            char current = peek();
            if (current == '"') {
                return quoted();
            }
            if (current == 'n') {
                word("null");
                return null;
            }
            if (current == 't') {
                word("true");
                return "true";
            }
            if (current == 'f') {
                word("false");
                return "false";
            }
            return number();
        }

        private String quoted() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (true) {
                char current = next();
                if (current == '"') {
                    return value.toString();
                }
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                char escaped = next();
                switch (escaped) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(unicode());
                    default -> throw error("неизвестная escape-последовательность \\" + escaped);
                }
            }
        }

        private char unicode() {
            if (position + 4 > text.length()) {
                throw error("обрезанная escape-последовательность \\u");
            }
            String digits = text.substring(position, position + 4);
            position += 4;
            try {
                return (char) Integer.parseInt(digits, 16);
            } catch (NumberFormatException e) {
                throw error("некорректная escape-последовательность \\u" + digits);
            }
        }

        private String number() {
            int start = position;
            while (position < text.length()) {
                char current = text.charAt(position);
                if (current == '-' || current == '+' || current == '.' || current == 'e' || current == 'E'
                        || (current >= '0' && current <= '9')) {
                    position++;
                } else {
                    break;
                }
            }
            if (start == position) {
                throw error("ожидалось значение");
            }
            return text.substring(start, position);
        }

        private void word(String expected) {
            if (!text.startsWith(expected, position)) {
                throw error("ожидалось «" + expected + "»");
            }
            position += expected.length();
        }

        private void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private char peek() {
            if (position >= text.length()) {
                throw error("неожиданный конец ответа");
            }
            return text.charAt(position);
        }

        private char next() {
            char current = peek();
            position++;
            return current;
        }

        private void expect(char expected) {
            char current = next();
            if (current != expected) {
                throw error("ожидался символ '" + expected + "', получен '" + current + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("JSON: " + message + " (позиция " + position + ")");
        }
    }
}
