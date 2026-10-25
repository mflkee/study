package ru.tsu.tpm.vacancyparser.finaltask.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tsu.tpm.vacancyparser.finaltask.model.ParsedVacancy;
import ru.tsu.tpm.vacancyparser.finaltask.model.Vacancy;
import ru.tsu.tpm.vacancyparser.finaltask.repo.VacancyRepository;

/**
 * Сохранение разобранных страниц в H2 (upsert по адресу).
 *
 * <p>Запись выполняется одним потоком после параллельного разбора: JPA-сессия и соединение с H2 не
 * предназначены для одновременного использования из нескольких потоков, а узкое место здесь — сеть и
 * разбор HTML, а не вставка. Пакетная схема — один {@code findByUrlIn} и один {@code saveAll} на обход,
 * а не запрос на каждую страницу.
 *
 * <p>Повторный обход того же адреса обновляет запись ({@code updated}), а не создаёт дубликат — это и
 * есть дедупликация по URL.
 */
@Service
public class VacancyStorageService {

    private final VacancyRepository repository;

    public VacancyStorageService(VacancyRepository repository) {
        this.repository = repository;
    }

    /**
     * Вставить новые и обновить существующие записи.
     *
     * @param pages разобранные страницы (порядок не важен, дубликаты URL схлопываются)
     * @return сколько записей добавлено и сколько обновлено
     */
    @Transactional
    public StorageResult upsertAll(List<ParsedVacancy> pages) {
        if (pages == null || pages.isEmpty()) {
            return new StorageResult(0, 0);
        }
        Map<String, ParsedVacancy> unique = new LinkedHashMap<>();
        for (ParsedVacancy page : pages) {
            if (page != null) {
                unique.put(page.url(), page);
            }
        }
        if (unique.isEmpty()) {
            return new StorageResult(0, 0);
        }

        Map<String, Vacancy> existing = repository.findByUrlIn(unique.keySet()).stream()
                .collect(Collectors.toMap(Vacancy::getUrl, Function.identity(), (left, right) -> left));

        int saved = 0;
        int updated = 0;
        List<Vacancy> batch = new ArrayList<>(unique.size());
        for (ParsedVacancy page : unique.values()) {
            Vacancy vacancy = existing.get(page.url());
            if (vacancy == null) {
                vacancy = new Vacancy(page);
                saved++;
            } else {
                vacancy.apply(page);
                updated++;
            }
            batch.add(vacancy);
        }
        repository.saveAll(batch);
        return new StorageResult(saved, updated);
    }

    /** Удалить все вакансии (для повторной проверки демонстрации). */
    @Transactional
    public long deleteAll() {
        long count = repository.count();
        repository.deleteAll();
        return count;
    }

    /**
     * Удалить записи прошлых запусков локальной заглушки (у неё каждый раз новый порт).
     *
     * @return сколько записей удалено
     */
    @Transactional
    public int deleteStaleDemoVacancies() {
        return repository.deleteStaleDemoVacancies();
    }

    /** Сколько вакансий хранится в H2. */
    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    /**
     * Итог записи пакета.
     *
     * @param saved   добавлено новых записей
     * @param updated обновлено существующих
     */
    public record StorageResult(int saved, int updated) {

        /** Всего записей затронуто. */
        public int total() {
            return saved + updated;
        }
    }
}
