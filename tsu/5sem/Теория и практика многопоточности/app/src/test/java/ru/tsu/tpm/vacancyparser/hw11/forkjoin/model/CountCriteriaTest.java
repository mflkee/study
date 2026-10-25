package ru.tsu.tpm.vacancyparser.hw11.forkjoin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Критерий подсчёта: город, зарплата и слово в нормализованном заголовке.
 */
class CountCriteriaTest {

    private static AggregatedVacancy vacancy(String city, String title, int salary, boolean remote) {
        return new AggregatedVacancy("vac-1", city, title, salary, remote);
    }

    @Test
    @DisplayName("Все три условия должны выполняться одновременно")
    void matchesAllConditions() {
        CountCriteria criteria = new CountCriteria("Томск", 100_000, "java");

        assertThat(criteria.matches(vacancy("Томск", "Java-разработчик", 120_000, false))).isTrue();
        assertThat(criteria.matches(vacancy("Томск", "Java-разработчик", 90_000, false))).isFalse();
        assertThat(criteria.matches(vacancy("Омск", "Java-разработчик", 120_000, false))).isFalse();
        assertThat(criteria.matches(vacancy("Томск", "Аналитик данных", 120_000, false))).isFalse();
    }

    @Test
    @DisplayName("Слово ищется в нормализованном заголовке без учёта регистра и пунктуации")
    void keywordIsNormalized() {
        CountCriteria criteria = new CountCriteria(null, 0, "JAVA");

        assertThat(criteria.matches(vacancy("Томск", "JAVA-разработчик", 1, false))).isTrue();
        assertThat(criteria.matches(vacancy("Томск", "Backend-разработчик (Java)", 1, false))).isTrue();
        assertThat(criteria.matches(vacancy("Томск", "Java Script", 1, false))).isTrue();
        assertThat(criteria.matches(vacancy("Томск", "Python-разработчик", 1, false))).isFalse();
    }

    @Test
    @DisplayName("Пустые город и слово означают «не проверять»")
    void emptyConditionsAreIgnored() {
        CountCriteria criteria = new CountCriteria("  ", 0, "  ");

        assertThat(criteria.city()).isNull();
        assertThat(criteria.keyword()).isNull();
        assertThat(criteria.matches(vacancy("Омск", "Любая вакансия", 0, true))).isTrue();
        assertThat(criteria.label()).contains("любой").contains("не проверяется");
    }

    @Test
    @DisplayName("Отрицательная граница зарплаты отвергается")
    void negativeSalaryRejected() {
        assertThatThrownBy(() -> new CountCriteria("Томск", -1, "java"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
