package ru.tsu.tpm.vacancyparser.hw13.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки ДЗ 13 (префикс {@code app.hw13}).
 *
 * <p>Число страниц, производителей, потребителей и ёмкость очереди — параметры эксперимента: от них
 * зависит, будет ли очередь заполняться и как часто производители станут ждать. Значения по умолчанию
 * подобраны так, чтобы обратное давление было видно на глаз (маленькая очередь), а второй прогон с
 * большой очередью показывал противоположное поведение.
 */
@Component
@ConfigurationProperties(prefix = "app.hw13")
public class Hw13Properties {

    /** Сколько страниц обходить. */
    private int pages = 24;

    /** Число производителей. */
    private int producers = 3;

    /** Число потребителей. */
    private int consumers = 3;

    /** Ёмкость очереди в основном прогоне (маленькая — видно обратное давление). */
    private int queueCapacity = 4;

    /** Ёмкость очереди во втором прогоне (большая — производители не ждут). */
    private int comparisonCapacity = 32;

    /** Задержка отдачи страницы локальным сайтом, миллисекунды. */
    private long pageDelayMillis = 60L;

    /** Таймаут установления соединения, миллисекунды. */
    private long connectTimeoutMillis = 1_000L;

    /** Таймаут запроса, миллисекунды. */
    private long requestTimeoutMillis = 2_000L;

    /** Предел ожидания завершения потоков, миллисекунды. */
    private long shutdownTimeoutMillis = 10_000L;

    /** Сколько примеров извлечённых заголовков печатать. */
    private int sampleTitles = 5;

    /** Сколько строк журнала «производитель/потребитель» печатать. */
    private int traceLines = 10;

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public int getProducers() {
        return producers;
    }

    public void setProducers(int producers) {
        this.producers = producers;
    }

    public int getConsumers() {
        return consumers;
    }

    public void setConsumers(int consumers) {
        this.consumers = consumers;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getComparisonCapacity() {
        return comparisonCapacity;
    }

    public void setComparisonCapacity(int comparisonCapacity) {
        this.comparisonCapacity = comparisonCapacity;
    }

    public long getPageDelayMillis() {
        return pageDelayMillis;
    }

    public void setPageDelayMillis(long pageDelayMillis) {
        this.pageDelayMillis = pageDelayMillis;
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

    public int getTraceLines() {
        return traceLines;
    }

    public void setTraceLines(int traceLines) {
        this.traceLines = traceLines;
    }
}
