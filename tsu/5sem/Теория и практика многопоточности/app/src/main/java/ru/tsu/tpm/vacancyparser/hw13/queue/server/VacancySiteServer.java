package ru.tsu.tpm.vacancyparser.hw13.queue.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Локальный «сайт вакансий» для ДЗ 13: отдаёт HTML-страницы с заголовками.
 *
 * <p>Заглушка нужна, чтобы производители и потребители работали с настоящими адресами и настоящими
 * задержками, но без интернета. Страницы отдают код {@code 404} с регулярным шагом — так проверяется,
 * что потребитель различает «страница недоступна» и «задача потеряна».
 *
 * <p>Порт выбирает ОС ({@code 0}), поэтому повторные прогоны и тесты не конфликтуют за номер.
 */
public final class VacancySiteServer implements AutoCloseable {

    /** Сколько страниц отдаёт список по умолчанию. */
    public static final int DEFAULT_PAGES = 24;

    /** Шаг, с которым страница отвечает 404. */
    public static final int NOT_FOUND_STEP = 9;

    private static final String[] TITLES = {
            "Java-разработчик",
            "Backend-разработчик (Java)",
            "Разработчик Spring Boot",
            "Инженер по тестированию",
            "Аналитик данных",
            "Team Lead Java"
    };

    private static final String[] CITIES = {
            "Томск", "Новосибирск", "Москва", "Санкт-Петербург", "Екатеринбург", "Удалённо"
    };

    private final HttpServer server;
    private final ExecutorService workers;
    private final long pageDelayMillis;
    private final AtomicInteger requests = new AtomicInteger();

    private VacancySiteServer(HttpServer server, ExecutorService workers, long pageDelayMillis) {
        this.server = server;
        this.workers = workers;
        this.pageDelayMillis = pageDelayMillis;
    }

    /** Поднять сайт на свободном порту с задержкой отдачи страницы. */
    public static VacancySiteServer start(long pageDelayMillis) throws IOException {
        if (pageDelayMillis < 0) {
            throw new IllegalArgumentException("задержка страницы не может быть отрицательной");
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "hw13-site-worker");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(workers);

        VacancySiteServer site = new VacancySiteServer(server, workers, pageDelayMillis);
        server.createContext("/vacancy/", site::handlePage);
        server.createContext("/health", exchange -> site.respond(exchange, 200, "ok"));
        server.start();
        return site;
    }

    private void handlePage(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        int pageNumber;
        try {
            pageNumber = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            respond(exchange, 400, "<html><head><title>Ошибка запроса</title></head></html>");
            return;
        }

        if (pageNumber % NOT_FOUND_STEP == 4) {
            respond(exchange, 404, "<html><head><title>Страница не найдена</title></head></html>");
            return;
        }

        if (pageDelayMillis > 0) {
            try {
                Thread.sleep(pageDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        respond(exchange, 200, pageHtml(pageNumber));
    }

    /** HTML страницы вакансии — заголовок детерминирован номером страницы. */
    public static String pageHtml(int pageNumber) {
        return "<!DOCTYPE html>\n<html lang=\"ru\"><head><meta charset=\"utf-8\">\n"
                + "<title>" + expectedTitle(pageNumber) + "</title></head>\n"
                + "<body><h1>Вакансия №" + pageNumber + "</h1>\n"
                + "<p>Город: " + CITIES[pageNumber % CITIES.length] + "</p>\n"
                + "<p>Зарплата: " + (60_000 + pageNumber % 10 * 10_000) + " ₽</p>\n"
                + "</body></html>\n";
    }

    /** Ожидаемый заголовок страницы. */
    public static String expectedTitle(int pageNumber) {
        return "Вакансия №" + pageNumber + " — " + TITLES[pageNumber % TITLES.length];
    }

    /** Ожидаемый код ответа страницы. */
    public static int expectedStatus(int pageNumber) {
        return pageNumber % NOT_FOUND_STEP == 4 ? 404 : 200;
    }

    /** Список адресов страниц {@code /vacancy/0 .. /vacancy/(count-1)}. */
    public List<String> urls(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("число страниц не может быть отрицательным");
        }
        List<String> urls = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            urls.add(baseUrl() + "/vacancy/" + index);
        }
        return urls;
    }

    /** Базовый адрес сайта. */
    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    /** Фактический порт. */
    public int port() {
        return server.getAddress().getPort();
    }

    /** Сколько запросов обработал сайт. */
    public int requestCount() {
        return requests.get();
    }

    /** Живые потоки сайта (проверка, что после остановки ничего не осталось). */
    public static long liveThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("hw13-site-worker") && thread.isAlive())
                .count();
    }

    /** Остановить сайт и освободить порт. */
    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
    }

    private void respond(HttpExchange exchange, int code, String body) throws IOException {
        requests.incrementAndGet();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.getResponseHeaders().add("Content-Type",
                    String.format(Locale.ROOT, "text/html; charset=utf-8"));
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException e) {
            // Клиент мог закрыть соединение — для заглушки это нормальный исход.
        }
    }
}
