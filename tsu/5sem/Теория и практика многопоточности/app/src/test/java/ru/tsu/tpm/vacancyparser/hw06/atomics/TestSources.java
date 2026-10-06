package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Поиск каталога исходников из теста.
 *
 * <p>Тесты сборки запускаются с рабочим каталогом {@code app/}, но модуль может собираться и из корня
 * курса. Помощник пробует оба варианта, чтобы проверки по исходникам не зависели от того, откуда
 * запущен Maven.
 */
final class TestSources {

    private TestSources() {
    }

    /** Каталог {@code src/main/java}. */
    static Path mainJava() {
        return locate("main", "java");
    }

    /** Каталог {@code src/test/java}. */
    static Path testJava() {
        return locate("test", "java");
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
