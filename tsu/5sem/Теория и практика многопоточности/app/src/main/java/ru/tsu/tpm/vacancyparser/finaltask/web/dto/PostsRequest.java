package ru.tsu.tpm.vacancyparser.finaltask.web.dto;

import java.util.List;

/**
 * Тело запроса {@code POST /final/posts}.
 *
 * <p>Все поля необязательны, и это сделано намеренно — один эндпоинт закрывает три сценария проверки:
 * <ul>
 *   <li>{@code urls} — поставить адреса в очередь и сразу обойти их (обычный режим);</li>
 *   <li>{@code url + html} — разобрать присланный HTML без сети (офлайн-проверка парсера);</li>
 *   <li>{@code vacancies} — сохранить готовые вакансии без сети и без парсинга.</li>
 * </ul>
 *
 * @param urls      адреса страниц вакансий
 * @param url       адрес для пары с {@code html}
 * @param html      HTML страницы для офлайн-разбора
 * @param vacancies готовые вакансии для сохранения
 * @param crawl     выполнить обход сразу; {@code false} — только поставить в очередь
 */
public record PostsRequest(
        List<String> urls,
        String url,
        String html,
        List<VacancyInput> vacancies,
        Boolean crawl) {
}
