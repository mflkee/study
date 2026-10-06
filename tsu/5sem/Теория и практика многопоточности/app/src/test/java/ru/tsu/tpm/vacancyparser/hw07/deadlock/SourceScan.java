package ru.tsu.tpm.vacancyparser.hw07.deadlock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Доступ к исходникам из тестов.
 *
 * <p>Часть проверок ДЗ 7 статическая (например, «в корректном варианте starvation нет приоритетов и
 * уступки процессора»). Чтобы такие проверки читали реальный файл, а не копию, тест находит каталог
 * исходников относительно рабочего каталога сборки.
 */
public final class SourceScan {

    private SourceScan() {
    }

    /** Каталог {@code src/main/java}. */
    public static Path mainJava() {
        return locate("main", "java");
    }

    /** Каталог {@code src/test/java}. */
    public static Path testJava() {
        return locate("test", "java");
    }

    /** Прочитать исходник по пути относительно каталога {@code main/java}. */
    public static String readMain(String relativePath) {
        try {
            return Files.readString(mainJava().resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path locate(String scope, String language) {
        Path base = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                base,
                base.resolve("app"),
                base.getParent() == null ? base : base.getParent());
        for (Path candidate : candidates) {
            Path source = candidate.resolve("src").resolve(scope).resolve(language);
            if (Files.isDirectory(source)) {
                return source;
            }
        }
        throw new IllegalStateException("не найден каталог src/" + scope + "/" + language + " от " + base);
    }
}
