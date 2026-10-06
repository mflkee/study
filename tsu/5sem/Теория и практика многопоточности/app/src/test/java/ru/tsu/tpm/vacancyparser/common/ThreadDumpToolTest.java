package ru.tsu.tpm.vacancyparser.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Снятие дампа потоков средствами JDK.
 *
 * <p>Проверки, требующие живого {@code jstack}/{@code jcmd}, пропускаются, если инструмента нет в
 * окружении: сам разбор дампа тестируется отдельно на фикстурах и от утилит не зависит.
 */
class ThreadDumpToolTest {

    @TempDir
    Path tempDir;

    @Test
    @Timeout(60)
    @DisplayName("Для живого процесса снимается непустой дамп с заголовком и сохраняется в файл")
    void captureReturnsDumpForLiveProcess() throws Exception {
        ThreadDumpTool.CapturedDump dump = ThreadDumpTool.capture(
                ProcessInfo.currentPid(), "test", "unit", tempDir, ThreadDumpTool.ToolLocator.standard());

        assumeTrue(dump.captured(), "jstack/jcmd недоступны в окружении: " + dump.message());

        assertThat(dump.text()).contains(ThreadDumpTool.DUMP_HEADER);
        assertThat(dump.file()).exists();
        assertThat(dump.file().toString()).contains("hw07-test-unit").endsWith(".jstack");
        assertThat(dump.tool()).isNotBlank();
    }

    @Test
    @DisplayName("При недоступности инструментов возвращается пустой результат без исключения")
    void missingToolReturnsEmptyWithoutException() {
        ThreadDumpTool.CapturedDump dump = ThreadDumpTool.capture(
                ProcessInfo.currentPid(), "test", "notool", tempDir, name -> null);

        assertThat(dump.captured()).isFalse();
        assertThat(dump.text()).isEmpty();
        assertThat(dump.file()).isNull();
        assertThat(dump.message()).isNotBlank();
        assertThat(Files.exists(tempDir.resolve("hw07-test-notool"))).isFalse();
    }
}
