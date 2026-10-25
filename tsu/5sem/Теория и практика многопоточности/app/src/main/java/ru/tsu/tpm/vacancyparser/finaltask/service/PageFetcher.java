package ru.tsu.tpm.vacancyparser.finaltask.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import ru.tsu.tpm.vacancyparser.finaltask.config.FinalTaskProperties;

/**
 * Загрузчик страниц через {@link RestTemplate}.
 *
 * <p><b>Почему RestTemplate, а не WebClient.</b> Параллельность в задании задаётся явно — собственным
 * пулом потоков, — поэтому реактивный стек не нужен: WebClient добавил бы вторую модель исполнения
 * (event loop) и лишнюю зависимость, ничего не изменив в измеримом результате. RestTemplate уже есть в
 * {@code spring-boot-starter-web}, честно блокируется на время ответа, и именно это позволяет показать
 * в логе, что N потоков пула одновременно ждут N разных страниц. Таймауты заданы явно: без них
 * «зависший» сайт занял бы поток пула навсегда.
 *
 * <p>Кодировка ответа принудительно UTF-8: страницы вакансий на русском, а
 * {@link StringHttpMessageConverter} по умолчанию может выбрать ISO-8859-1 и испортить текст.
 *
 * <p>Класс потокобезопасен: {@link RestTemplate} после настройки не меняется и может вызываться
 * одновременно из всех потоков пула.
 */
@Component
public class PageFetcher {

    private final RestTemplate restTemplate;

    public PageFetcher(FinalTaskProperties properties) {
        FinalTaskProperties.Http http = properties.getHttp();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(http.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(http.getReadTimeoutMs()));

        RestTemplate template = new RestTemplate(factory);
        template.getMessageConverters().removeIf(StringHttpMessageConverter.class::isInstance);
        template.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.restTemplate = template;
    }

    /** Загрузить страницу. Метод не бросает исключений: любой сбой возвращается как {@link FetchResult}. */
    public FetchResult fetch(String url) {
        long start = System.nanoTime();
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            long duration = System.nanoTime() - start;
            int status = response.getStatusCode().value();
            if (status < 200 || status >= 300) {
                return FetchResult.failure(url, status, response.getBody(), "HTTP " + status, duration);
            }
            return FetchResult.success(url, status, response.getBody(), duration);
        } catch (RestClientResponseException e) {
            // Неуспешный код — это полученный ответ, а не сетевая ошибка.
            return FetchResult.failure(
                    url,
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString(),
                    "HTTP " + e.getStatusCode().value(),
                    System.nanoTime() - start);
        } catch (ResourceAccessException e) {
            return FetchResult.failure(
                    url,
                    0,
                    null,
                    "сетевая ошибка или таймаут: " + describe(e),
                    System.nanoTime() - start);
        } catch (RuntimeException e) {
            return FetchResult.failure(
                    url,
                    0,
                    null,
                    "запрос не выполнен: " + describe(e),
                    System.nanoTime() - start);
        }
    }

    private static String describe(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
