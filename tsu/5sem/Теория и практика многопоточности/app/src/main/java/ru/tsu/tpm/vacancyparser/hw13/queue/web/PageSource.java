package ru.tsu.tpm.vacancyparser.hw13.queue.web;

import ru.tsu.tpm.vacancyparser.hw13.queue.model.PageResult;
import ru.tsu.tpm.vacancyparser.hw13.queue.model.UrlTask;

/**
 * Источник страницы: то, что делает потребитель с взятой из очереди задачей.
 *
 * <p>Интерфейс отделён от HTTP-клиента, чтобы сценарий «производители—потребители» проверялся без сети:
 * в тестах источник — лямбда с управляемыми задержками и сбоями, в демонстрации — локальный
 * HTTP-сервер. Логика очереди от способа получения страницы не зависит.
 */
@FunctionalInterface
public interface PageSource {

    /** Скачать страницу и извлечь из неё данные. */
    PageResult fetch(UrlTask task) throws Exception;
}
