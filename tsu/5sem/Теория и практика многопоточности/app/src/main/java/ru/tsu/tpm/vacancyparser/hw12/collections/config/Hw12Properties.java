package ru.tsu.tpm.vacancyparser.hw12.collections.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки ДЗ 12 (префикс {@code app.hw12}).
 *
 * <p>Параметры нагрузки (число потоков, ключей и операций), срок жизни кэша, его ёмкость и размер
 * журнала событий задаются конфигурацией: демонстрацию можно сделать тяжелее или легче без правки кода.
 */
@Component
@ConfigurationProperties(prefix = "app.hw12")
public class Hw12Properties {

    /** Потоков-клиентов кэша. */
    private int threads = 8;

    /** Сколько разных ключей запрашивают клиенты. */
    private int keys = 200;

    /** Обращений к кэшу на поток. */
    private int opsPerThread = 2_000;

    /** Моделируемая длительность загрузки значения, миллисекунды. */
    private long loaderDelayMillis = 1L;

    /** Срок жизни записи кэша, миллисекунды. */
    private long ttlMillis = 300L;

    /** Предел числа записей в кэше. */
    private int cacheCapacity = 256;

    /** Предел журнала последних событий. */
    private int logLimit = 100;

    /** Сколько подписчиков регистрируется в демонстрации. */
    private int subscribers = 3;

    /** Сколько записей кладётся для проверки истечения срока жизни. */
    private int ttlKeys = 5;

    /** Ёмкость отдельного кэша для проверки вытеснения. */
    private int evictionCapacity = 128;

    /** Сколько записей кладётся для проверки вытеснения по ёмкости. */
    private int evictionInsertions = 340;

    /** Потоков в замерах коллекций. */
    private int benchmarkThreads = 8;

    /** Ключей в замере карт. */
    private int benchmarkKeys = 1_000;

    /** Операций на поток в замере. */
    private int benchmarkOpsPerThread = 50_000;

    /** Размер топа рейтинга. */
    private int topSize = 5;

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getKeys() {
        return keys;
    }

    public void setKeys(int keys) {
        this.keys = keys;
    }

    public int getOpsPerThread() {
        return opsPerThread;
    }

    public void setOpsPerThread(int opsPerThread) {
        this.opsPerThread = opsPerThread;
    }

    public long getLoaderDelayMillis() {
        return loaderDelayMillis;
    }

    public void setLoaderDelayMillis(long loaderDelayMillis) {
        this.loaderDelayMillis = loaderDelayMillis;
    }

    public long getTtlMillis() {
        return ttlMillis;
    }

    public void setTtlMillis(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public int getCacheCapacity() {
        return cacheCapacity;
    }

    public void setCacheCapacity(int cacheCapacity) {
        this.cacheCapacity = cacheCapacity;
    }

    public int getLogLimit() {
        return logLimit;
    }

    public void setLogLimit(int logLimit) {
        this.logLimit = logLimit;
    }

    public int getSubscribers() {
        return subscribers;
    }

    public void setSubscribers(int subscribers) {
        this.subscribers = subscribers;
    }

    public int getTtlKeys() {
        return ttlKeys;
    }

    public void setTtlKeys(int ttlKeys) {
        this.ttlKeys = ttlKeys;
    }

    public int getEvictionCapacity() {
        return evictionCapacity;
    }

    public void setEvictionCapacity(int evictionCapacity) {
        this.evictionCapacity = evictionCapacity;
    }

    public int getEvictionInsertions() {
        return evictionInsertions;
    }

    public void setEvictionInsertions(int evictionInsertions) {
        this.evictionInsertions = evictionInsertions;
    }

    public int getBenchmarkThreads() {
        return benchmarkThreads;
    }

    public void setBenchmarkThreads(int benchmarkThreads) {
        this.benchmarkThreads = benchmarkThreads;
    }

    public int getBenchmarkKeys() {
        return benchmarkKeys;
    }

    public void setBenchmarkKeys(int benchmarkKeys) {
        this.benchmarkKeys = benchmarkKeys;
    }

    public int getBenchmarkOpsPerThread() {
        return benchmarkOpsPerThread;
    }

    public void setBenchmarkOpsPerThread(int benchmarkOpsPerThread) {
        this.benchmarkOpsPerThread = benchmarkOpsPerThread;
    }

    public int getTopSize() {
        return topSize;
    }

    public void setTopSize(int topSize) {
        this.topSize = topSize;
    }
}
