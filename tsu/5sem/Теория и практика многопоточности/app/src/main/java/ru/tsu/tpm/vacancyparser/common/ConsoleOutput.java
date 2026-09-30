package ru.tsu.tpm.vacancyparser.common;

import java.io.PrintStream;
import org.springframework.stereotype.Component;

/**
 * Единая точка консольного вывода приложения.
 *
 * <p>Код логики не обращается к {@code System.out} напрямую — только через этот класс.
 * Так вывод домашних заданий не зависит от настроек логирования Spring и одинаково
 * выглядит в консоли, в файле отчёта и в выводе юнит-теста.
 *
 * <p>Класс потокобезопасен: строки копятся в {@link StringBuffer}, поэтому демонстрации
 * ДЗ 1, где пишут несколько потоков сразу, не теряют и не перемешивают строки.
 */
@Component
public class ConsoleOutput {

    private static final String PREFIX = "[tpm] ";
    private static final String ITEM_INDENT = "    - ";

    private final PrintStream out;
    private final StringBuffer captured = new StringBuffer();

    /** Конструктор по умолчанию — используется Spring-контекстом. */
    public ConsoleOutput() {
        this(System.out);
    }

    /** Конструктор для тестов: вывод уходит в переданный поток. */
    public ConsoleOutput(PrintStream out) {
        this.out = out;
    }

    /** Заголовок раздела — визуально отделяет этапы демонстрации. */
    public void section(String title) {
        String line = "=== " + title + " ===";
        append(line);
        append("");
    }

    /** Сообщение с префиксом приложения. */
    public void info(String message) {
        append(PREFIX + message);
    }

    /** Элемент списка с отступом. */
    public void item(String message) {
        append(ITEM_INDENT + message);
    }

    /** Строка без префикса — для вывода, который должен попасть в отчёт дословно. */
    public void raw(String message) {
        append(message);
    }

    /**
     * Предупреждение о незавершённом или неуспешном шаге.
     *
     * <p>Используется там, где сценарий умеет деградировать: вместо зависания или падения
     * демонстрация сообщает, что ожидаемого результата не достигнуто.
     */
    public void warn(String message) {
        append(PREFIX + "ВНИМАНИЕ: " + message);
    }

    /** Весь вывод, накопленный с момента создания объекта. */
    public synchronized String captured() {
        return captured.toString();
    }

    /** Сбросить накопленный вывод. */
    public synchronized void reset() {
        captured.setLength(0);
    }

    /**
     * Запись одной строки целиком.
     *
     * <p>Метод синхронизирован целиком, а не по отдельным вызовам {@code StringBuffer}:
     * строка и её перевод строки должны попасть в накопитель одной операцией, иначе
     * вывод из двух потоков сшивается в одну строку (проверяется тестом на конкурентной
     * записи). Заодно сериализуется запись в поток вывода, чтобы строки не перемешивались.
     */
    private synchronized void append(String line) {
        captured.append(line).append(System.lineSeparator());
        out.println(line);
    }
}
