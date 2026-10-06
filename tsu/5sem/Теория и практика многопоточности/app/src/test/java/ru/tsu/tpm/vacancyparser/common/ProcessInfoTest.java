package ru.tsu.tpm.vacancyparser.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Хелпер PID: диагностика должна знать номер процесса без ручного поиска.
 */
class ProcessInfoTest {

    @Test
    @DisplayName("PID положителен и совпадает с PID текущего процесса")
    void pidMatchesProcessHandle() {
        assertThat(ProcessInfo.currentPid())
                .isPositive()
                .isEqualTo(ProcessHandle.current().pid());
    }

    @Test
    @DisplayName("Строка PID содержит префикс и фактический номер")
    void pidLineMentionsPid() {
        assertThat(ProcessInfo.pidLine())
                .contains("pid")
                .contains(String.valueOf(ProcessInfo.currentPid()));
    }
}
