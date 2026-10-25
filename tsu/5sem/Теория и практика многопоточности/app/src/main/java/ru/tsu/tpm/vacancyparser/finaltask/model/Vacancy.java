package ru.tsu.tpm.vacancyparser.finaltask.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Вакансия, сохранённая в H2.
 *
 * <p>Ключ дедупликации — {@link #url}: повторный обход той же страницы не создаёт вторую запись, а
 * обновляет существующую и освежает {@link #fetchedAt}. Поэтому периодический обход по расписанию
 * безопасен и не размножает данные.
 *
 * <p>Поля соответствуют заданию варианта 3: название, компания, зарплата, требования, город, дата
 * размещения. {@code salaryText} хранит исходную формулировку («от 150 000 ₽», «по договорённости»), а
 * {@code salary} — число для сортировки; у «по договорённости» числа нет, и это {@code null}, а не ноль.
 */
@Entity
@Table(
        name = "vacancies",
        uniqueConstraints = @UniqueConstraint(name = "uk_vacancies_url", columnNames = "url"),
        indexes = {
            @Index(name = "idx_vacancies_city", columnList = "city"),
            @Index(name = "idx_vacancies_salary", columnList = "salary"),
            @Index(name = "idx_vacancies_published_at", columnList = "published_at")
        })
public class Vacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "company", length = 512)
    private String company;

    /** Зарплата числом (рубли); {@code null}, если в объявлении «по договорённости». */
    @Column(name = "salary")
    private Integer salary;

    @Column(name = "salary_text", length = 255)
    private String salaryText;

    @Column(name = "city", length = 255)
    private String city;

    @Column(name = "requirements", length = 4000)
    private String requirements;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    /** Конструктор для JPA. */
    protected Vacancy() {
    }

    /** Новая вакансия из разобранной страницы. */
    public Vacancy(ParsedVacancy parsed) {
        this.url = parsed.url();
        apply(parsed);
    }

    /** Обновить содержимое записи свежими данными страницы (URL не меняется). */
    public void apply(ParsedVacancy parsed) {
        this.title = parsed.title();
        this.company = parsed.company();
        this.salary = parsed.salary();
        this.salaryText = parsed.salaryText();
        this.city = parsed.city();
        this.requirements = parsed.requirements();
        this.publishedAt = parsed.publishedAt();
        this.fetchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public Integer getSalary() {
        return salary;
    }

    public String getSalaryText() {
        return salaryText;
    }

    public String getCity() {
        return city;
    }

    public String getRequirements() {
        return requirements;
    }

    public LocalDate getPublishedAt() {
        return publishedAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
