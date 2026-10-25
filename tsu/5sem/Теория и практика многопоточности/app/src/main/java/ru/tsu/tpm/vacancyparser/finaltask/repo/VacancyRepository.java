package ru.tsu.tpm.vacancyparser.finaltask.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.tsu.tpm.vacancyparser.finaltask.model.Vacancy;

/**
 * Репозиторий вакансий (Spring Data JPA).
 *
 * <p>Ключевой для дедупликации метод — {@link #findByUrlIn(Collection)}: один запрос на весь пакет
 * разобранных страниц вместо запроса на каждую. Остальное даёт {@link JpaRepository}: {@code saveAll},
 * {@code findAll}, {@code count}, {@code deleteAll}.
 */
public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    /** Найти вакансию по адресу страницы. */
    Optional<Vacancy> findByUrl(String url);

    /** Найти все вакансии с адресами из набора — для пакетного upsert. */
    List<Vacancy> findByUrlIn(Collection<String> urls);

    /**
     * Удалить записи предыдущих запусков демонстрационного источника.
     *
     * <p>Заглушка при каждом старте занимает новый свободный порт, поэтому её адреса меняются, и без
     * очистки в H2 копились бы дубликаты одной и той же вакансии с разными портами. Удаляются только
     * адреса локальной заглушки; данные, присланные проверяющим (в том числе {@code offline://...}),
     * не затрагиваются.
     */
    @Modifying
    @Query("delete from Vacancy v where v.url like 'http://127.0.0.1:%/vacancies/%'")
    int deleteStaleDemoVacancies();
}
