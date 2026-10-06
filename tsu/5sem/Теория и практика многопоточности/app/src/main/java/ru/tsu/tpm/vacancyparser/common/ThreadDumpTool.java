package ru.tsu.tpm.vacancyparser.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Снятие дампа потоков средствами JDK.
 *
 * <p>Основной инструмент — {@code jstack <pid>}, резервный — {@code jcmd <pid> Thread.print}: он
 * делает то же самое и доступен всегда, когда доступен сам JDK. Полученный текст сохраняется в
 * каталог сборки, чтобы фрагмент можно было приложить к отчёту, а полный дамп — посмотреть.
 *
 * <p>Инструменты ищутся сначала рядом с текущей JVM ({@code java.home/bin}), затем в {@code PATH}:
 * так демонстрация работает и когда JDK лежит отдельно, и когда {@code jstack} добавлен в путь.
 * Если инструментов нет вовсе, {@link #capture} возвращает неуспех с понятным сообщением и
 * <b>не</b> бросает исключение — диагностика не должна ронять сценарий.
 */
public final class ThreadDumpTool {

    /** Маркер заголовка дампа потоков, по которому проверяется успех снятия. */
    public static final String DUMP_HEADER = "Full thread dump";

    private static final long TOOL_TIMEOUT_SECONDS = 20L;
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    /** Как искать исполняемый файл инструмента по имени; {@code null} — инструмент недоступен. */
    @FunctionalInterface
    public interface ToolLocator {
        String locate(String name);

        /** Стандартный поиск: рядом с JVM, затем в {@code PATH}. */
        static ToolLocator standard() {
            return name -> {
                Path nearJvm = Path.of(System.getProperty("java.home"), "bin", name);
                if (Files.isExecutable(nearJvm)) {
                    return nearJvm.toString();
                }
                String path = System.getenv("PATH");
                if (path == null) {
                    return null;
                }
                for (String dir : path.split(java.io.File.pathSeparator)) {
                    if (dir.isBlank()) {
                        continue;
                    }
                    Path candidate = Path.of(dir, name);
                    if (Files.isExecutable(candidate)) {
                        return candidate.toString();
                    }
                }
                return null;
            };
        }
    }

    /**
     * Итог снятия дампа.
     *
     * @param captured получилось ли снять дамп
     * @param text     текст дампа (пусто при неуспехе)
     * @param file     файл, в который сохранён дамп ({@code null} при неуспехе)
     * @param tool     имя использованного инструмента
     * @param message  человекочитаемое пояснение
     */
    public record CapturedDump(boolean captured, String text, Path file, String tool, String message) {
    }

    private ThreadDumpTool() {
    }

    /** Снять дамп процесса в каталог {@code target/dumps}. */
    public static CapturedDump capture(long pid, String scenario, String label) {
        return capture(pid, scenario, label, Path.of("target", "dumps"), ToolLocator.standard());
    }

    /**
     * Снять дамп процесса: сначала {@code jstack}, при неудаче — {@code jcmd ... Thread.print}.
     *
     * @param dumpsDir каталог для сохранения дампа
     * @param locator  способ поиска инструментов
     */
    public static CapturedDump capture(
            long pid, String scenario, String label, Path dumpsDir, ToolLocator locator) {
        String jstack = locator.locate("jstack");
        if (jstack != null) {
            String text = run(List.of(jstack, Long.toString(pid)));
            if (isDump(text)) {
                return save(text, pid, scenario, label, dumpsDir, "jstack");
            }
        }

        String jcmd = locator.locate("jcmd");
        if (jcmd != null) {
            String text = run(List.of(jcmd, Long.toString(pid), "Thread.print"));
            if (isDump(text)) {
                return save(text, pid, scenario, label, dumpsDir, "jcmd Thread.print");
            }
        }

        return new CapturedDump(
                false,
                "",
                null,
                "",
                "инструменты jstack/jcmd недоступны, дамп не снят; ручная диагностика: jstack <pid> или kill -3");
    }

    private static boolean isDump(String text) {
        return text != null && !text.isBlank() && text.contains(DUMP_HEADER);
    }

    private static CapturedDump save(
            String text, long pid, String scenario, String label, Path dumpsDir, String tool) {
        try {
            Files.createDirectories(dumpsDir);
            Path file = dumpsDir.resolve("hw07-" + scenario + "-" + label + ".jstack");
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return new CapturedDump(true, text, file, tool, "дамп сохранён: " + file);
        } catch (IOException e) {
            return new CapturedDump(false, text, null, tool, "не удалось сохранить дамп: " + e.getMessage());
        }
    }

    /** Запустить инструмент и вернуть его вывод; при любой неудаче — пустую строку. */
    private static String run(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.redirectErrorStream(true);
        Process process = null;
        try {
            process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return process.exitValue() == 0 ? output : "";
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** Метка времени для имени файла дампа. */
    public static String timestampLabel() {
        return LocalDateTime.now().format(LABEL_FORMAT);
    }
}
