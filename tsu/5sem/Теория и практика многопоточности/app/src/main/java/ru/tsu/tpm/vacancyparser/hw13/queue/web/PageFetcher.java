package ru.tsu.tpm.vacancyparser.hw13.queue.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.PageResult;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.UrlTask;

/**
 * HTTP-загрузчик страниц для потребителей очереди.
 *
 * <p>Каждый вызов синхронный: он выполняется в потоке-потребителе, который специально освобождён от
 * другой работы. Задача очереди — распределить страницы между потребителями, а не сделать отдельный
 * запрос асинхронным.
 *
 * <p>Код ответа сохраняется в результате всегда: {@code 404} — это исход обработки, а не сбой
 * инфраструктуры. Сбоем считается обрыв соединения или таймаут — тогда {@code status = 0} и заполнено
 * поле ошибки. Потребитель в обоих случаях продолжает брать задачи: одна плохая страница не должна
 * останавливать конвейер.
 */
public final class PageFetcher implements PageSource, AutoCloseable {

    private final HttpClient client;
    private final long requestTimeoutMillis;
    private final AtomicInteger requests = new AtomicInteger();

    /** Создать загрузчик с таймаутами соединения и запроса. */
    public PageFetcher(long connectTimeoutMillis, long requestTimeoutMillis) {
        if (connectTimeoutMillis <= 0 || requestTimeoutMillis <= 0) {
            throw new IllegalArgumentException("таймауты должны быть положительными");
        }
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public PageResult fetch(UrlTask task) {
        long start = System.nanoTime();
        String consumer = Thread.currentThread().getName();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(task.url()))
                    .timeout(Duration.ofMillis(requestTimeoutMillis))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            requests.incrementAndGet();
            long duration = System.nanoTime() - start;

            if (response.statusCode() != 200) {
                return PageResult.failure(
                        task, response.statusCode(), "HTTP " + response.statusCode(), duration, consumer);
            }
            String title = TitleExtractor.extract(response.body());
            if (title.isEmpty()) {
                return PageResult.failure(task, response.statusCode(), "в ответе нет <title>", duration, consumer);
            }
            return PageResult.success(task, response.statusCode(), title, duration, consumer);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PageResult.failure(task, 0, "ожидание прервано", System.nanoTime() - start, consumer);
        } catch (IOException | IllegalArgumentException e) {
            return PageResult.failure(task, 0, describe(e), System.nanoTime() - start, consumer);
        }
    }

    /** Сколько запросов сделано. */
    public int requestCount() {
        return requests.get();
    }

    @Override
    public void close() {
        client.close();
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
