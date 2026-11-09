package ru.tsu.tpm.vacancyparser.hw14.loom.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки ДЗ 14 (префикс {@code app.hw14}).
 *
 * <p>Все числа эксперимента — параметры: сколько страниц грузить, какая задержка у локального сайта,
 * сколько задач в демонстрации масштабирования, сколько «блокирующих» задач в опыте с pinning,
 * сколько задач в сценарии «поток на задачу» и сколько подзадач/какой срок у опытов со
 * {@code StructuredTaskScope}. Значения по умолчанию подобраны так, чтобы демонстрация целиком
 * укладывалась в несколько секунд, но различия между виртуальными потоками и платформенным пулом
 * были видны на порядок, а не на проценты.
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

    /** Сколько задач в сценарии «поток на задачу» (текст задания: 10 000). */
    private int swarmTasks = 10_000;

    /** Ожидание внутри задачи сценария «поток на задачу», миллисекунды. */
    private long swarmSleepMillis = 1L;

    /**
     * Сколько платформенных потоков держать одновременно в сценарии «поток на задачу»;
     * {@code 0} — все сразу (10 000), положительное число меньше {@code swarmTasks} — запуск пачками.
     */
    private int swarmMaxPlatformThreads = 0;

    /** Сколько имён потоков печатать в журнал (остальные только собираются и проверяются тестами). */
    private int swarmPrintNames = 8;

    /** Сколько подзадач в опытах со {@code StructuredTaskScope}. */
    private int structuredTasks = 10_000;

    /** Ожидание обычной подзадачи в опытах со скоупом, миллисекунды. */
    private long structuredDelayMillis = 300L;

    /** Через сколько миллисекунд падает «сбойная» подзадача. */
    private long structuredFailAfterMillis = 100L;

    /** Номер подзадачи, которая падает в опыте с исключением. */
    private int structuredFailingTask = 137;

    /** Срок ожидания группы в опыте с таймаутом, миллисекунды. */
    private long structuredTimeoutMillis = 250L;

    /** Ожидание подзадачи в опыте с таймаутом — заведомо больше срока. */
    private long structuredSlowDelayMillis = 5_000L;

    /** Задержки «зеркал» в опыте «первый успешный ответ», миллисекунды. */
    private List<Long> structuredReplicaDelaysMillis = List.of(400L, 150L, 250L);

    public int getSwarmTasks() {
        return swarmTasks;
    }

    public void setSwarmTasks(int swarmTasks) {
        this.swarmTasks = swarmTasks;
    }

    public long getSwarmSleepMillis() {
        return swarmSleepMillis;
    }

    public void setSwarmSleepMillis(long swarmSleepMillis) {
        this.swarmSleepMillis = swarmSleepMillis;
    }

    public int getSwarmMaxPlatformThreads() {
        return swarmMaxPlatformThreads;
    }

    public void setSwarmMaxPlatformThreads(int swarmMaxPlatformThreads) {
        this.swarmMaxPlatformThreads = swarmMaxPlatformThreads;
    }

    public int getSwarmPrintNames() {
        return swarmPrintNames;
    }

    public void setSwarmPrintNames(int swarmPrintNames) {
        this.swarmPrintNames = swarmPrintNames;
    }

    public int getStructuredTasks() {
        return structuredTasks;
    }

    public void setStructuredTasks(int structuredTasks) {
        this.structuredTasks = structuredTasks;
    }

    public long getStructuredDelayMillis() {
        return structuredDelayMillis;
    }

    public void setStructuredDelayMillis(long structuredDelayMillis) {
        this.structuredDelayMillis = structuredDelayMillis;
    }

    public long getStructuredFailAfterMillis() {
        return structuredFailAfterMillis;
    }

    public void setStructuredFailAfterMillis(long structuredFailAfterMillis) {
        this.structuredFailAfterMillis = structuredFailAfterMillis;
    }

    public int getStructuredFailingTask() {
        return structuredFailingTask;
    }

    public void setStructuredFailingTask(int structuredFailingTask) {
        this.structuredFailingTask = structuredFailingTask;
    }

    public long getStructuredTimeoutMillis() {
        return structuredTimeoutMillis;
    }

    public void setStructuredTimeoutMillis(long structuredTimeoutMillis) {
        this.structuredTimeoutMillis = structuredTimeoutMillis;
    }

    public long getStructuredSlowDelayMillis() {
        return structuredSlowDelayMillis;
    }

    public void setStructuredSlowDelayMillis(long structuredSlowDelayMillis) {
        this.structuredSlowDelayMillis = structuredSlowDelayMillis;
    }

    public List<Long> getStructuredReplicaDelaysMillis() {
        return structuredReplicaDelaysMillis;
    }

    public void setStructuredReplicaDelaysMillis(List<Long> structuredReplicaDelaysMillis) {
        this.structuredReplicaDelaysMillis = structuredReplicaDelaysMillis;
    }

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
