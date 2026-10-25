package ru.tsu.tpm.vacancyparser.hw12.collections.ranking;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import ru.tsu.tpm.vacancyparser.hw12.collections.model.VacancyCard;

/**
 * Рейтинг вакансий по зарплате на {@link ConcurrentSkipListMap}.
 *
 * <p>Нужен, чтобы показать ещё один класс из {@code java.util.concurrent}, а не только
 * {@code ConcurrentHashMap}. Сортированная конкурентная карта даёт «топ-N без сортировки на чтении»:
 * {@link ConcurrentSkipListMap#descendingMap()} отдаёт ключи от большего к меньшему, и первые N записей
 * берутся за O(N), тогда как пересортировка всего списка заняла бы O(M log M).
 *
 * <p>Значения — {@link CopyOnWriteArrayList}: зарплата одна и та же у многих вакансий, и список
 * «кто на этой зарплате» читается при выводе чаще, чем меняется. Повторное добавление одного
 * идентификатора не создаёт дубликат.
 */
public final class SalaryRanking {

    private final ConcurrentSkipListMap<Integer, CopyOnWriteArrayList<String>> bySalary =
            new ConcurrentSkipListMap<>();

    /** Добавить вакансию в рейтинг. */
    public void add(VacancyCard card) {
        if (card == null) {
            throw new IllegalArgumentException("карточка не задана");
        }
        bySalary.computeIfAbsent(card.salary(), salary -> new CopyOnWriteArrayList<>())
                .addIfAbsent(card.id());
    }

    /** Убрать вакансию из рейтинга. */
    public boolean remove(VacancyCard card) {
        if (card == null) {
            return false;
        }
        boolean[] removed = {false};
        bySalary.computeIfPresent(card.salary(), (salary, ids) -> {
            removed[0] = ids.remove(card.id());
            return ids.isEmpty() ? null : ids;
        });
        return removed[0];
    }

    /** Идентификаторы топ-N вакансий по убыванию зарплаты. */
    public List<String> topIds(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("предел не может быть отрицательным");
        }
        List<String> ids = new ArrayList<>(limit);
        for (CopyOnWriteArrayList<String> group : bySalary.descendingMap().values()) {
            for (String id : group) {
                if (ids.size() >= limit) {
                    return ids;
                }
                ids.add(id);
            }
        }
        return ids;
    }

    /** Самая высокая зарплата в рейтинге или {@code null}, если он пуст. */
    public Integer highestSalary() {
        return bySalary.isEmpty() ? null : bySalary.lastKey();
    }

    /** Сколько вакансий в рейтинге. */
    public int size() {
        return bySalary.values().stream().mapToInt(List::size).sum();
    }

    /** Строки «зарплата — вакансии» в порядке убывания, не больше {@code limit} групп. */
    public List<String> topGroups(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("предел не может быть отрицательным");
        }
        List<String> lines = new ArrayList<>();
        for (var entry : bySalary.descendingMap().entrySet()) {
            if (lines.size() >= limit) {
                break;
            }
            lines.add(entry.getKey() + " ₽: " + String.join(", ", entry.getValue()));
        }
        return lines;
    }
}
