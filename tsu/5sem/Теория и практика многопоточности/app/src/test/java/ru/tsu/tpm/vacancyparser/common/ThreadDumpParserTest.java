package ru.tsu.tpm.vacancyparser.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Разбор дампа на фикстурах: реальный фрагмент со взаимной блокировкой и чистый дамп без неё.
 *
 * <p>Фикстуры — настоящие дампы {@code jstack} с этой машины, а не выдуманный текст: разбор обязан
 * работать на том, что печатает JVM. Разбор не зависит от {@code jstack}, поэтому тесты идут всегда.
 */
class ThreadDumpParserTest {

    private static String fixture(String name) throws IOException {
        try (InputStream stream = ThreadDumpParserTest.class.getResourceAsStream("/hw07/" + name)) {
            assertThat(stream).as("фикстура %s обязана быть в тестовых ресурсах", name).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Реальный дамп со взаимной блокировкой: секция, имена потоков и ресурсы")
    void parsesRealDeadlockDump() throws IOException {
        ThreadDumpParser.DeadlockReport report = ThreadDumpParser.parse(fixture("deadlock-jstack.txt"));

        assertThat(report.detected()).isTrue();
        assertThat(report.rawSection()).contains("Found one Java-level deadlock");
        assertThat(report.threadNames()).containsExactlyInAnyOrder("Deadlock-A", "Deadlock-B");
        assertThat(report.waits()).hasSize(2);
        for (ThreadDumpParser.Wait wait : report.waits()) {
            assertThat(wait.resource()).contains("ownable synchronizer");
            assertThat(wait.heldBy()).isIn("Deadlock-A", "Deadlock-B");
            assertThat(wait.heldBy())
                    .as("поток ждёт ресурс, который удерживает другой поток цикла")
                    .isNotEqualTo(wait.thread());
        }
        assertThat(report.summary()).contains("обнаружена").contains("Deadlock-A").contains("Deadlock-B");
    }

    @Test
    @DisplayName("Чистый дамп: взаимная блокировка не обнаружена, потоков цикла нет")
    void cleanDumpHasNoDeadlock() throws IOException {
        ThreadDumpParser.DeadlockReport report = ThreadDumpParser.parse(fixture("clean-jstack.txt"));

        assertThat(report.detected()).isFalse();
        assertThat(report.threadNames()).isEmpty();
        assertThat(report.waits()).isEmpty();
        assertThat(report.rawSection()).isEmpty();
        assertThat(report.summary()).contains("не обнаружена");
    }

    @Test
    @DisplayName("Разбор устойчив к порядку потоков, адресам и времени запуска")
    void parseIsStableAcrossFormatting() throws IOException {
        String real = fixture("deadlock-jstack.txt");
        ThreadDumpParser.DeadlockReport first = ThreadDumpParser.parse(real);

        // Тот же смысл, но другой порядок потоков, другие адреса, добавленная строка времени.
        String reordered = "2026-10-05 23:59:59"
                + System.lineSeparator()
                + "Full thread dump OpenJDK 64-Bit Server VM (21 mixed mode):"
                + System.lineSeparator()
                + "Found one Java-level deadlock:"
                + System.lineSeparator()
                + "============================="
                + System.lineSeparator()
                + "\"Deadlock-B\":"
                + System.lineSeparator()
                + "  waiting for ownable synchronizer 0x0000000000000002, (a java.util.concurrent.locks.ReentrantLock$NonfairSync),"
                + System.lineSeparator()
                + "  which is held by \"Deadlock-A\""
                + System.lineSeparator()
                + "\"Deadlock-A\":"
                + System.lineSeparator()
                + "  waiting for ownable synchronizer 0x0000000000000001, (a java.util.concurrent.locks.ReentrantLock$NonfairSync),"
                + System.lineSeparator()
                + "  which is held by \"Deadlock-B\""
                + System.lineSeparator()
                + "Found 1 deadlock."
                + System.lineSeparator();
        ThreadDumpParser.DeadlockReport second = ThreadDumpParser.parse(reordered);

        assertThat(second.detected()).isTrue();
        assertThat(Set.copyOf(second.threadNames())).isEqualTo(Set.copyOf(first.threadNames()));
        assertThat(second.waits()).hasSize(first.waits().size());
    }

    @Test
    @DisplayName("Дубликат секции в большом дампе всё равно распознаётся")
    void sectionAmongOtherThreadsIsFound() throws IOException {
        String combined = fixture("clean-jstack.txt") + System.lineSeparator() + fixture("deadlock-jstack.txt");

        ThreadDumpParser.DeadlockReport report = ThreadDumpParser.parse(combined);

        assertThat(report.detected()).isTrue();
        assertThat(report.threadNames()).containsExactlyInAnyOrder("Deadlock-A", "Deadlock-B");
    }
}
