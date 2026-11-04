package ru.tsu.tpm.vacancyparser.hw11.forkjoin.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.AggregatedVacancy;

/**
 * Детерминированный генератор накопленных агрегированных вакансий.
 *
 * <p>Данные соответствуют тому, что парсер (ДЗ 8–10) уже собрал и сохранил: города, заголовки,
 * зарплаты, признак удалённой работы. Генератор с зерном нужен, чтобы замеры ДЗ 11 были
 * воспроизводимы: одинаковое зерно — одинаковый набор и одинаковый ответ счётчика.
 *
 * <p>Набор намеренно большой: на десятках записей рекурсивное разбиение только добавляет накладные
 * расходы, и сравнивать параллельный подсчёт с последовательным не имеет смысла.
 */
public final class VacancyDataset {

    /** Города, встречающиеся в наборе. */
    public static final String[] CITIES = {
            "Томск", "Новосибирск", "Москва", "Санкт-Петербург", "Екатеринбург",
            "Казань", "Омск", "Тюмень", "Красноярск", "Удалённо"
    };

    /** Заголовки вакансий в наборе. */
    public static final String[] TITLES = {
            "Java-разработчик",
            "Backend-разработчик (Java)",
            "Разработчик Spring Boot",
            "Инженер по тестированию",
            "Аналитик данных",
            "Team Lead Java",
            "QA-инженер автоматизации",
            "DevOps-инженер",
            "Специалист по сопровождению",
            "Python-разработчик",
            "Системный администратор",
            "Продуктовый аналитик"
    };

    private VacancyDataset() {
    }

    /**
     * Сгенерировать набор записей.
     *
     * @param size количество записей
     * @param seed зерно генератора
     */
    public static List<AggregatedVacancy> generate(int size, long seed) {
        if (size < 0) {
            throw new IllegalArgumentException("размер набора не может быть отрицательным");
        }
        Random random = new Random(seed);
        List<AggregatedVacancy> data = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            String city = CITIES[random.nextInt(CITIES.length)];
            String title = TITLES[random.nextInt(TITLES.length)];
            int salary = 40_000 + random.nextInt(210_000);
            boolean remote = random.nextInt(100) < 15;
            data.add(new AggregatedVacancy("vac-" + index, city, title, salary, remote));
        }
        return data;
    }
}
