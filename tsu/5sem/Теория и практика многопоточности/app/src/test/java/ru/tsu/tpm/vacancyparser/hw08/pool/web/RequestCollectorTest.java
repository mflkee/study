package ru.tsu.tpm.vacancyparser.hw08.pool.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.tsu.tpm.vacancyparser.hw08.pool.HttpWorkerPool;
import ru.tsu.tpm.vacancyparser.hw08.pool.config.RunConfig;
import ru.tsu.tpm.vacancyparser.hw08.pool.config.WorkMode;
import ru.tsu.tpm.vacancyparser.hw08.pool.server.LocalStubServer;

/**
 * Сбор результатов через пул: полнота, соответствие адресу, прогресс, порядок и отсутствие потерь.
 */
class RequestCollectorTest {

    private static RunConfig config(int core, int max, int queue) {
        return new RunConfig(
                WorkMode.LOCAL, List.of("http://local"), core, max, queue, 1_000L, 500L, 300L, 2_000L);
    }

    @Test
    @Timeout(60)
    @DisplayName("Число записей равно числу адресов, каждая запись соответствует своему адресу")
    void everyAddressGetsExactlyOneResult() throws IOException, InterruptedException {
        try (LocalStubServer stub = LocalStubServer.start()) {
            List<String> urls = stub.urls();
            RunConfig config = config(4, 8, 8).withUrls(urls);
            HttpWorkerPool pool = HttpWorkerPool.create(config);
            try {
                RunResult result = new RequestCollector(pool, requester(config), null).run(urls);

                assertThat(result.size()).isEqualTo(urls.size());
                for (int index = 0; index < urls.size(); index++) {
                    assertThat(result.at(index).url()).isEqualTo(urls.get(index));
                }
                assertThat(result.runNanos()).isPositive();
            } finally {
                pool.close();
            }
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Прогресс: 20 сообщений с возрастающим счётчиком, последнее — «20 из 20»")
    void progressIsPrinted() throws IOException, InterruptedException {
        try (LocalStubServer stub = LocalStubServer.start()) {
            List<String> urls = stub.urls();
            RunConfig config = config(4, 8, 8).withUrls(urls);
            HttpWorkerPool pool = HttpWorkerPool.create(config);
            List<String> progress = Collections.synchronizedList(new ArrayList<>());
            try {
                new RequestCollector(pool, requester(config), progress::add).run(urls);

                assertThat(progress).hasSize(20);
                assertThat(progress.get(progress.size() - 1)).contains("20 из 20");
                for (int i = 1; i <= 20; i++) {
                    assertThat(progress).contains("выполнено " + i + " из 20");
                }
            } finally {
                pool.close();
            }
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Порядок записей совпадает с порядком входного списка при двух прогонах")
    void orderMatchesInputList() throws IOException, InterruptedException {
        try (LocalStubServer stub = LocalStubServer.start()) {
            List<String> urls = stub.urls();
            RunConfig config = config(8, 8, 20).withUrls(urls);
            HttpWorkerPool pool = HttpWorkerPool.create(config);
            try {
                RunResult first = new RequestCollector(pool, requester(config), null).run(urls);
                RunResult second = new RequestCollector(pool, requester(config), null).run(urls);

                assertThat(first.results().stream().map(RequestResult::url).toList()).isEqualTo(urls);
                assertThat(second.results().stream().map(RequestResult::url).toList()).isEqualTo(urls);
            } finally {
                pool.close();
            }
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Перегрузка с малой очередью: ни одна задача не теряется")
    void noTaskIsLostUnderOverload() throws IOException, InterruptedException {
        try (LocalStubServer stub = LocalStubServer.start()) {
            List<String> urls = stub.urls();
            RunConfig config = config(1, 2, 2).withUrls(urls);
            HttpWorkerPool pool = HttpWorkerPool.create(config);
            List<String> progress = new CopyOnWriteArrayList<>();
            try {
                RunResult result = new RequestCollector(pool, requester(config), progress::add).run(urls);

                assertThat(result.size()).isEqualTo(20);
                assertThat(progress).hasSize(20);
                assertThat(result.rejectionCount())
                        .as("при малой очереди обработчик отказа обязан срабатывать")
                        .isPositive();
                assertThat(progress.get(progress.size() - 1)).contains("20 из 20");
            } finally {
                pool.close();
            }
        }
    }

    private static HttpRequester requester(RunConfig config) {
        return new HttpRequester(config.connectTimeoutMillis(), config.requestTimeoutMillis());
    }
}
