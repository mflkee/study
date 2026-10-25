package ru.tsu.tpm.vacancyparser.finaltask.web.dto;

import java.util.List;

/**
 * Сведения о демонстрационном источнике страниц.
 *
 * @param baseUrl   базовый адрес локального сервера-заглушки
 * @param pageCount число страниц
 * @param urls      адреса страниц (их можно передать в {@code POST /final/posts})
 * @param note      пояснение
 */
public record SourceInfo(String baseUrl, int pageCount, List<String> urls, String note) {
}
