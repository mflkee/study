package ru.tsu.tpm.vacancyparser.hw12.collections.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.hw12.collections.benchmark.ConcurrentBenchmarks;
import ru.tsu.tpm.vacancyparser.hw12.collections.cache.CacheStats;
import ru.tsu.tpm.vacancyparser.hw12.collections.events.EventType;
import ru.tsu.tpm.vacancyparser.hw12.collections.model.VacancyCard;
import ru.tsu.tpm.vacancyparser.hw12.collections.ranking.SalaryRanking;

/**
 * Строки отчёта ДЗ 12: сравнения замеров, счётчики событий, топ рейтинга.
 */
class ConcurrentReportTest {

    @Test
    @DisplayName("Сравнение карт показывает, во сколько раз конкурентная быстрее")
    void mapComparison() {
        ConcurrentBenchmarks.MapRun concurrent =
                new ConcurrentBenchmarks.MapRun("ConcurrentHashMap", 8, 50_000, 100_000_000L, 1_000);
        ConcurrentBenchmarks.MapRun synchronizedMap =
                new ConcurrentBenchmarks.MapRun("synchronizedMap(HashMap)", 8, 50_000, 400_000_000L, 1_000);

        assertThat(ConcurrentReport.mapSpeedup(concurrent, synchronizedMap)).isEqualTo(4.0);
        assertThat(ConcurrentReport.mapComparison(concurrent, synchronizedMap))
                .contains("ConcurrentHashMap быстрее synchronizedMap(HashMap) в 4.00×")
                .contains("оп/с");
        assertThat(ConcurrentReport.mapComparison(null, synchronizedMap))
                .contains("сравнение карт недоступно");
    }

    @Test
    @DisplayName("Сравнение счётчиков учитывает корректность обоих")
    void counterComparison() {
        ConcurrentBenchmarks.CounterRun adder =
                new ConcurrentBenchmarks.CounterRun("LongAdder", 100_000, 100_000, 50_000_000L);
        ConcurrentBenchmarks.CounterRun atomic =
                new ConcurrentBenchmarks.CounterRun("AtomicLong", 100_000, 100_000, 100_000_000L);

        assertThat(ConcurrentReport.counterComparison(adder, atomic))
                .contains("LongAdder быстрее AtomicLong в 2.00×")
                .contains("оба без потерь: да");

        ConcurrentBenchmarks.CounterRun broken =
                new ConcurrentBenchmarks.CounterRun("LongAdder", 100_000, 99_999, 50_000_000L);
        assertThat(ConcurrentReport.counterComparison(broken, atomic)).contains("оба без потерь: НЕТ");
        assertThat(ConcurrentReport.counterComparison(null, atomic))
                .contains("сравнение счётчиков недоступно");
    }

    @Test
    @DisplayName("Сводка событий считает потери как разницу опубликованных и доставленных")
    void eventSummary() {
        assertThat(ConcurrentReport.eventSummary(100, 98, 2))
                .contains("опубликовано 100")
                .contains("доставлено 98")
                .contains("отклонено 2")
                .contains("потеряно 2");
        assertThat(ConcurrentReport.eventSummary(10, 10, 0)).contains("потеряно 0");
    }

    @Test
    @DisplayName("Счётчики по типам пропускают нулевые значения")
    void eventCounts() {
        Map<EventType, Long> counts = new LinkedHashMap<>();
        counts.put(EventType.CACHE_HIT, 5L);
        counts.put(EventType.CACHE_MISS, 0L);
        counts.put(EventType.CACHE_LOAD, 2L);

        assertThat(ConcurrentReport.eventCountLines(counts))
                .containsExactly("попадание: 5", "загрузка: 2");
        assertThat(ConcurrentReport.eventCountLines(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("Топ рейтинга выводится группами и списком идентификаторов")
    void rankingLines() {
        SalaryRanking ranking = new SalaryRanking();
        ranking.add(new VacancyCard("a", "Java", "Томск", 100_000, 4.0));
        ranking.add(new VacancyCard("b", "Java", "Томск", 200_000, 4.0));

        assertThat(ConcurrentReport.rankingLines(ranking, 2))
                .containsExactly("200000 ₽: b", "100000 ₽: a", "идентификаторы топа: [b, a]");
        assertThat(ConcurrentReport.rankingLines(ranking, 0)).isEmpty();
        assertThat(ConcurrentReport.rankingLines(null, 5)).isEmpty();
    }

    @Test
    @DisplayName("Строка кэша совпадает со строкой счётчиков")
    void cacheLine() {
        CacheStats stats = new CacheStats(9, 1, 1, 0, 0, 5);
        assertThat(ConcurrentReport.cacheLine(stats)).isEqualTo(stats.reportLine());
    }
}
