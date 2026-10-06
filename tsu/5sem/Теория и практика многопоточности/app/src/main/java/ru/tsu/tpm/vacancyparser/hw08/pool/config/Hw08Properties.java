package ru.tsu.tpm.vacancyparser.hw08.pool.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки ДЗ 8, привязанные к конфигурации приложения (префикс {@code app.hw08}).
 *
 * <p>Значения задаются в {@code application.properties} и переопределяются аргументами запуска
 * ({@code --app.hw08.pool.core-size=6} и т.д.), поэтому демонстрация не содержит зашитых параметров.
 * Фактические значения печатаются перед прогоном — по ним и проверяется, что настройка сработала.
 *
 * <p>Аннотация {@link Component} нужна, чтобы бин настроек находился компонентным сканированием в том
 * числе в тестовом контексте, который собирается без {@code @SpringBootApplication}.
 */
@Component
@ConfigurationProperties(prefix = "app.hw08")
public class Hw08Properties {

    /** Режим работы: local (по умолчанию), external или mixed. */
    private String mode = "local";

    /**
     * Внешние адреса. По умолчанию — список публичных API; в локальном режиме он не используется,
     * потому что адреса берутся у сервера-заглушки.
     */
    private List<String> urls = new ArrayList<>(List.of(
            "https://example.com",
            "https://www.example.org",
            "https://httpbin.org/status/200",
            "https://httpbin.org/status/404",
            "https://httpbin.org/status/500",
            "https://httpbin.org/delay/1",
            "https://api.github.com",
            "https://api.github.com/zen",
            "https://jsonplaceholder.typicode.com/posts/1",
            "https://jsonplaceholder.typicode.com/comments",
            "https://jsonplaceholder.typicode.com/albums",
            "https://jsonplaceholder.typicode.com/todos/1",
            "https://jsonplaceholder.typicode.com/users",
            "https://jsonplaceholder.typicode.com/photos",
            "https://catfact.ninja/fact",
            "https://api.coindesk.com/v1/bpi/currentprice.json",
            "https://worldtimeapi.org/api/timezone/Etc/UTC",
            "https://api.publicapis.org/entries",
            "https://date.nager.at/api/v3/PublicHolidays/2026/RU",
            "https://api.open-meteo.com/v1/forecast?latitude=0&longitude=0"));

    /** Параметры пула потоков. */
    private Pool pool = new Pool();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    public Pool getPool() {
        return pool;
    }

    public void setPool(Pool pool) {
        this.pool = pool;
    }

    /**
     * Ручные настройки пула. Значения по умолчанию подобраны так, чтобы максимальный размер был
     * достижим: при 20 задачах очередь заполняется и пул расширяется с {@code coreSize} до
     * {@code maxSize}, но задач хватает всем (без срабатывания обработчика отказа).
     */
    public static class Pool {

        /** Минимальный размер пула (одновременных запросов). */
        private int coreSize = 4;

        /** Максимальный размер пула. */
        private int maxSize = 8;

        /** Ёмкость очереди задач — обязательно ограниченная. */
        private int queueCapacity = 8;

        /** Время жизни простаивающего сверхядерного потока, секунды. */
        private long keepAliveSeconds = 30L;

        /** Таймаут установления соединения, миллисекунды. */
        private long connectTimeoutMillis = 1_000L;

        /** Таймаут запроса (ожидания ответа), миллисекунды. */
        private long requestTimeoutMillis = 3_000L;

        /** Таймаут ожидания завершения пула, секунды. */
        private long shutdownTimeoutSeconds = 10L;

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public long getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(long keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
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

        public long getShutdownTimeoutSeconds() {
            return shutdownTimeoutSeconds;
        }

        public void setShutdownTimeoutSeconds(long shutdownTimeoutSeconds) {
            this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
        }
    }
}
