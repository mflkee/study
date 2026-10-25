package ru.tsu.tpm.vacancyparser.hw12.collections.events;

/**
 * Событие кэша: что произошло, с какой вакансией, в каком потоке и когда.
 *
 * <p>Событие неизменяемо, поэтому его можно безопасно раздавать нескольким подписчикам одновременно.
 * {@link #sequence} выдаётся атомарным счётчиком и задаёт порядок публикации: по нему видно, что
 * подписчики получили события в том же порядке, в котором они возникли.
 *
 * @param sequence      порядковый номер события
 * @param type          тип события
 * @param vacancyId     идентификатор вакансии
 * @param thread        поток, в котором событие возникло
 * @param timestampNanos монотонное время возникновения
 */
public record VacancyEvent(long sequence, EventType type, String vacancyId, String thread, long timestampNanos) {

    /** Служебное событие остановки рассылки. */
    static final VacancyEvent STOP = new VacancyEvent(-1L, null, null, null, 0L);

    /** Является ли событие служебным сигналом остановки. */
    public boolean isStop() {
        return this == STOP;
    }

    /** Строка для журнала и отчёта. */
    public String reportLine() {
        if (isStop()) {
            return "остановка рассылки";
        }
        return "#" + sequence + " " + type.label() + " " + vacancyId + " (" + thread + ")";
    }
}
