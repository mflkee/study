package ru.tsu.tpm.vacancyparser.hw05.locks;

/**
 * Явный признак того, что операция с буфером не выполнена.
 *
 * <p>Исключение непроверяемое намеренно: операции буфера вызываются из задач producer'ов и
 * consumer'ов, и оборачивать каждый вызов в {@code try/catch} было бы шумом. Но ошибка обязана быть
 * <b>громкой</b>: молчаливое «ничего не положил и промолчал» — худший исход для потокобезопасной
 * структуры, потому что потеря элемента обнаружится далеко от места ошибки.
 *
 * <p>Причина названа явно, а не выведена из текста: вызывающий код (и демонстрация) должен
 * различать «места не дождались», «работа окончена» и «поток прервали».
 */
public final class BufferOperationException extends RuntimeException {

    /** Почему операция не выполнена. */
    public enum Reason {
        /** За отведённое время состояние буфера не стало подходящим. */
        TIMEOUT("истёк срок ожидания"),
        /** Буфер закрыт: работа завершается, ждать больше нечего. */
        CLOSED("буфер закрыт"),
        /** Поток прерван во время ожидания; признак прерывания восстановлен. */
        INTERRUPTED("ожидание прервано");

        private final String description;

        Reason(String description) {
            this.description = description;
        }

        /** Как причина называется в выводе. */
        public String description() {
            return description;
        }
    }

    private final Reason reason;

    public BufferOperationException(Reason reason) {
        super("операция с буфером не выполнена: " + reason.description());
        this.reason = reason;
    }

    public BufferOperationException(Reason reason, Throwable cause) {
        super("операция с буфером не выполнена: " + reason.description(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
