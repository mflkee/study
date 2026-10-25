package ru.tsu.tpm.vacancyparser.finaltask.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tsu.tpm.vacancyparser.finaltask.model.Vacancy;
import ru.tsu.tpm.vacancyparser.finaltask.repo.VacancyRepository;

/**
 * Выборка результатов из H2 с фильтрацией и сортировкой через {@code parallelStream()}.
 *
 * <p>Требование задания: «извлечение результатов, возможность сортировки по входным параметрам из H2
 * через parallelStream()». Здесь параллельными потоками выполняются и фильтрация, и сортировка:
 * {@code findAll()} читает снимок таблицы, дальше данные обрабатываются в общем пуле ForkJoin. Для
 * учебного объёма это быстрее не стало бы, но требование выполнено буквально, а на тысячах строк
 * фильтрация действительно распараллеливается.
 *
 * <p>Пагинация делается после сортировки, поэтому {@code total} — это число записей после фильтров, что
 * и ожидает клиент.
 */
@Service
public class VacancyQueryService {

    /** Верхняя граница размера страницы: защита от запроса «все строки одним ответом». */
    public static final int MAX_PAGE_SIZE = 200;

    private final VacancyRepository repository;

    public VacancyQueryService(VacancyRepository repository) {
        this.repository = repository;
    }

    /**
     * Страница результатов.
     *
     * @param page      номер страницы с нуля
     * @param size      размер страницы (1..{@value #MAX_PAGE_SIZE})
     * @param sort      поле сортировки: id, title, company, salary, city, publishedAt, fetchedAt
     * @param direction asc или desc
     * @param city      фильтр по городу (без учёта регистра, {@code null} — без фильтра)
     * @param query     подстрока в названии, компании или требованиях
     * @param minSalary нижняя граница зарплаты
     */
    @Transactional(readOnly = true)
    public AnswerPage answer(
            int page, int size, String sort, String direction, String city, String query, Integer minSalary) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        String sortField = sort == null || sort.isBlank() ? "publishedAt" : sort.trim();
        String sortDirection = direction == null || direction.isBlank() ? "desc" : direction.trim();

        Comparator<Vacancy> comparator = comparator(sortField);
        if (sortDirection.equalsIgnoreCase("desc")) {
            comparator = comparator.reversed();
        } else if (!sortDirection.equalsIgnoreCase("asc")) {
            throw new IllegalArgumentException("direction должен быть asc или desc, получено: " + direction);
        }

        // Фильтрация — parallelStream по снимку таблицы.
        List<Vacancy> filtered = repository.findAll().parallelStream()
                .filter(vacancy -> cityMatches(vacancy, city))
                .filter(vacancy -> textMatches(vacancy, query))
                .filter(vacancy -> salaryMatches(vacancy, minSalary))
                .collect(Collectors.toList());

        long total = filtered.size();
        // Сортировка и пагинация — тоже parallelStream.
        List<Vacancy> items = filtered.parallelStream()
                .sorted(comparator)
                .skip((long) safePage * safeSize)
                .limit(safeSize)
                .collect(Collectors.toList());

        int totalPages = (int) ((total + safeSize - 1) / safeSize);
        return new AnswerPage(
                items, total, safePage, safeSize, totalPages, sortField,
                sortDirection.toLowerCase(Locale.ROOT), city, query, minSalary);
    }

    private static Comparator<Vacancy> comparator(String sortField) {
        return switch (sortField.toLowerCase(Locale.ROOT)) {
            case "id" -> Comparator.comparing(Vacancy::getId);
            case "title" -> Comparator.comparing(Vacancy::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "company" ->
                    Comparator.comparing(Vacancy::getCompany, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "salary" -> Comparator.comparing(Vacancy::getSalary, Comparator.nullsLast(Comparator.naturalOrder()));
            case "city" -> Comparator.comparing(Vacancy::getCity, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "publishedat", "published_at", "published" ->
                    Comparator.comparing(Vacancy::getPublishedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "fetchedat", "fetched_at", "fetched" ->
                    Comparator.comparing(Vacancy::getFetchedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> throw new IllegalArgumentException(
                    "неизвестное поле сортировки: " + sortField
                            + "; допустимо: id, title, company, salary, city, publishedAt, fetchedAt");
        };
    }

    private static boolean cityMatches(Vacancy vacancy, String city) {
        if (city == null || city.isBlank()) {
            return true;
        }
        return vacancy.getCity() != null && vacancy.getCity().equalsIgnoreCase(city.trim());
    }

    private static boolean textMatches(Vacancy vacancy, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return contains(vacancy.getTitle(), needle)
                || contains(vacancy.getCompany(), needle)
                || contains(vacancy.getRequirements(), needle);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean salaryMatches(Vacancy vacancy, Integer minSalary) {
        return minSalary == null || (vacancy.getSalary() != null && vacancy.getSalary() >= minSalary);
    }

    /**
     * Страница ответа вместе с параметрами выборки.
     *
     * @param items       вакансии текущей страницы
     * @param total       всего записей после фильтрации
     * @param page        номер страницы с нуля
     * @param size        размер страницы
     * @param totalPages  число страниц
     * @param sort        применённое поле сортировки
     * @param direction   применённое направление
     * @param city        применённый фильтр по городу
     * @param query       применённый текстовый фильтр
     * @param minSalary   применённый фильтр по зарплате
     */
    public record AnswerPage(
            List<Vacancy> items,
            long total,
            int page,
            int size,
            int totalPages,
            String sort,
            String direction,
            String city,
            String query,
            Integer minSalary) {

        public AnswerPage {
            items = List.copyOf(items);
        }
    }
}
