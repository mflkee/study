package ru.tsu.tpm.vacancyparser.hw10.completable.source;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.CompanyInfo;
import ru.tsu.tpm.vacancyparser.hw10.completable.model.VacancyDetails;
import ru.tsu.tpm.vacancyparser.hw10.completable.server.VacancyStubServer;

/**
 * HTTP-клиент двух источников ДЗ 10: API вакансий и справочник компаний.
 *
 * <p>Клиент намеренно <b>синхронный</b>: он вызывается из задачи {@code CompletableFuture}, то есть
 * внутри собственного пула. Асинхронность здесь обеспечивает конвейер, а не клиент, — так проще
 * показать, что все этапы (запрос, преобразование, объединение) идут на нашем исполнителе, а не на
 * {@code ForkJoinPool.commonPool}.
 *
 * <p>Ответ с кодом, отличным от {@code 200}, превращается в {@link SourceUnavailableException}:
 * для вызывающего кода это такой же сбой источника, как обрыв соединения, и обрабатывается он тем же
 * {@code handle}/{@code exceptionally}.
 */
public final class HttpVacancyClient implements VacancyDetailsSource, CompanyInfoSource, AutoCloseable {

    private final HttpClient client;
    private final String baseUrl;
    private final long requestTimeoutMillis;
    private final AtomicInteger requestCount = new AtomicInteger();

    /** Создать клиент для заглушки по её базовому адресу. */
    public HttpVacancyClient(String baseUrl, long connectTimeoutMillis, long requestTimeoutMillis) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("не задан базовый адрес API");
        }
        if (connectTimeoutMillis <= 0 || requestTimeoutMillis <= 0) {
            throw new IllegalArgumentException("таймауты должны быть положительными");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();
    }

    @Override
    public VacancyDetails fetchDetails(String id) throws IOException, InterruptedException {
        Map<String, String> json = get(VacancyStubServer.DETAILS_PATH + encode(id));
        return new VacancyDetails(
                FlatJson.string(json, "id"),
                FlatJson.string(json, "title"),
                FlatJson.string(json, "city"),
                FlatJson.intValue(json, "salaryFrom"),
                FlatJson.intValue(json, "salaryTo"));
    }

    @Override
    public CompanyInfo fetchCompany(String id) throws IOException, InterruptedException {
        Map<String, String> json = get(VacancyStubServer.COMPANY_PATH + encode(id));
        return new CompanyInfo(
                FlatJson.string(json, "id"),
                FlatJson.string(json, "name"),
                FlatJson.doubleValue(json, "rating"),
                FlatJson.intValue(json, "openVacancies"),
                false);
    }

    /** Сколько запросов сделал клиент. */
    public int requestCount() {
        return requestCount.get();
    }

    /** Таймаут запроса — для вывода в отчёте. */
    public long requestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    private Map<String, String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(requestTimeoutMillis))
                .GET()
                .build();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        requestCount.incrementAndGet();
        if (response.statusCode() != 200) {
            throw new SourceUnavailableException(path, response.statusCode(), response.body());
        }
        return FlatJson.parseObject(response.body());
    }

    private static String encode(String id) {
        return URLEncoder.encode(id, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        client.close();
    }

    /**
     * Источник ответил ошибкой или недоступен.
     *
     * <p>Наследуется от {@link IOException}: для конвейера это обычный сбой ввода-вывода, который
     * ловится {@code handle} и превращается в запись о сбое, а не в падение всей операции.
     */
    public static final class SourceUnavailableException extends IOException {

        private final String path;
        private final int status;

        public SourceUnavailableException(String path, int status, String body) {
            super("источник " + path + " ответил HTTP " + status
                    + (body == null || body.isBlank() ? "" : ": " + body));
            this.path = path;
            this.status = status;
        }

        /** Запрошенный путь. */
        public String path() {
            return path;
        }

        /** Код ответа источника. */
        public int status() {
            return status;
        }
    }
}
