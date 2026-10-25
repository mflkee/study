package ru.tsu.tpm.vacancyparser.hw12.collections.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw12.collections.model.VacancyCard;

/**
 * Рейтинг на ConcurrentSkipListMap: порядок по зарплате, отсутствие дубликатов, удаление.
 */
class SalaryRankingTest {

    private static VacancyCard card(String id, int salary) {
        return new VacancyCard(id, "Вакансия " + id, "Томск", salary, 4.0);
    }

    @Test
    @DisplayName("Топ идёт по убыванию зарплаты, одинаковые зарплаты группируются")
    void topIsOrderedBySalary() {
        SalaryRanking ranking = new SalaryRanking();
        ranking.add(card("a", 100_000));
        ranking.add(card("b", 250_000));
        ranking.add(card("c", 150_000));
        ranking.add(card("d", 250_000));

        assertThat(ranking.topIds(3)).containsExactly("b", "d", "c");
        assertThat(ranking.highestSalary()).isEqualTo(250_000);
        assertThat(ranking.size()).isEqualTo(4);
        assertThat(ranking.topGroups(2)).containsExactly(
                "250000 ₽: b, d",
                "150000 ₽: c");
    }

    @Test
    @DisplayName("Повторное добавление не создаёт дубликат")
    void noDuplicates() {
        SalaryRanking ranking = new SalaryRanking();
        ranking.add(card("a", 100_000));
        ranking.add(card("a", 100_000));

        assertThat(ranking.size()).isEqualTo(1);
        assertThat(ranking.topIds(5)).containsExactly("a");
    }

    @Test
    @DisplayName("Удаление убирает вакансию и пустую группу")
    void remove() {
        SalaryRanking ranking = new SalaryRanking();
        ranking.add(card("a", 100_000));
        ranking.add(card("b", 200_000));

        assertThat(ranking.remove(card("a", 100_000))).isTrue();
        assertThat(ranking.remove(card("a", 100_000))).isFalse();
        assertThat(ranking.remove(null)).isFalse();
        assertThat(ranking.size()).isEqualTo(1);
        assertThat(ranking.topIds(5)).containsExactly("b");
    }

    @Test
    @DisplayName("Пустой рейтинг и нулевой предел обрабатываются")
    void emptyRanking() {
        SalaryRanking ranking = new SalaryRanking();

        assertThat(ranking.size()).isZero();
        assertThat(ranking.highestSalary()).isNull();
        assertThat(ranking.topIds(5)).isEmpty();
        assertThat(ranking.topGroups(5)).isEmpty();

        ranking.add(card("a", 100_000));
        assertThat(ranking.topIds(0)).isEmpty();
        assertThatThrownBy(() -> ranking.topIds(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ranking.topGroups(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ranking.add(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
