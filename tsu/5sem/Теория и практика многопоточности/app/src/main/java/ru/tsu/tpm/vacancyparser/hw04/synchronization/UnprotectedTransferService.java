package ru.tsu.tpm.vacancyparser.hw04.synchronization;

/**
 * Перевод без защиты — вторая половина сравнения.
 *
 * <p>Почему отдельный класс, а не флаг в {@link TransferService}: защиту перевода составляет сам
 * протокол захвата, а монитор нельзя «выключить флагом» — он либо есть в методе, либо его нет.
 * Поэтому сравниваются две реализации одного контракта: с протоколом (единый порядок захвата и
 * тайм-аут) и без него вовсе. Нагрузка, число потоков и сумма переводов при этом одинаковые.
 */
public final class UnprotectedTransferService {

    private UnprotectedTransferService() {
    }

    /** Перевести сумму без захвата счетов: прямое изменение двух балансов. */
    public static void transfer(UnprotectedAccount from, UnprotectedAccount to, long amount) {
        from.add(-amount);
        to.add(amount);
    }
}
