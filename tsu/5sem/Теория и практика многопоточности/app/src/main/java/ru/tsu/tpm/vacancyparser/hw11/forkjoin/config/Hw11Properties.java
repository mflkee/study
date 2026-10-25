package ru.tsu.tpm.vacancyparser.hw11.forkjoin.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.hw11.forkjoin.model.CountCriteria;

/**
 * Настройки ДЗ 11 (префикс {@code app.hw11}).
 *
 * <p>Размер набора, размер партии, параллелизм и критерий подсчёта — параметры эксперимента, поэтому
 * они живут в конфигурации, а не в коде: отчёт можно воспроизвести, меняя аргументы запуска
 * ({@code --app.hw11.dataset-size=2000000}).
 */
@Component
@ConfigurationProperties(prefix = "app.hw11")
public class Hw11Properties {

    /** Сколько агрегированных записей генерировать. */
    private int datasetSize = 1_000_000;

    /** Размер партии по умолчанию. */
    private int batchSize = 5_000;

    /** Параллелизм пула по умолчанию. */
    private int poolSize = 8;

    /** Размеры партий для таблицы сравнения. */
    private List<Integer> batchSizes = List.of(1_000, 5_000, 20_000, 100_000);

    /** Варианты параллелизма для таблицы сравнения. */
    private List<Integer> poolSizes = List.of(2, 4, 8);

    /** Сколько раз повторять замер (в таблицу попадает лучшее время). */
    private int repeats = 2;

    /** Зерно генератора набора. */
    private long seed = 2026L;

    /** Город в критерии подсчёта. */
    private String criteriaCity = "Томск";

    /** Нижняя граница зарплаты в критерии. */
    private int criteriaMinSalary = 100_000;

    /** Слово в заголовке в критерии подсчёта. */
    private String criteriaKeyword = "java";

    /** Критерий подсчёта, собранный из настроек. */
    public CountCriteria criteria() {
        return new CountCriteria(criteriaCity, criteriaMinSalary, criteriaKeyword);
    }

    public int getDatasetSize() {
        return datasetSize;
    }

    public void setDatasetSize(int datasetSize) {
        this.datasetSize = datasetSize;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public List<Integer> getBatchSizes() {
        return batchSizes;
    }

    public void setBatchSizes(List<Integer> batchSizes) {
        this.batchSizes = batchSizes;
    }

    public List<Integer> getPoolSizes() {
        return poolSizes;
    }

    public void setPoolSizes(List<Integer> poolSizes) {
        this.poolSizes = poolSizes;
    }

    public int getRepeats() {
        return repeats;
    }

    public void setRepeats(int repeats) {
        this.repeats = repeats;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public String getCriteriaCity() {
        return criteriaCity;
    }

    public void setCriteriaCity(String criteriaCity) {
        this.criteriaCity = criteriaCity;
    }

    public int getCriteriaMinSalary() {
        return criteriaMinSalary;
    }

    public void setCriteriaMinSalary(int criteriaMinSalary) {
        this.criteriaMinSalary = criteriaMinSalary;
    }

    public String getCriteriaKeyword() {
        return criteriaKeyword;
    }

    public void setCriteriaKeyword(String criteriaKeyword) {
        this.criteriaKeyword = criteriaKeyword;
    }
}
