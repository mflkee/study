package ru.tsu.tpm.vacancyparser.hw12.collections.cache;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import ru.tsu.tpm.vacancyparser.hw12.collections.model.VacancyCard;

/**
 * Запись кэша: карточка, срок жизни и номер версии.
 *
 * <p>{@code sequence} — не украшение: при проверке срока жизни нужно убедиться, что удаляется именно
 * эта версия записи. Пока {@code DelayQueue} держала ключ, запись могли перезаписать новой (свежий
 * срок), и удаление по одному имени выбросило бы актуальные данные. Сравнение версий это исключает.
 *
 * @param card           карточка вакансии
 * @param expiresAtNanos момент истечения срока жизни
 * @param sequence       номер версии записи
 */
public record CacheEntry(VacancyCard card, long expiresAtNanos, long sequence) {
}
