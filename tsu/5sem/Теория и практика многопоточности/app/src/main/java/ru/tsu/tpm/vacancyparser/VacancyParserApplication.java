package ru.tsu.tpm.vacancyparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа приложения.
 *
 * <p>Приложение является каркасом для Итогового домашнего задания (вариант 3 —
 * парсер вакансий). Домашние задания 1–8 наращивают его по модулям {@code hw01..hw08},
 * каждый из которых запускается изолированно через {@code --app.hw=hwNN}.
 */
@SpringBootApplication
public class VacancyParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(VacancyParserApplication.class, args);
    }
}
