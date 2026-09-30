package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Перевод между счетами: захват двух ресурсов в одном порядке плюс тайм-аут.
 *
 * <p><b>Как предотвращается взаимная блокировка.</b> Две вещи, и обе обязательны:
 *
 * <ol>
 *   <li><b>Единый порядок захвата.</b> Все потоки захватывают счета в порядке возрастания
 *       идентификатора, независимо от того, куда переводится сумма. Взаимная блокировка возникает,
 *       когда один поток держит A и ждёт B, а другой держит B и ждёт A — при одном и том же порядке
 *       такой цикл невозможен: второй поток не может держать «младший» счёт, ожидая «старший»,
 *       если все начинают с младшего. Это правило работает всегда и не зависит от тайминга.</li>
 *   <li><b>Тайм-аут захвата.</b> Страховка на случай, когда порядок всё-таки нарушен (новый код,
 *       забытая ветка): вместо бесконечного ожидания операция возвращает явный признак неуспеха и
 *       освобождает уже взятые ресурсы. Тайм-аут не устраняет ошибку порядка, но не даёт ей
 *       превратиться в зависший процесс.</li>
 * </ol>
 *
 * <p>Порядок захвата реализован здесь, в доменном сервисе, а не в демонстрации: иначе
 * «предотвращение» существовало бы только на время показа и не защищало бы настоящие вызовы.
 *
 * <p><b>Граница с ДЗ 7.</b> Здесь взаимная блокировка только <i>предотвращается</i>. Намеренно
 * создать её, снять дамп через {@code jstack} и разобрать строку
 * «Found one Java-level deadlock» — задача ДЗ 7 (дедлайн 07.10.2026).
 */
public final class TransferService {

    /** Тайм-аут захвата по умолчанию: заведомо больше времени перевода, но конечный. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 200L;

    /** Почему перевод не состоялся. */
    public enum Failure {
        /** Перевод выполнен. */
        NONE,
        /** Один из счетов не освободился за отведённый тайм-аут. */
        LOCK_TIMEOUT,
        /** На счёте-источнике недостаточно средств. */
        INSUFFICIENT_FUNDS
    }

    /**
     * Итог попытки перевода.
     *
     * @param success выполнен ли перевод
     * @param failure причина при {@code success == false}
     */
    public record TransferResult(boolean success, Failure failure) {

        static TransferResult ok() {
            return new TransferResult(true, Failure.NONE);
        }

        static TransferResult failed(Failure failure) {
            return new TransferResult(false, failure);
        }
    }

    private final long timeoutMillis;

    /** Журнал порядка захвата: сюда пишется последовательность идентификаторов при каждой попытке. */
    private final List<String> acquisitionLog = Collections.synchronizedList(new ArrayList<>());

    public TransferService() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }

    public TransferService(long timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("тайм-аут не может быть отрицательным");
        }
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Порядок захвата двух счетов: по возрастанию идентификатора.
     *
     * <p>Идентификатор — строка, поэтому «возрастание» здесь лексикографическое: {@code "acc-10"}
     * идёт раньше {@code "acc-2"}. Для предотвращения взаимной блокировки важен не числовой
     * порядок, а то, что он <b>один и тот же во всех потоках</b>: любой глобально согласованный
     * порядок делает цикл ожидания невозможным. Поэтому правило названо явно и проверяется тестом,
     * а не подразумевается.
     *
     * <p>Чистая функция от пары счетов — именно поэтому все потоки получают один и тот же порядок
     * независимо от того, в каком порядке счета переданы в перевод.
     */
    public static List<Account> acquisitionOrder(Account first, Account second) {
        Objects.requireNonNull(first, "счёт-источник обязателен");
        Objects.requireNonNull(second, "счёт-получатель обязателен");
        return first.id().compareTo(second.id()) <= 0
                ? List.of(first, second)
                : List.of(second, first);
    }

    /** Тайм-аут, с которым работает сервис. */
    public long timeoutMillis() {
        return timeoutMillis;
    }

    /** Журнал порядка захвата — по нему проверяется, что все потоки захватывают ресурсы одинаково. */
    public List<String> acquisitionLog() {
        synchronized (acquisitionLog) {
            return List.copyOf(acquisitionLog);
        }
    }

    /** Очистить журнал. */
    public void clearLog() {
        acquisitionLog.clear();
    }

    /** Перевод с тайм-аутом по умолчанию. */
    public TransferResult transfer(Account from, Account to, long amount) throws InterruptedException {
        return transfer(from, to, amount, timeoutMillis);
    }

    /**
     * Перевести {@code amount} со счёта {@code from} на счёт {@code to}.
     *
     * <p>Захват идёт строго в порядке возрастания идентификатора; при неудаче уже взятые счета
     * освобождаются в обратном порядке. Возврат всегда явный: либо перевод выполнен, либо названа
     * причина отказа.
     *
     * @param amount сумма перевода; должна быть положительной
     */
    public TransferResult transfer(Account from, Account to, long amount, long timeoutMillis)
            throws InterruptedException {
        if (amount <= 0) {
            throw new IllegalArgumentException("сумма перевода должна быть положительной");
        }

        List<Account> ordered = acquisitionOrder(from, to);
        acquisitionLog.add(ordered.get(0).id() + " -> " + ordered.get(1).id());

        List<Account> held = new ArrayList<>(2);
        try {
            for (Account account : ordered) {
                if (!account.tryLock(timeoutMillis)) {
                    // Ресурс занят дольше тайм-аута: освобождаем всё, что уже взяли, и сообщаем
                    // о неуспехе. Именно это не даёт ожиданию превратиться в бесконечное.
                    return TransferResult.failed(Failure.LOCK_TIMEOUT);
                }
                held.add(account);
            }

            if (from.balanceUnderLock() < amount) {
                return TransferResult.failed(Failure.INSUFFICIENT_FUNDS);
            }

            from.applyUnderLock(-amount);
            to.applyUnderLock(amount);
            return TransferResult.ok();
        } finally {
            for (int i = held.size() - 1; i >= 0; i--) {
                held.get(i).unlock();
            }
        }
    }
}
