package ru.tsu.tpm.vacancyparser.hw10.completable.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Локальный HTTP-сервер-заглушка «API вакансий» для ДЗ 10.
 *
 * <p>Два независимых источника на одном порту: {@code /api/vacancies/{id}} — подробности вакансии,
 * {@code /api/companies/{id}} — справочник компаний. Задержка ответа задаётся параметром: именно она
 * делает выигрыш от {@code CompletableFuture} измеримым (последовательные запросы складываются,
 * параллельные — перекрываются). Часть идентификаторов намеренно отвечает ошибкой: так проверяется
 * требование «сбой одного источника не ломает всю операцию».
 *
 * <p>Сервер поднимается на свободном порту ({@code 0} — порт выбирает ОС), поэтому демонстрация и
 * тесты не зависят от внешней сети и от занятости конкретного порта.
 */
public final class VacancyStubServer implements AutoCloseable {

    /** Префикс маршрута подробностей вакансии. */
    public static final String DETAILS_PATH = "/api/vacancies/";

    /** Префикс маршрута справочника компаний. */
    public static final String COMPANY_PATH = "/api/companies/";

    /** Заголовки вакансий — берутся по кругу, чтобы ответы были предсказуемыми. */
    private static final String[] TITLES = {
            "Java-разработчик",
            "Backend-разработчик (Java)",
            "Инженер по тестированию",
            "Аналитик данных",
            "Team Lead Java",
            "Разработчик Spring Boot",
            "Специалист по сопровождению",
            "QA-инженер автоматизации"
    };

    /** Города вакансий. */
    private static final String[] CITIES = {
            "Томск", "Новосибирск", "Москва", "Санкт-Петербург", "Екатеринбург", "Удалённо"
    };

    /** Названия компаний. */
    private static final String[] COMPANIES = {
            "ООО «Ромашка»", "АО «Сибирь-Софт»", "ООО «Код и Точка»", "АО «Технопарк»", "ООО «Дата-Мост»"
    };

    private final HttpServer server;
    private final ExecutorService workers;
    private final long delayMillis;
    private final Set<String> failingDetails;
    private final Set<String> failingCompanies;
    private final AtomicInteger requests = new AtomicInteger();

    private VacancyStubServer(
            HttpServer server,
            ExecutorService workers,
            long delayMillis,
            Set<String> failingDetails,
            Set<String> failingCompanies) {
        this.server = server;
        this.workers = workers;
        this.delayMillis = delayMillis;
        this.failingDetails = Set.copyOf(failingDetails);
        this.failingCompanies = Set.copyOf(failingCompanies);
    }

    /** Поднять заглушку с задержкой ответа и набором «сломанных» идентификаторов. */
    public static VacancyStubServer start(long delayMillis, Set<String> failingDetails, Set<String> failingCompanies)
            throws IOException {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("задержка ответа не может быть отрицательной");
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "hw10-stub-worker");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(workers);

