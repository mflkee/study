package ru.tsu.tpm.vacancyparser.hw10.completable.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки ДЗ 10 (префикс {@code app.hw10}).
 *
 * <p>Задаются в {@code application.properties} и переопределяются аргументами запуска, поэтому
 * демонстрацию можно ускорить или ужесточить без правки кода:
 * {@code --app.hw10.pool-size=3 --app.hw10.latency-millis=50}.
 *
 * <p>{@code detailsFailId} и {@code companyFailId} — идентификаторы, на которых заглушка отвечает
 * ошибкой. Это делает требование «имитировать ошибку в одном источнике» настраиваемым, а не зашитым.
 */
@Component
@ConfigurationProperties(prefix = "app.hw10")
public class Hw10Properties {

    /** Размер собственного пула потоков. */
    private int poolSize = 6;

    /** Задержка ответа заглушки на каждый запрос, миллисекунды. */
    private long latencyMillis = 120L;

    /** Моделируемая длительность тяжёлого преобразования, миллисекунды. */
    private long transformDelayMillis = 10L;

    /** Таймаут установления соединения, миллисекунды. */
    private long connectTimeoutMillis = 1_000L;

    /** Таймаут запроса, миллисекунды. */
    private long requestTimeoutMillis = 2_000L;

    /** Таймаут остановки пула, миллисекунды. */
    private long shutdownTimeoutMillis = 3_000L;

    /** Идентификаторы, по которым идёт выборка в демонстрации. */
    private List<String> ids = List.of(
            "v-01", "v-02", "v-03", "v-04", "v-05", "v-06",
            "v-07", "v-08", "v-09", "v-10", "v-11", "v-12");

    /** Идентификатор, на котором «падает» источник данных вакансии. */
    private String detailsFailId = "v-07";

    /** Идентификатор, на котором «падает» справочник компаний. */
    private String companyFailId = "v-04";

    /** Сколько строк журнала печатать в демонстрации. */
    private int traceLines = 14;

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }

    public void setLatencyMillis(long latencyMillis) {
        this.latencyMillis = latencyMillis;
    }

    public long getTransformDelayMillis() {
        return transformDelayMillis;
    }

    public void setTransformDelayMillis(long transformDelayMillis) {
        this.transformDelayMillis = transformDelayMillis;
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

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public String getDetailsFailId() {
        return detailsFailId;
    }

    public void setDetailsFailId(String detailsFailId) {
        this.detailsFailId = detailsFailId;
    }

    public String getCompanyFailId() {
        return companyFailId;
    }

    public void setCompanyFailId(String companyFailId) {
        this.companyFailId = companyFailId;
    }

    public int getTraceLines() {
        return traceLines;
    }

    public void setTraceLines(int traceLines) {
        this.traceLines = traceLines;
    }
}
