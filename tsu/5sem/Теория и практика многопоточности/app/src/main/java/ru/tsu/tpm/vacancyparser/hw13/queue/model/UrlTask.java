package ru.tsu.tpm.vacancyparser.hw13.queue.model;

/**
 * Задача обхода: адрес страницы вакансии в очереди.
 *
 * <p>В очереди лежат именно задачи, а не готовые данные: producer кладёт «что скачать», consumer
 * решает «как скачать и что извлечь». Значение {@code pageNumber} нужно для отчёта и для проверки
 * «ни одна страница не потерялась и не обработана дважды».
 *
 * <p>Отдельный экземпляр {@link #POISON} — «таблетка» остановки: признак «задач больше не будет».
 * Сравнение идёт по идентичности ({@code ==}), поэтому никакой настоящий адрес с ней не спутается.
 *
 * @param url        адрес страницы
 * @param pageNumber номер страницы в исходном списке
 */
public record UrlTask(String url, int pageNumber) {

    /** Признак конца работы для потребителей. */
    public static final UrlTask POISON = new UrlTask("__poison__", -1);

    public UrlTask {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("адрес задачи не задан");
        }
    }

    /** Является ли задача сигналом остановки. */
    public boolean isPoison() {
        return this == POISON;
    }

    /** Строка для журнала. */
    public String reportLine() {
        return isPoison() ? "сигнал остановки" : "задача №" + pageNumber + " " + url;
    }
}