        VacancyStubServer stub = new VacancyStubServer(
                server, workers, delayMillis, failingDetails, failingCompanies);
        stub.registerRoutes();
        server.start();
        return stub;
    }

    /** Поднять заглушку без «сломанных» идентификаторов. */
    public static VacancyStubServer start(long delayMillis) throws IOException {
        return start(delayMillis, Set.of(), Set.of());
    }

    private void registerRoutes() {
        server.createContext("/health", exchange -> respond(exchange, 200, "{\"status\":\"ok\"}"));
        server.createContext(DETAILS_PATH, exchange -> handleDetails(exchange));
        server.createContext(COMPANY_PATH, exchange -> handleCompany(exchange));
    }

    private void handleDetails(HttpExchange exchange) throws IOException {
        String id = idFrom(exchange.getRequestURI().getPath(), DETAILS_PATH);
        if (id.isEmpty()) {
            respond(exchange, 400, "{\"error\":\"не задан идентификатор вакансии\"}");
            return;
        }
        if (failingDetails.contains(id)) {
            respond(exchange, 500, "{\"error\":\"источник вакансий недоступен\"}");
            return;
        }
        pause();
        respond(exchange, 200, detailsJson(id));
    }

    private void handleCompany(HttpExchange exchange) throws IOException {
        String id = idFrom(exchange.getRequestURI().getPath(), COMPANY_PATH);
        if (id.isEmpty()) {
            respond(exchange, 400, "{\"error\":\"не задан идентификатор компании\"}");
            return;
        }
        if (failingCompanies.contains(id)) {
            respond(exchange, 503, "{\"error\":\"справочник компаний перегружен\"}");
            return;
        }
        pause();
        respond(exchange, 200, companyJson(id));
    }

    /** Тело успешного ответа первого источника. */
    public static String detailsJson(String id) {
        int seed = seed(id);
        int salaryFrom = 60_000 + (seed % 12) * 10_000;
        int salaryTo = salaryFrom + 30_000 + (seed % 5) * 10_000;
        return String.format(
                Locale.ROOT,
                "{\"id\":\"%s\",\"title\":\"%s\",\"city\":\"%s\",\"salaryFrom\":%d,\"salaryTo\":%d}",
                id, TITLES[seed % TITLES.length], CITIES[seed % CITIES.length], salaryFrom, salaryTo);
    }

    /** Тело успешного ответа второго источника. */
    public static String companyJson(String id) {
        int seed = seed(id);
        return String.format(
                Locale.ROOT,
                "{\"id\":\"%s\",\"name\":\"%s\",\"rating\":%.1f,\"openVacancies\":%d}",
                id, COMPANIES[seed % COMPANIES.length], 3.5 + (seed % 15) / 10.0, 1 + seed % 20);
    }

    /** Ожидаемый заголовок вакансии для идентификатора — используется в проверках. */
    public static String expectedTitle(String id) {
        return TITLES[seed(id) % TITLES.length];
    }

    /** Ожидаемый город для идентификатора. */
    public static String expectedCity(String id) {
        return CITIES[seed(id) % CITIES.length];
    }

    /** Детерминированное «зерно» из идентификатора: номер после дефиса либо хеш. */
    static int seed(String id) {
        int digits = 0;
        int parsed = 0;
        for (int index = 0; index < id.length(); index++) {
            char current = id.charAt(index);
            if (Character.isDigit(current)) {
                parsed = parsed * 10 + (current - '0');
                digits++;
            }
        }
        return digits == 0 ? Math.abs(id.hashCode()) : Math.abs(parsed);
    }

    private static String idFrom(String path, String prefix) {
        return path.length() <= prefix.length() ? "" : path.substring(prefix.length());
    }

    private void pause() {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void respond(HttpExchange exchange, int code, String body) throws IOException {
        requests.incrementAndGet();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException e) {
            // Клиент мог закрыть соединение по таймауту — для заглушки это нормальный исход.
        }
    }

    /** Фактический порт. */
    public int port() {
        return server.getAddress().getPort();
    }

    /** Базовый адрес заглушки. */
    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    /** Сколько запросов обработала заглушка (успешных и неуспешных). */
    public int requestCount() {
        return requests.get();
    }

    /** Список идентификаторов {@code v-01..v-NN} для демонстрации. */
    public static List<String> defaultIds(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("нужен хотя бы один идентификатор");
        }
        List<String> ids = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            ids.add(String.format(Locale.ROOT, "v-%02d", index));
        }
        return ids;
    }

    /** Проверка, что «сломанные» идентификаторы входят в рабочий список. */
    public static List<String> missingFailIds(List<String> ids, String... failIds) {
        Set<String> present = new LinkedHashSet<>(ids);
        List<String> missing = new ArrayList<>();
        for (String failId : failIds) {
            if (failId != null && !failId.isBlank() && !present.contains(failId)) {
                missing.add(failId);
            }
        }
        return missing;
    }

    /** Живые потоки заглушки (для проверки, что после остановки ничего не осталось). */
    public static long liveThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("hw10-stub-worker") && thread.isAlive())
                .count();
    }

    /** Остановить заглушку и освободить порт. */
    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
    }
}
