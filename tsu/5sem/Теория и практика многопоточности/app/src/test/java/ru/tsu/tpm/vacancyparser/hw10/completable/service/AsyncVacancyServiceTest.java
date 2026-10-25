package ru.tsu.tpm.vacancyparser.hw10.completable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.CompanyInfo;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.FinalResult;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.VacancyDetails;
import ru.tsu.tpm.vacancyparser.hw10.completable.source.CompanyInfoSource;
import ru.tsu.tpm.vacancyparser.hw10.completable.source.VacancyDetailsSource;

/**
 * Конвейер ДЗ 10: параллельность, объединение двух источников, изоляция сбоев, остановка пула.
 *
 * <p>Источники здесь детерминированные: задержка задаётся параметром, «сломанные» идентификаторы —
 * множеством. Поэтому проверки не зависят от сети и от заглушки, а время измеряется с запасом.
 */
class AsyncVacancyServiceTest {

    private final List<AsyncVacancyService> created = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopServices() {
        created.forEach(AsyncVacancyService::close);
        created.clear();
    }

    @Test
    @Timeout(60)
    @DisplayName("fetchAllDataAsync возвращает все объекты в порядке входного списка")
    void returnsAllResultsInInputOrder() {
        List<String> ids = ids(8);
        AsyncVacancyService service = service(4, 20L, Set.of(), Set.of());

        List<FinalResult> results = service.fetchAllDataAsync(ids);

        assertThat(results).hasSize(8);
        assertThat(results).extracting(FinalResult::id).containsExactlyElementsOf(ids);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.normalizedTitle()).doesNotContain("  ");
            assertThat(result.attractiveness()).isPositive();
        });
    }

    @Test
    @Timeout(60)
    @DisplayName("Асинхронная версия заметно быстрее синхронной и использует свой пул")
    void asyncIsFasterThanSyncAndUsesOwnPool() {
        List<String> ids = ids(8);
        AsyncVacancyService service = service(4, 40L, Set.of(), Set.of());

        FetchOutcome async = service.runAsync(ids);
        FetchOutcome sync = service.runSync(ids);

        assertThat(sync.durationMillis()).isGreaterThan(0.0);
        assertThat(async.durationMillis())
                .as("асинхронно %.1f мс против синхронно %.1f мс",
                        async.durationMillis(), sync.durationMillis())
                .isLessThan(sync.durationMillis());
        assertThat(async.threadCount()).isGreaterThan(1);
        assertThat(async.threadsUsed()).allSatisfy(name ->
                assertThat(name).startsWith(AsyncVacancyService.THREAD_PREFIX));
        assertThat(async.threadsUsed()).noneMatch(name -> name.contains("commonPool"));
        assertThat(sync.threadsUsed()).containsExactly(Thread.currentThread().getName());
        assertThat(async.sourceCalls()).isEqualTo(16);
    }

    @Test
    @Timeout(60)
    @DisplayName("Сбой источника вакансий исключает только свою запись, остальные остаются")
    void detailsFailureIsIsolated() {
        List<String> ids = ids(6);
        Set<String> failing = Set.of("v-03");
        AsyncVacancyService service = service(4, 10L, failing, Set.of());

        FetchOutcome outcome = service.runAsync(ids);

        assertThat(outcome.results()).hasSize(5);
        assertThat(outcome.results()).extracting(FinalResult::id).doesNotContain("v-03");
        assertThat(outcome.failures()).hasSize(1);
        assertThat(outcome.failures().get(0).id()).isEqualTo("v-03");
        assertThat(outcome.failures().get(0).message()).contains("источник недоступен");
    }

    @Test
    @Timeout(60)
    @DisplayName("Сбой справочника компаний даёт заглушку, но не выбрасывает вакансию")
    void companyFailureProducesFallback() {
        List<String> ids = ids(5);
        AsyncVacancyService service = service(4, 10L, Set.of(), Set.of("v-02"));

        FetchOutcome outcome = service.runAsync(ids);

        assertThat(outcome.results()).hasSize(5);
        assertThat(outcome.failures()).isEmpty();
        FinalResult affected = outcome.results().stream()
                .filter(result -> result.id().equals("v-02"))
                .findFirst()
                .orElseThrow();
        assertThat(affected.companyFallback()).isTrue();
        assertThat(affected.company().name()).isEqualTo(CompanyInfo.UNKNOWN_NAME);
        assertThat(affected.company().rating()).isZero();
        assertThat(outcome.results())
                .filteredOn(result -> !result.id().equals("v-02"))
                .allSatisfy(result -> assertThat(result.companyFallback()).isFalse());
    }

    @Test
    @Timeout(60)
    @DisplayName("Сбой всех источников вакансий даёт пустой результат без исключения")
    void allDetailsFailingReturnsEmptyList() {
        List<String> ids = ids(4);
        AsyncVacancyService service = service(3, 5L, new HashSet<>(ids), Set.of());

        FetchOutcome outcome = service.runAsync(ids);

        assertThat(outcome.results()).isEmpty();
        assertThat(outcome.failures()).hasSize(4);
        assertThat(service.fetchAllDataAsync(ids)).isEmpty();
    }

    @Test
    @Timeout(60)
    @DisplayName("Пустой список — пустой результат, без обращения к источникам")
    void emptyIdsProduceEmptyOutcome() {
        AsyncVacancyService service = service(2, 0L, Set.of(), Set.of());

        FetchOutcome outcome = service.runAsync(List.of());

        assertThat(outcome.results()).isEmpty();
        assertThat(outcome.failures()).isEmpty();
        assertThat(outcome.sourceCalls()).isZero();
    }

    @Test
    @Timeout(60)
    @DisplayName("Потоки пула — именованные демоны, это видно из самих задач")
    void poolThreadsAreNamedDaemons() {
        Set<String> names = ConcurrentHashMap.newKeySet();
        List<Boolean> daemonFlags = new CopyOnWriteArrayList<>();
        VacancyDetailsSource details = id -> {
            names.add(Thread.currentThread().getName());
            daemonFlags.add(Thread.currentThread().isDaemon());
            return new VacancyDetails(id, "Java", "Томск", 100_000, 150_000);
        };
        CompanyInfoSource companies = id -> {
            names.add(Thread.currentThread().getName());
            daemonFlags.add(Thread.currentThread().isDaemon());
            return new CompanyInfo(id, "ООО Тест", 4.0, 2, false);
        };
        AsyncVacancyService service = new AsyncVacancyService(details, companies, 3, 0L, 2_000L, null);
        created.add(service);

        service.runAsync(ids(6));

        assertThat(names).isNotEmpty().allSatisfy(name ->
                assertThat(name).startsWith(AsyncVacancyService.THREAD_PREFIX));
        assertThat(daemonFlags).containsOnly(true);
    }

    @Test
    @Timeout(60)
    @DisplayName("Журнал содержит имя потока и идентификатор запроса")
    void traceContainsThreadAndRequestId() {
        AsyncVacancyService service = service(3, 0L, Set.of("v-02"), Set.of());

        service.runAsync(ids(3));

        String journal = String.join("\n", service.traceLines());
        assertThat(service.traceLines()).isNotEmpty();
        assertThat(journal)
                .contains(AsyncVacancyService.THREAD_PREFIX)
                .contains("v-01")
                .contains("v-02")
                .contains("сбой источника вакансий");
        service.clearTrace();
        assertThat(service.traceLines()).isEmpty();
    }

    @Test
    @Timeout(60)
    @DisplayName("close() останавливает пул, повторный вызов безопасен")
    void closeStopsExecutor() {
        AsyncVacancyService service = service(4, 0L, Set.of(), Set.of());
        service.runAsync(ids(4));

        service.close();

        assertThat(service.isShutdown()).isTrue();
        assertThat(service.isTerminated()).isTrue();
        assertThat(ThreadDump.liveThreadsWithPrefixes(AsyncVacancyService.THREAD_PREFIX)).isEmpty();
        service.close();
    }

    @Test
    @Timeout(60)
    @DisplayName("Некорректные параметры отвергаются сразу")
    void invalidArgumentsRejected() {
        VacancyDetailsSource details = id -> new VacancyDetails(id, "Java", "Томск", 1, 2);
        CompanyInfoSource companies = id -> new CompanyInfo(id, "Тест", 4.0, 1, false);

        assertThatThrownBy(() -> new AsyncVacancyService(null, companies, 2, 0L, 100L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsyncVacancyService(details, null, 2, 0L, 100L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsyncVacancyService(details, companies, 0, 0L, 100L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsyncVacancyService(details, companies, 2, -1L, 100L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsyncVacancyService(details, companies, 2, 0L, 0L, null))
                .isInstanceOf(IllegalArgumentException.class);

        AsyncVacancyService service = new AsyncVacancyService(details, companies, 2, 0L, 500L, null);
        created.add(service);
        assertThatThrownBy(() -> service.runAsync(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.runAsync(java.util.Arrays.asList("v-01", " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AsyncVacancyService service(
            int poolSize, long latencyMillis, Set<String> failingDetails, Set<String> failingCompanies) {
        VacancyDetailsSource details = id -> {
            sleep(latencyMillis);
            if (failingDetails.contains(id)) {
                throw new IOException("источник недоступен: " + id);
            }
            return new VacancyDetails(
                    id,
                    "  Java-разработчик  (" + id.toUpperCase(Locale.ROOT) + ") ",
                    "Томск",
                    100_000,
                    150_000);
        };
        CompanyInfoSource companies = id -> {
            sleep(latencyMillis);
            if (failingCompanies.contains(id)) {
                throw new IOException("справочник недоступен: " + id);
            }
            return new CompanyInfo(id, "ООО «Тест-" + id + "»", 4.2, 3, false);
        };
        AsyncVacancyService service = new AsyncVacancyService(details, companies, poolSize, 0L, 2_000L, null);
        created.add(service);
        return service;
    }

    private static List<String> ids(int count) {
        List<String> ids = new ArrayList<>(count);
        IntStream.rangeClosed(1, count).forEach(index ->
                ids.add(String.format(Locale.ROOT, "v-%02d", index)));
        return ids;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
