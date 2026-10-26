package ru.tsu.tpm.vacancyparser.hw14.loom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки ДЗ 14 (префикс {@code app.hw14}).
 *
 * <p>Все числа эксперимента — параметры: сколько страниц грузить, какая задержка у локального сайта,
 * сколько задач в демонстрации масштабирования и сколько «блокирующих» задач в опыте с pinning.
 * Значения по умолчанию подобраны так, чтобы демонстрация целиком укладывалась в несколько секунд,
 * но различия между виртуальными потоками и платформенным пулом были видны на порядок, а не на
 * проценты.
 *
 * <p>Размер платформенного пула для сравнения ({@link #fixedPoolSize} = 16) намеренно скромный: это
 * типичный размер пула для блокирующего IO, и именно на нём видно, что производительность упирается
 * в число потоков, а не в процессор. Пул на {@link #platformPoolSize} потоков в демонстрации
 * масштабирования — «щедрый» вариант, который всё равно проигрывает на тысячах спящих задач.
 */
@Component
@ConfigurationProperties(prefix = "app.hw14")
public class Hw14Properties {

    /** Сколько страниц вакансий загружать в базовом сценарии. */
    private int pages = 200;

    /** Задержка отдачи страницы локальным сайтом, миллисекунды (имитация сети). */
    private long pageDelayMillis = 50L;

    /** Размер платформенного пула в сравнении «тот же объём работы». */
    private int fixedPoolSize = 16;

    /** Сколько задач в демонстрации масштабирования. */
    private int scalingTasks = 10_000;

    /** Имитация блокировки в задаче масштабирования, миллисекунды. */
    private long scalingDelayMillis = 50L;

    /** Размер платформенного пула в демонстрации масштабирования. */
    private int platformPoolSize = 200;

    /** Сколько задач в опыте с pinning (по умолчанию 8 кругов по числу carrier-потоков). */
    private int pinningTasks = 96;

    /** Длительность блокирующей операции внутри synchronized/ReentrantLock, миллисекунды. */
    private long pinningBlockMillis = 60L;

    /** Таймаут установления соединения, миллисекунды. */
    private long connectTimeoutMillis = 1_000L;

    /** Таймаут запроса страницы, миллисекунды. */
    private long requestTimeoutMillis = 5_000L;

    /** Предел ожидания завершения задач одного прогона, миллисекунды. */
    private long taskTimeoutMillis = 60_000L;

    /** Предел ожидания остановки пула, миллисекунды. */
    private long shutdownTimeoutMillis = 5_000L;

    /** Сколько примеров заголовков печатать. */
    private int sampleTitles = 5;

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public long getPageDelayMillis() {
        return pageDelayMillis;
    }

    public void setPageDelayMillis(long pageDelayMillis) {
        this.pageDelayMillis = pageDelayMillis;
    }

    public int getFixedPoolSize() {
        return fixedPoolSize;
    }

    public void setFixedPoolSize(int fixedPoolSize) {
        this.fixedPoolSize = fixedPoolSize;
    }

    public int getScalingTasks() {
        return scalingTasks;
    }

    public void setScalingTasks(int scalingTasks) {
        this.scalingTasks = scalingTasks;
    }

    public long getScalingDelayMillis() {
        return scalingDelayMillis;
    }

    public void setScalingDelayMillis(long scalingDelayMillis) {
        this.scalingDelayMillis = scalingDelayMillis;
    }

    public int getPlatformPoolSize() {
        return platformPoolSize;
    }

    public void setPlatformPoolSize(int platformPoolSize) {
        this.platformPoolSize = platformPoolSize;
    }

    public int getPinningTasks() {
        return pinningTasks;
    }

    public void setPinningTasks(int pinningTasks) {
        this.pinningTasks = pinningTasks;
    }

    public long getPinningBlockMillis() {
        return pinningBlockMillis;
    }

    public void setPinningBlockMillis(long pinningBlockMillis) {
        this.pinningBlockMillis = pinningBlockMillis;
    }

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(long connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public long getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public void setRequestTimeoutMillis(long requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public long getTaskTimeoutMillis() {
        return taskTimeoutMillis;
    }

    public void setTaskTimeoutMillis(long taskTimeoutMillis) {
        this.taskTimeoutMillis = taskTimeoutMillis;
    }

    public long getShutdownTimeoutMillis() {
        return shutdownTimeoutMillis;
    }

    public void setShutdownTimeoutMillis(long shutdownTimeoutMillis) {
        this.shutdownTimeoutMillis = shutdownTimeoutMillis;
    }

    public int getSampleTitles() {
        return sampleTitles;
    }

    public void setSampleTitles(int sampleTitles) {
        this.sampleTitles = sampleTitles;
    }
}
