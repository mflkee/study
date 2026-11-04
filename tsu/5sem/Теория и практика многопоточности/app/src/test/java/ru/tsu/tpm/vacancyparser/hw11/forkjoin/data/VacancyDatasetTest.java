package ru.tsu.tpm.vacancyparser.hw11.forkjoin.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.AggregatedVacancy;

/**
 * Генератор набора: детерминированность, размер, диапазоны значений.
 */
class VacancyDatasetTest {

    @Test
    @DisplayName("Одно зерно — одинаковый набор, другое зерно — другой набор")
    void deterministicPerSeed() {
        List<AggregatedVacancy> first = VacancyDataset.generate(500, 2026L);
        List<AggregatedVacancy> same = VacancyDataset.generate(500, 2026L);
        List<AggregatedVacancy> other = VacancyDataset.generate(500, 2027L);

        assertThat(first).isEqualTo(same).hasSize(500);
        assertThat(first).isNotEqualTo(other);
    }

    @Test
    @DisplayName("Значения попадают в заявленные диапазоны")
    void valuesAreWithinRanges() {
        List<AggregatedVacancy> data = VacancyDataset.generate(2_000, 1L);

        assertThat(data).allSatisfy(vacancy -> {
            assertThat(vacancy.id()).startsWith("vac-");
            assertThat(VacancyDataset.CITIES).contains(vacancy.city());
            assertThat(VacancyDataset.TITLES).contains(vacancy.title());
            assertThat(vacancy.salary()).isBetween(40_000, 250_000);
        });
        assertThat(data.stream().filter(AggregatedVacancy::remote).count())
                .isPositive()
                .isLessThan(data.size());
    }

    @Test
    @DisplayName("Пустой набор допустим, отрицательный размер отвергается")
    void sizeValidation() {
        assertThat(VacancyDataset.generate(0, 1L)).isEmpty();
        assertThatThrownBy(() -> VacancyDataset.generate(-1, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
