package ru.tsu.tpm.vacancyparser.finaltask.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки Итогового ДЗ (префикс {@code app.final}).
 *
 * <p>Все параметры имеют значения по умолчанию, поэтому приложение запускается «как есть» и сразу
 * выполняет демонстрационный обход. Переопределение — аргументами запуска или строкой в
 * {@code application.properties}, например {@code --app.final.pool.size=8}.
 */
@Component
@ConfigurationProperties(prefix = "app.final")
public class FinalTaskProperties {

    private final Pool pool = new Pool();
    private final Http http = new Http();
    private final Crawl crawl = new Crawl();
    private final Schedule schedule = new Schedule();
    private final Demo demo = new Demo();

    public Pool getPool() {
        return pool;
    }

    public Http getHttp() {
        return http;
    }

    public Crawl getCrawl() {
        return crawl;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public Demo getDemo() {
        return demo;
    }

    /** Параметры собственного пула потоков. */
    public static class Pool {

        /** Число потоков обхода (фиксированное). */
        private int size = 4;

        /** Ёмкость ограниченной очереди пула. */
        private int queueCapacity = 64;

        /** Сколько ждать завершения задач при остановке пула. */
        private long shutdownTimeoutMs = 5_000L;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public long getShutdownTimeoutMs() {
            return shutdownTimeoutMs;
        }

        public void setShutdownTimeoutMs(long shutdownTimeoutMs) {
            this.shutdownTimeoutMs = shutdownTimeoutMs;
        }
    }

    /** Таймауты HTTP-клиента. */
    public static class Http {

        private long connectTimeoutMs = 2_000L;
        private long readTimeoutMs = 5_000L;

        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public long getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(long readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    /** Общие параметры обхода. */
    public static class Crawl {

        /** Предел времени на весь обход: страховка от «зависшей» задачи. */
        private long totalTimeoutMs = 30_000L;

        public long getTotalTimeoutMs() {
            return totalTimeoutMs;
        }

        public void setTotalTimeoutMs(long totalTimeoutMs) {
            this.totalTimeoutMs = totalTimeoutMs;
        }
    }

    /** Расписание периодического обхода. */
    public static class Schedule {

        private long initialDelayMs = 5_000L;
        private long fixedDelayMs = 20_000L;
        private int batchSize = 20;

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    /** Демонстрационный локальный источник страниц. */
    public static class Demo {

        private boolean enabled = true;
        private int pages = 12;
        private long pageDelayMs = 120L;
        private boolean crawlOnStartup = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPages() {
            return pages;
        }

        public void setPages(int pages) {
            this.pages = pages;
        }

        public long getPageDelayMs() {
            return pageDelayMs;
        }

        public void setPageDelayMs(long pageDelayMs) {
            this.pageDelayMs = pageDelayMs;
        }

        public boolean isCrawlOnStartup() {
            return crawlOnStartup;
        }

        public void setCrawlOnStartup(boolean crawlOnStartup) {
            this.crawlOnStartup = crawlOnStartup;
        }
    }
}
