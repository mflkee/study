package ru.tsu.tpm.vacancyparser.hw08.pool.web;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Асинхронный HTTP-клиент из JDK ({@link HttpClient}) с классификацией исхода.
 *
 * <p>Запрос отправляется через {@code sendAsync} — то есть настоящей асинхронной отправкой, — а
 * задача пула дожидается результата. Клиент несёт собственные таймауты соединения и запроса, которые
 * и задают границу между «ответ получен» и «таймаут». Внешних зависимостей нет: всё из JDK.
 *
 * <p>Классификация — главная ценность этого класса. Ответ с кодом {@code 404}/{@code 5xx} — это
 * {@link OutcomeCategory#RESPONSE}; таймаут, отказ соединения и неразрешённое имя — разные категории.
 * Ошибка транспорта не должна выглядеть как ответ сервера, и наоборот.
 */
public final class HttpRequester {

    private final HttpClient client;
    private final long connectTimeoutNanos;
    private final long requestTimeoutNanos;
    private final long requestTimeoutMillis;

    /** Создать клиент с заданными таймаутами соединения и запроса. */
    public HttpRequester(long connectTimeoutMillis, long requestTimeoutMillis) {
        this.connectTimeoutNanos = connectTimeoutMillis * 1_000_000L;
        this.requestTimeoutNanos = requestTimeoutMillis * 1_000_000L;
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Выполнить запрос к одному адресу и вернуть классифицированный результат. */
    public RequestResult fetch(String url) {
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(requestTimeoutMillis))
                    .GET()
                    .build();
            HttpResponse<String> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get();
            return RequestResult.response(url, response.statusCode(), System.nanoTime() - start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RequestResult.interrupted(url, System.nanoTime() - start, "ожидание прервано");
        } catch (ExecutionException e) {
            return classifyFailure(url, e.getCause(), start);
        } catch (IllegalArgumentException e) {
            return RequestResult.rejected(url, "некорректный адрес: " + e.getMessage());
        } catch (RuntimeException e) {
            return RequestResult.rejected(url, "отказ: " + e.getMessage());
        }
    }

    /** Разнести сбой по категориям: таймаут, сетевая ошибка, прочее. */
    RequestResult classifyFailure(String url, Throwable cause, long start) {
        if (cause instanceof HttpConnectTimeoutException) {
            return RequestResult.timeout(url, connectTimeoutNanos, "таймаут соединения");
        }
        if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
            return RequestResult.timeout(url, requestTimeoutNanos, "таймаут запроса");
        }
        if (cause instanceof UnknownHostException) {
            return RequestResult.networkError(url, System.nanoTime() - start, "имя не разрешено: " + cause.getMessage());
        }
        if (cause instanceof ConnectException
                || cause instanceof NoRouteToHostException
                || cause instanceof SocketException) {
            return RequestResult.networkError(url, System.nanoTime() - start, "соединение не установлено: " + cause.getMessage());
        }
        if (cause instanceof java.io.IOException) {
            return RequestResult.networkError(url, System.nanoTime() - start, cause.getClass().getSimpleName());
        }
        return RequestResult.rejected(url, "непредвиденный сбой: " + cause);
    }
}
