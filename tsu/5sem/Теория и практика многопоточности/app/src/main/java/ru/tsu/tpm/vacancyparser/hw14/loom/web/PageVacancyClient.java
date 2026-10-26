package ru.tsu.tpm.vacancyparser.hw14.loom.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.tsu.tpm.vacancyparser.hw14.loom.benchmark.ThreadProbe;
import ru.tsu.tpm.vacancyparser.hw14.loom.model.PageLoad;

/**
 * Синхронный HTTP-загрузчик страниц вакансий.
 *
 * <p>Ключевое слово — <b>синхронный</b>. В ДЗ 10 асинхронность строилась на {@code CompletableFuture},
 * а здесь намеренно оставлен блокирующий {@code HttpClient.send}: именно блокирующий вызов позволяет
 * виртуальному потоку сняться с carrier-потока и отдать его другим задачам. С асинхронным клиентом
 * выигрыш виртуальных потоков исчез бы — ждать было бы нечего.
 *
 * <p>Каждый результат несёт «паспорт» потока ({@link ThreadProbe}), поэтому по журналу видно: запрос
 * выполнял виртуальный поток {@code hw14-vt-7}, смонтированный на {@code ForkJoinPool-1-worker-3}.
 * Код ответа сохраняется всегда: HTTP-ошибка — это исход обработки, а не потеря задачи.
 */
public final class PageVacancyClient implements AutoCloseable {

    private final HttpClient client;
    private final long requestTimeoutMillis;
    private final AtomicInteger requests = new AtomicInteger();

    /** Создать загрузчик с таймаутами соединения и запроса. */
    public PageVacancyClient(long connectTimeoutMillis, long requestTimeoutMillis) {
        if (connectTimeoutMillis <= 0 || requestTimeoutMillis <= 0) {
            throw new IllegalArgumentException("таймауты должны быть положительными");
        }
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Загрузить страницу и извлечь заголовок. */
    public PageLoad fetch(String url, int pageNumber) {
        ThreadProbe worker = ThreadProbe.of(Thread.currentThread());
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(requestTimeoutMillis))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            requests.incrementAndGet();
            long duration = System.nanoTime() - start;

            if (response.statusCode() != 200) {
                return PageLoad.failure(
                        url, pageNumber, response.statusCode(), "HTTP " + response.statusCode(), duration, worker);
            }
            String title = HtmlTitle.extract(response.body());
            if (title.isEmpty()) {
                return PageLoad.failure(url, pageNumber, response.statusCode(), "в ответе нет <title>", duration, worker);
            }
            return PageLoad.success(url, pageNumber, response.statusCode(), title, duration, worker);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PageLoad.failure(url, pageNumber, 0, "ожидание прервано", System.nanoTime() - start, worker);
        } catch (IOException | IllegalArgumentException e) {
            return PageLoad.failure(url, pageNumber, 0, describe(e), System.nanoTime() - start, worker);
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

    /** Извлечение {@code <title>} из HTML: одна регулярка, без внешних библиотек. */
    public static final class HtmlTitle {

        private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE
                | Pattern.DOTALL);

        private HtmlTitle() {
        }

        /** Текст заголовка без тегов и лишних пробелов; пусто, если тега нет. */
        public static String extract(String html) {
            if (html == null || html.isEmpty()) {
                return "";
            }
            Matcher matcher = TITLE.matcher(html);
            if (!matcher.find()) {
                return "";
            }
            return normalize(matcher.group(1));
        }

        private static String normalize(String raw) {
            String text = raw.replaceAll("(?s)<[^>]*>", " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replaceAll("\\s+", " ")
                    .trim();
            return text;
        }
    }
}
