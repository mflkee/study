package ru.tsu.tpm.vacancyparser.hw13.queue.queue;

import java.util.Queue;
import java.util.function.Consumer;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.PageResult;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.UrlTask;
import ru.tsu.tpm.vacancyparser.hw13.queue.web.PageSource;

/**
 * Потребитель: берёт адрес из очереди, скачивает страницу, извлекает заголовок, кладёт результат.
 *
 * <p>Цикл заканчивается на сигнале остановки. Сбой загрузки или разбора не прерывает цикл: результат
 * записывается с признаком неуспеха, и потребитель берёт следующую задачу — иначе одна недоступная
 * страница остановила бы весь конвейер.
 *
 * <p>Результаты складываются в потокобезопасную очередь: у каждого потребителя свой поток, порядок
 * завершения не определён, а отчёт строится уже после остановки всех.
 */
public final class PageConsumer implements Runnable {

    private final TaskQueue queue;
    private final PageSource source;
    private final Queue<PageResult> results;
    private final Consumer<String> trace;

    /**
     * @param queue   общая очередь задач
     * @param source  источник страницы
     * @param results потокобезопасный приёмник результатов
     * @param trace   журнал (может быть {@code null})
     */
    public PageConsumer(TaskQueue queue, PageSource source, Queue<PageResult> results, Consumer<String> trace) {
        this.queue = queue;
        this.source = source;
        this.results = results;
        this.trace = trace;
    }

    @Override
    public void run() {
        while (true) {
            UrlTask task;
            try {
                task = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("прерван в ожидании задачи");
                return;
            }

            if (task.isPoison()) {
                log("получен сигнал остановки, выхожу");
                return;
            }

            PageResult result;
            try {
                result = source.fetch(task);
            } catch (Exception e) {
                result = PageResult.failure(task, 0, describe(e), 0L, Thread.currentThread().getName());
            }
            results.add(result);
            log("обработал " + task.reportLine() + " → " + (result.ok() ? "«" + result.title() + "»" : "сбой"));
        }
    }

    private void log(String message) {
        if (trace != null) {
            trace.accept("[" + Thread.currentThread().getName() + "] " + message);
        }
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
