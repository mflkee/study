package ru.tsu.tpm.vacancyparser.hw11.forkjoin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Агрегат подсчёта: сложение, нейтральный элемент, производные величины.
 */
class CountResultTest {

    @Test
    @DisplayName("Пустой агрегат — нейтральный элемент сложения")
    void emptyIsNeutral() {
        CountResult value = new CountResult(10, 4, 2, 500_000, 150_000);

        assertThat(value.plus(CountResult.empty())).isEqualTo(value);
        assertThat(CountResult.empty().plus(value)).isEqualTo(value);
        assertThat(CountResult.empty()).isEqualTo(new CountResult(0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("Сложение ассоциативно — порядок подзадач на ответ не влияет")
    void plusIsAssociative() {
        CountResult first = new CountResult(100, 10, 3, 1_000_000, 200_000);
        CountResult second = new CountResult(200, 20, 5, 2_000_000, 250_000);
        CountResult third = new CountResult(300, 30, 7, 3_000_000, 300_000);

        assertThat(first.plus(second).plus(third))
                .isEqualTo(first.plus(second.plus(third)))
                .isEqualTo(new CountResult(600, 60, 15, 6_000_000, 300_000));
    }

    @Test
    @DisplayName("Средняя зарплата считается по подошедшим, при нуле — ноль")
    void averageSalary() {
        assertThat(new CountResult(10, 2, 0, 300_000, 200_000).averageSalary()).isEqualTo(150_000.0);
        assertThat(new CountResult(10, 0, 0, 0, 0).averageSalary()).isZero();
    }

    @Test
    @DisplayName("Строка отчёта содержит все агрегаты")
    void reportLine() {
        String line = new CountResult(1_000, 25, 5, 2_500_000, 150_000).reportLine();

        assertThat(line)
                .contains("просмотрено 1000")
                .contains("подошло 25")
                .contains("удалённых 5")
                .contains("средняя 100000.0")
                .contains("максимум 150000");
    }

    @Test
    @DisplayName("Запись вакансии проверяет обязательные поля")
    void vacancyValidatesFields() {
        assertThatThrownBy(() -> new AggregatedVacancy("", "Томск", "Java", 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AggregatedVacancy("vac-1", " ", "Java", 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AggregatedVacancy("vac-1", "Томск", null, 1, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AggregatedVacancy("vac-1", "Томск", "Java", -1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
