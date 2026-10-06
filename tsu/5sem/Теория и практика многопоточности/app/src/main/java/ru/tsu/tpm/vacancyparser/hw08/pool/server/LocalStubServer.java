package ru.tsu.tpm.vacancyparser.hw08.pool.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Локальный HTTP-сервер-заглушка для воспроизводимого прогона ДЗ 8.
 *
 * <p>Поднимается на свободном порту (порт {@code 0} — ОС выбирает сама), отдаёт предсказуемые коды
 * ({@code 200}, {@code 404}, {@code 500}, {@code 503}) и управляемые задержки. Один маршрут отвечает
 * дольше таймаута запроса — на нём проверяется категория «таймаут». Заглушка нужна, чтобы демонстрация
 * и тесты не зависели от внешней сети и не «мигали» из-за ограничений публичных API.
 *
 * <p>{@link #urls()} возвращает ровно 20 адресов — это локальный список по умолчанию. {@link #stop()}
 * освобождает порт, поэтому повторный запуск тестов и демо не упирается в «адрес занят».
 */
public final class LocalStubServer implements AutoCloseable {

    /** Сколько адресов отдаёт локальный список по умолчанию. */
    public static final int LOCAL_URL_COUNT = 20;

    /** Индекс адреса с задержкой больше таймаута: на нём получается категория «таймаут». */
    public static final int SLOW_INDEX = LOCAL_URL_COUNT - 1;

    /** Задержка «медленного» маршрута по умолчанию — гарантированно больше типового таймаута. */
    public static final long DEFAULT_SLOW_DELAY_MILLIS = 5_000L;

    private static final String BODY = "stub response";

    private final HttpServer server;
    private final ExecutorService workers;
    private final long slowDelayMillis;

    private LocalStubServer(HttpServer server, ExecutorService workers, long slowDelayMillis) {
        this.server = server;
        this.workers = workers;
        this.slowDelayMillis = slowDelayMillis;
    }

    /** Поднять заглушку на свободном порту с задержкой «медленного» маршрута по умолчанию. */
    public static LocalStubServer start() throws IOException {
        return start(DEFAULT_SLOW_DELAY_MILLIS);
    }

    /** Поднять заглушку на свободном порту с заданной задержкой «медленного» маршрута. */
    public static LocalStubServer start(long slowDelayMillis) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "stub-worker");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(workers);

        LocalStubServer stub = new LocalStubServer(server, workers, slowDelayMillis);
        stub.registerRoutes();
        server.start();
        return stub;
    }

    private void registerRoutes() {
        server.createContext("/ok", exchange -> respond(exchange, 200, 0L));
        server.createContext("/not-found", exchange -> respond(exchange, 404, 0L));
        server.createContext("/error", exchange -> respond(exchange, 500, 0L));
        server.createContext("/unavailable", exchange -> respond(exchange, 503, 0L));
        server.createContext("/slow", exchange -> respond(exchange, 200, slowDelayMillis));

        for (int index = 0; index < LOCAL_URL_COUNT; index++) {
            int code = expectedStatus(index);
            long delay = index == SLOW_INDEX ? slowDelayMillis : (index % 4) * 10L;
            server.createContext("/local/" + index, exchange -> respond(exchange, code, delay));
        }
    }

    /** Ожидаемый код ответа локального маршрута с указанным индексом. */
    public static int expectedStatus(int index) {
        if (index % 5 == 2) {
            return 404;
        }
        if (index % 7 == 3) {
            return 500;
        }
        if (index % 11 == 5) {
            return 503;
        }
        return 200;
    }

    /** Отвечает ли маршрут дольше таймаута запроса. */
    public static boolean isSlow(int index) {
        return index == SLOW_INDEX;
    }

    private void respond(HttpExchange exchange, int code, long delayMillis) throws IOException {
        handled.incrementAndGet();
        try (exchange) {
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            byte[] bytes = BODY.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        } catch (IOException e) {
            // Клиент мог закрыть соединение по таймауту — для заглушки это нормальный исход.
        }
    }

    /** Фактический порт, на котором поднята заглушка. */
    public int port() {
        return server.getAddress().getPort();
    }

    /** Базовый адрес заглушки. */
    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    /** Список из 20 локальных адресов. */
    public List<String> urls() {
        List<String> urls = new ArrayList<>(LOCAL_URL_COUNT);
        for (int index = 0; index < LOCAL_URL_COUNT; index++) {
            urls.add(baseUrl() + "/local/" + index);
        }
        return urls;
    }

    /** Все живые потоки заглушки с заданным префиксом (для проверки, что ничего не осталось). */
    public static long liveThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("stub-worker") && thread.isAlive())
                .count();
    }

    /** Остановить заглушку и освободить порт. */
    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
    }

    /** Остановить заглушку: то же, что {@link #close()}, но читается как действие. */
    public void stop() {
        close();
    }

    /** Счётчик обработанных запросов (для проверки, что повторный тест не видит старый сервер). */
    public AtomicInteger requestCounter() {
        return handled;
    }

    private final AtomicInteger handled = new AtomicInteger();
}
