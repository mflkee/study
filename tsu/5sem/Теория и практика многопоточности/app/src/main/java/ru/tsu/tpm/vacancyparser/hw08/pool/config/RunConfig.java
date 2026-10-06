package ru.tsu.tpm.vacancyparser.hw08.pool.config;

import java.util.List;

/**
 * Проверенная конфигурация прогона: то, что реально используется пулом, клиентом и отчётом.
 *
 * <p>Отделена от {@link Hw08Properties}: свойства — это сырые значения из конфигурации, а
 * {@code RunConfig} — их проверенный снимок. Если значение некорректно (минимальный размер больше
 * максимального, нулевая ёмкость очереди, неположительный таймаут, пустой список адресов), прогон
 * останавливается сразу с понятным сообщением, а не падает позже в середине работы.
 *
 * @param mode                  режим работы
 * @param urls                  эффективный список адресов (в локальном режиме — адреса заглушки)
 * @param coreSize              минимальный размер пула
 * @param maxSize               максимальный размер пула
 * @param queueCapacity         ёмкость очереди задач
 * @param keepAliveMillis       время жизни простаивающего сверхядерного потока
 * @param connectTimeoutMillis  таймаут установления соединения
 * @param requestTimeoutMillis  таймаут запроса
 * @param shutdownTimeoutMillis таймаут ожидания завершения пула
 */
public record RunConfig(
        WorkMode mode,
        List<String> urls,
        int coreSize,
        int maxSize,
        int queueCapacity,
        long keepAliveMillis,
        long connectTimeoutMillis,
        long requestTimeoutMillis,
        long shutdownTimeoutMillis) {

    public RunConfig {
        urls = List.copyOf(urls);
        validate(urls, coreSize, maxSize, queueCapacity, keepAliveMillis,
                connectTimeoutMillis, requestTimeoutMillis, shutdownTimeoutMillis);
    }

    /** Собрать конфигурацию из свойств приложения. */
    public static RunConfig from(Hw08Properties properties) {
        Hw08Properties.Pool pool = properties.getPool();
        return new RunConfig(
                WorkMode.from(properties.getMode()),
                properties.getUrls(),
                pool.getCoreSize(),
                pool.getMaxSize(),
                pool.getQueueCapacity(),
                pool.getKeepAliveSeconds() * 1_000L,
                pool.getConnectTimeoutMillis(),
                pool.getRequestTimeoutMillis(),
                pool.getShutdownTimeoutSeconds() * 1_000L);
    }

    /** Копия с другим списком адресов (используется, когда адреса даёт сервер-заглушка). */
    public RunConfig withUrls(List<String> replacement) {
        return new RunConfig(
                mode,
                replacement,
                coreSize,
                maxSize,
                queueCapacity,
                keepAliveMillis,
                connectTimeoutMillis,
                requestTimeoutMillis,
                shutdownTimeoutMillis);
    }

    /** Строки с фактическими настройками для вывода и отчёта. */
    public List<String> settingsLines() {
        return List.of(
                "режим работы: " + mode.label(),
                "адресов в списке: " + urls.size(),
                "размер пула (минимум/максимум): " + coreSize + "/" + maxSize,
                "ёмкость очереди: " + queueCapacity,
                "время жизни простаивающего потока: " + keepAliveMillis + " мс",
                "таймаут соединения: " + connectTimeoutMillis + " мс",
                "таймаут запроса: " + requestTimeoutMillis + " мс",
                "таймаут завершения пула: " + shutdownTimeoutMillis + " мс");
    }

    private static void validate(
            List<String> urls,
            int coreSize,
            int maxSize,
            int queueCapacity,
            long keepAliveMillis,
            long connectTimeoutMillis,
            long requestTimeoutMillis,
            long shutdownTimeoutMillis) {
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("список адресов пуст: нужен хотя бы один URL");
        }
        if (coreSize < 1) {
            throw new IllegalArgumentException("минимальный размер пула должен быть положительным, получено " + coreSize);
        }
        if (maxSize < coreSize) {
            throw new IllegalArgumentException(
                    "максимальный размер пула (" + maxSize + ") не может быть меньше минимального (" + coreSize + ")");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("ёмкость очереди должна быть положительной, получено " + queueCapacity);
        }
        if (keepAliveMillis <= 0) {
            throw new IllegalArgumentException("время жизни простаивающего потока должно быть положительным, получено "
                    + keepAliveMillis + " мс");
        }
        if (connectTimeoutMillis <= 0) {
            throw new IllegalArgumentException("таймаут соединения должен быть положительным, получено "
                    + connectTimeoutMillis + " мс");
        }
        if (requestTimeoutMillis <= 0) {
            throw new IllegalArgumentException("таймаут запроса должен быть положительным, получено "
                    + requestTimeoutMillis + " мс");
        }
        if (shutdownTimeoutMillis <= 0) {
            throw new IllegalArgumentException("таймаут завершения пула должен быть положительным, получено "
                    + shutdownTimeoutMillis + " мс");
        }
    }
}
