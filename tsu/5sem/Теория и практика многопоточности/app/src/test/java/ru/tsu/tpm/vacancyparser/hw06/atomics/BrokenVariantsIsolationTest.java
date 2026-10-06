package ru.tsu.tpm.vacancyparser.hw06.atomics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Изоляция учебных неверных вариантов.
 *
 * <p>Неверные варианты («{@code volatile}-счётчик», check-then-act кэш, признак без {@code volatile})
 * существуют в коде намеренно, но обязаны оставаться вне рабочего пути: их нельзя случайно взять в
 * рабочее решение. Проверка статическая — по исходникам: вне пакета {@code hw06.atomics} ни одна
 * ссылка на них не допускается, и каждый неверный класс помечен комментарием об учебном характере.
 */
class BrokenVariantsIsolationTest {

    private static final String PACKAGE_MARKER = "/hw06/atomics/";
    private static final List<String> BROKEN_SYMBOLS = List.of(
            "VolatileCounterBroken", "BrokenSingletonCache", "NonVolatileFlagWorker");

    @Test
    @DisplayName("Ни один файл вне пакета hw06.atomics не ссылается на неверные варианты")
    void brokenVariantsAreNotReferencedOutsideTheirPackage() throws IOException {
        Path mainJava = TestSources.mainJava();
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(mainJava)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains(PACKAGE_MARKER))
                    .filter(this::mentionsBrokenVariant)
                    .toList();
        }

        assertThat(offenders)
                .as("учебные дефектные классы не должны покидать свой пакет: %s", offenders)
                .isEmpty();
    }

    @Test
    @DisplayName("Каждый неверный класс помечен как учебный неверный вариант")
    void brokenClassesAreMarked() throws IOException {
        Path packageDir = TestSources.mainJava().resolve("ru/tsu/tpm/vacancyparser/hw06/atomics");
        for (String simpleName : BROKEN_SYMBOLS) {
            Path source = packageDir.resolve(simpleName + ".java");
            assertThat(source).as("исходник %s обязан существовать", simpleName).exists();
            assertThat(Files.readString(source))
                    .as("класс %s обязан быть явно помечен как учебный неверный вариант", simpleName)
                    .contains("Учебный неверный вариант");
        }
    }

    private boolean mentionsBrokenVariant(Path path) {
        try {
            String code = Files.readString(path);
            return BROKEN_SYMBOLS.stream().anyMatch(code::contains);
        } catch (IOException e) {
            throw new IllegalStateException("не удалось прочитать " + path, e);
        }
    }
}
