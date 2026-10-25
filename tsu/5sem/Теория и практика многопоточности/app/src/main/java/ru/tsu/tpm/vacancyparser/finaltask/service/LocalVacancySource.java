package ru.tsu.tpm.vacancyparser.finaltask.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.finaltask.config.FinalTaskConfig;
import ru.tsu.tpm.vacancyparser.finaltask.config.FinalTaskProperties;

/**
 * Локальный HTTP-сервер-заглушка с настоящими HTML-страницами вакансий.
 *
 * <p>Зачем: проверяющему не нужен интернет. Приложение поднимает заглушку на свободном порту
 * ({@code 127.0.0.1:0} — порт выбирает ОС) и обходит её тем же кодом, которым обходило бы hh.ru:
 * HTTP-запрос, HTML, парсер, H2. Страницы размечены как на hh.ru — JSON-LD {@code JobPosting},
 * {@code data-qa="vacancy-title"} и т. д., — поэтому демонстрация проверяет именно парсер, а не
 * заранее подготовленный ответ.
 *
 * <p>Каждая страница отдаётся с задержкой {@code app.final.demo.page-delay-ms}. Задержка делает
 * выигрыш от параллельности измеримым: 12 страниц по 120 мс последовательно — это ~1,4 с, а на четырёх
 * потоках — около 0,4 с. Время обхода видно в логе и в отчёте.
 *
 * <p>Сервер работает только в режиме Итогового ДЗ: при {@code --app.hw=hwNN} бин не создаётся, и
 * демонстрации прошлых заданий не занимают порт.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "final", matchIfMissing = true)
public class LocalVacancySource implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LocalVacancySource.class);

    /** Максимальное число страниц, которые отдаёт заглушка (защита от абсурдной настройки). */
    private static final int MAX_PAGES = 100;

    private final FinalTaskProperties properties;
    private final List<Sample> samples = samples();
    private final ExecutorService workers =
            Executors.newFixedThreadPool(8, FinalTaskConfig.namedThreadFactory(FinalTaskConfig.STUB_THREAD_PREFIX, true));

    private HttpServer server;
    private volatile boolean started;

    public LocalVacancySource(FinalTaskProperties properties) {
        this.properties = properties;
    }

    /** Поднять сервер, если демонстрационный источник включён. */
    @PostConstruct
    public void start() {
        if (!properties.getDemo().isEnabled()) {
            log.info("демонстрационный источник отключён (app.final.demo.enabled=false)");
            return;
        }
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            httpServer.setExecutor(workers);
            httpServer.createContext("/", this::handle);
            httpServer.start();
            this.server = httpServer;
            this.started = true;
            log.info("демонстрационный источник вакансий поднят: {} (страниц: {})",
                    baseUrl(), pageCount());
        } catch (IOException e) {
            workers.shutdownNow();
            throw new IllegalStateException("не удалось поднять локальный источник вакансий: " + e.getMessage(), e);
        }
    }

    /** Базовый адрес заглушки или {@code null}, если она отключена. */
    public String baseUrl() {
        return started ? "http://127.0.0.1:" + server.getAddress().getPort() : null;
    }

    /** Сколько страниц отдаёт заглушка. */
    public int pageCount() {
        if (!started) {
            return 0;
        }
        return Math.max(1, Math.min(MAX_PAGES, properties.getDemo().getPages()));
    }

    /** Адреса всех страниц — их ставит в очередь стартовый наполнитель и расписание. */
    public List<String> urls() {
        if (!started) {
            return List.of();
        }
        int pages = pageCount();
        List<String> urls = new ArrayList<>(pages);
        for (int index = 0; index < pages; index++) {
            urls.add(baseUrl() + "/vacancies/" + index);
        }
        return urls;
    }

    /** Остановить сервер и освободить порт. */
    @PreDestroy
    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
        workers.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || "/index.html".equals(path)) {
                respond(exchange, 200, indexPage());
                return;
            }
            if (path.startsWith("/vacancies/")) {
                int index = parseIndex(path.substring("/vacancies/".length()));
                if (index < 0 || index >= pageCount()) {
                    respond(exchange, 404, "<!DOCTYPE html><html><body><h1>404</h1></body></html>");
                    return;
                }
                applyDelay();
                respond(exchange, 200, page(index));
                return;
            }
            respond(exchange, 404, "<!DOCTYPE html><html><body><h1>404</h1></body></html>");
        } catch (IOException e) {
            // Клиент мог закрыть соединение по таймауту — для заглушки это нормальный исход.
        }
    }

    private void applyDelay() {
        long delay = properties.getDemo().getPageDelayMs();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int parseIndex(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void respond(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private String indexPage() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"utf-8\">")
                .append("<title>Демонстрационный источник вакансий</title></head><body>")
                .append("<h1>Демонстрационный источник вакансий</h1>")
                .append("<p>Страниц: ").append(pageCount()).append("</p><ul>");
        for (int index = 0; index < pageCount(); index++) {
            Sample sample = samples.get(index % samples.size());
            html.append("<li><a href=\"/vacancies/").append(index).append("\">")
                    .append(escapeHtml(sample.title()))
                    .append("</a> — ").append(escapeHtml(sample.company())).append("</li>");
        }
        html.append("</ul></body></html>");
        return html.toString();
    }

    private String page(int index) {
        Sample sample = samples.get(index % samples.size());
        int vacancyId = 1000 + index;
        String salaryJson = sample.salary() == null
                ? ""
                : ",\"baseSalary\":{\"@type\":\"MonetaryAmount\",\"currency\":\"RUB\","
                        + "\"value\":{\"@type\":\"QuantitativeValue\",\"value\":" + sample.salary() + "}}";

        return "<!DOCTYPE html>\n"
                + "<html lang=\"ru\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<title>" + escapeHtml(sample.title()) + " — вакансия</title>\n"
                + "<meta property=\"og:title\" content=\"" + escapeHtml(sample.title()) + "\">\n"
                + "<meta property=\"article:published_time\" content=\"" + sample.publishedAt() + "\">\n"
                + "<script type=\"application/ld+json\">\n"
                + "{\"@context\":\"https://schema.org\",\"@type\":\"JobPosting\","
                + "\"title\":\"" + escapeJson(sample.title()) + "\","
                + "\"datePosted\":\"" + sample.publishedAt() + "\","
                + "\"hiringOrganization\":{\"@type\":\"Organization\",\"name\":\""
                + escapeJson(sample.company()) + "\"},"
                + "\"jobLocation\":{\"@type\":\"Place\",\"address\":{\"@type\":\"PostalAddress\","
                + "\"addressLocality\":\"" + escapeJson(sample.city()) + "\"}}"
                + salaryJson
                + "}\n</script>\n</head>\n<body>\n<main>\n"
                + "<h1 data-qa=\"vacancy-title\">" + escapeHtml(sample.title()) + "</h1>\n"
                + "<a data-qa=\"vacancy-company-name\" href=\"/employer/" + vacancyId + "\">"
                + escapeHtml(sample.company()) + "</a>\n"
                + "<span data-qa=\"vacancy-salary\">" + escapeHtml(sample.salaryText()) + "</span>\n"
                + "<div data-qa=\"vacancy-view-location\">" + escapeHtml(sample.city()) + "</div>\n"
                + "<div data-qa=\"vacancy-description\"><p>" + escapeHtml(sample.requirements())
                + "</p><p>Оформление по ТК РФ, гибкий график, офис в центре города.</p></div>\n"
                + "</main>\n</body>\n</html>\n";
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Данные страницы: по одному образцу на страницу, при большем числе страниц образцы повторяются. */
    private record Sample(
            String title,
            String company,
            String salaryText,
            Integer salary,
            String city,
            String requirements,
            String publishedAt) {
    }

    private static List<Sample> samples() {
        return List.of(
                new Sample("Java-разработчик", "ООО «Ромашка»", "от 150 000 ₽", 150_000, "Москва",
                        "Java 21, Spring Boot, PostgreSQL, REST, опыт от 3 лет, знание многопоточности.",
                        "2026-09-21"),
                new Sample("Senior Java Developer", "АО «Техносфера»", "от 280 000 ₽", 280_000, "Санкт-Петербург",
                        "Java, Spring Framework, Kafka, микросервисы, менторство, английский B2.",
                        "2026-09-24"),
                new Sample("Backend-разработчик (Kotlin)", "ООО «Кодмастер»", "200 000 – 300 000 ₽", 200_000,
                        "Новосибирск",
                        "Kotlin, Spring Boot, Docker, Kubernetes, проектирование API, опыт от 2 лет.",
                        "2026-09-27"),
                new Sample("Middle Java Engineer", "ИП Иванов А. А.", "по договорённости", null, "Москва",
                        "Java 17+, Spring Data, Hibernate, SQL, Git, умение читать чужой код.",
                        "2026-09-30"),
                new Sample("DevOps-инженер", "ООО «Облако»", "от 220 000 ₽", 220_000, "Москва",
                        "Linux, Docker, CI/CD, Ansible, мониторинг, опыт эксплуатации highload-систем.",
                        "2026-10-02"),
                new Sample("Аналитик данных", "ООО «ДатаЛаб»", "от 120 000 ₽", 120_000, "Казань",
                        "SQL, Python, pandas, BI-отчётность, статистика, внимательность к деталям.",
                        "2026-10-05"),
                new Sample("Frontend-разработчик (React)", "ООО «Веб-Студия»", "180 000 – 230 000 ₽", 180_000,
                        "Екатеринбург",
                        "JavaScript, TypeScript, React, Redux, вёрстка, опыт от 2 лет.",
                        "2026-10-07"),
                new Sample("Python-разработчик", "АО «НейроТек»", "от 170 000 ₽", 170_000, "Москва",
                        "Python 3.12, FastAPI, asyncio, PostgreSQL, Redis, опыт от 3 лет.",
                        "2026-10-09"),
                new Sample("QA-инженер (автотесты)", "ООО «ТестПро»", "от 110 000 ₽", 110_000,
                        "Нижний Новгород",
                        "Selenium, JUnit 5, REST Assured, тест-дизайн, ведение тестовой документации.",
                        "2026-10-11"),
                new Sample("Системный администратор", "ООО «Сервер-Плюс»", "90 000 – 130 000 ₽", 90_000, "Самара",
                        "Windows Server, Linux, сети, резервное копирование, поддержка пользователей.",
                        "2026-10-13"),
                new Sample("Технический писатель", "ООО «ДокСервис»", "по договорённости", null, "Москва",
                        "Markdown, Git, UML, опыт описания API, грамотный русский язык.",
                        "2026-10-15"),
                new Sample("C++ разработчик", "АО «СофтВэй»", "от 200 000 ₽", 200_000, "Санкт-Петербург",
                        "C++20, STL, многопоточность, CMake, Linux, опыт от 4 лет.",
                        "2026-10-16"));
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "LocalVacancySource[%s, страниц %d]", baseUrl(), pageCount());
    }
}
