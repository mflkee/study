package ru.tsu.tpm.vacancyparser.hw04.synchronization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Предотвращение взаимных блокировок: единый порядок захвата и тайм-аут как страховка.
 */
class AccountTransferTest {

    private static final long TIMEOUT_MILLIS = 5_000L;

    @Test
    @DisplayName("Порядок захвата не зависит от того, куда идёт перевод")
    void acquisitionOrderIsAlwaysAscending() {
        Account first = new Account("acc-1", 1_000);
        Account second = new Account("acc-2", 1_000);

        assertThat(TransferService.acquisitionOrder(first, second))
                .as("перевод 1 → 2 и 2 → 1 обязаны давать один и тот же порядок")
                .containsExactly(first, second);
        assertThat(TransferService.acquisitionOrder(second, first)).containsExactly(first, second);
        assertThat(TransferService.acquisitionOrder(first, second))
                .isEqualTo(TransferService.acquisitionOrder(second, first));
    }

    @Test
    @DisplayName("Порядок захвата нормализуется для любой пары, включая обратную")
    void orderIsNormalizedForAnyPair() {
        Account early = new Account("acc-a", 0);
        Account late = new Account("acc-c", 0);

        assertThat(TransferService.acquisitionOrder(early, late).stream().map(Account::id))
                .containsExactly("acc-a", "acc-c");
        assertThat(TransferService.acquisitionOrder(late, early).stream().map(Account::id))
                .as("порядок аргументов не влияет на порядок захвата")
                .containsExactly("acc-a", "acc-c");
    }

    @Test
    @DisplayName("Сравнение идентификаторов лексикографическое — и это задокументировано, а не сюрприз")
    void orderComparisonIsLexicographic() {
        Account ten = new Account("acc-10", 0);
        Account two = new Account("acc-2", 0);

        assertThat(TransferService.acquisitionOrder(ten, two).stream().map(Account::id))
                .as("для предотвращения блокировки важен согласованный порядок, а не числовой; "
                        + "строка 'acc-10' лексикографически меньше 'acc-2'")
                .containsExactly("acc-10", "acc-2");
    }

    @Test
    @Timeout(30)
    @DisplayName("Встречные переводы из двух потоков: взаимной блокировки нет, суммы сохранены")
    void oppositeTransfersDoNotDeadlock() throws InterruptedException {
        Account first = new Account("acc-1", 1_000_000);
        Account second = new Account("acc-2", 1_000_000);
        long initialTotal = first.balance() + second.balance();
        TransferService service = new TransferService(1_000L);

        ConcurrentLoad.runIndexed(2, index -> {
            for (int i = 0; i < 5_000; i++) {
                try {
                    if (index == 0) {
                        service.transfer(first, second, 1L);
                    } else {
                        // Обратное направление: без единого порядка захвата это классический сценарий
                        // взаимной блокировки — потоки хватают счета в противоположном порядке.
                        service.transfer(second, first, 1L);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });

        assertThat(first.balance() + second.balance())
                .as("сумма балансов обязана сохраниться: сколько ушло, столько и пришло")
                .isEqualTo(initialTotal);
        assertThat(ThreadDump.hasDeadlock())
                .as("взаимная блокировка не должна возникнуть")
                .isFalse();
        assertEveryLoggedAcquisitionIsAscending(service);
    }

    @Test
    @Timeout(30)
    @DisplayName("Тайм-аут: занятый счёт не освободился — операция возвращает явный признак неуспеха")
    void timeoutReturnsExplicitFailure() throws InterruptedException {
        Account busy = new Account("acc-1", 1_000);
        Account other = new Account("acc-2", 1_000);
        TransferService service = new TransferService(150L);

        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            try {
                if (busy.tryLock(TIMEOUT_MILLIS)) {
                    occupied.countDown();
                    release.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    busy.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "account-holder");
        holder.setDaemon(true);
        holder.start();
        assertThat(occupied.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();

        long startedAt = System.nanoTime();
        TransferService.TransferResult result = service.transfer(busy, other, 100L);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        release.countDown();
        holder.join(TIMEOUT_MILLIS);

        assertThat(result.success()).isFalse();
        assertThat(result.failure())
                .as("причина отказа — именно тайм-аут захвата")
                .isEqualTo(TransferService.Failure.LOCK_TIMEOUT);
        assertThat(elapsedMillis)
                .as("ожидание ограничено тайм-аутом, а не бесконечно")
                .isLessThan(TIMEOUT_MILLIS);
        assertThat(busy.isLocked())
                .as("неудачная попытка не оставляет счёт занятым")
                .isFalse();
    }

    @Test
    @Timeout(30)
    @DisplayName("Неудачный захват освобождает уже взятый ресурс")
    void failedSecondAcquisitionReleasesTheFirst() throws InterruptedException {
        Account first = new Account("acc-1", 1_000);
        Account second = new Account("acc-2", 1_000);
        TransferService service = new TransferService(150L);

        // Держим ВТОРОЙ по порядку счёт: первый сервис успеет взять, второй — нет.
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            try {
                if (second.tryLock(TIMEOUT_MILLIS)) {
                    occupied.countDown();
                    release.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    second.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "second-account-holder");
        holder.setDaemon(true);
        holder.start();
        assertThat(occupied.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();

        TransferService.TransferResult result = service.transfer(first, second, 100L);

        release.countDown();
        holder.join(TIMEOUT_MILLIS);

        assertThat(result.failure()).isEqualTo(TransferService.Failure.LOCK_TIMEOUT);
        assertThat(first.isLocked())
                .as("первый счёт был взят и обязан быть освобождён в finally")
                .isFalse();

        // Проверяем, что счёт действительно свободен: его можно взять снова.
        assertThat(first.tryLock(500L)).isTrue();
        first.unlock();
    }

    @Test
    @Timeout(30)
    @DisplayName("Недостаточно средств — тоже явный отказ, и балансы не меняются")
    void insufficientFundsIsExplicitFailure() throws InterruptedException {
        Account poor = new Account("acc-1", 50);
        Account rich = new Account("acc-2", 1_000);
        TransferService service = new TransferService(500L);

        TransferService.TransferResult result = service.transfer(poor, rich, 100L);

        assertThat(result.success()).isFalse();
        assertThat(result.failure()).isEqualTo(TransferService.Failure.INSUFFICIENT_FUNDS);
        assertThat(poor.balance()).isEqualTo(50);
        assertThat(rich.balance()).isEqualTo(1_000);
    }

    @RepeatedTest(5)
    @DisplayName("Баланс сохраняется при многопоточном переводе в обе стороны")
    void balanceSumSurvivesConcurrentTransfers() throws InterruptedException {
        Account first = new Account("acc-1", 10_000);
        Account second = new Account("acc-2", 10_000);
        Account third = new Account("acc-3", 10_000);
        long initialTotal = first.balance() + second.balance() + third.balance();
        TransferService service = new TransferService(1_000L);
        AtomicInteger failures = new AtomicInteger();

        ConcurrentLoad.runIndexed(6, index -> {
            for (int i = 0; i < 2_000; i++) {
                try {
                    TransferService.TransferResult result = switch (index % 3) {
                        case 0 -> service.transfer(first, second, 1L);
                        case 1 -> service.transfer(second, third, 1L);
                        default -> service.transfer(third, first, 1L);
                    };
                    if (!result.success()) {
                        failures.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });

        assertThat(first.balance() + second.balance() + third.balance())
                .as("ни одно значение не потеряно и не создано из ничего")
                .isEqualTo(initialTotal);
        assertThat(ThreadDump.hasDeadlock()).isFalse();
        assertEveryLoggedAcquisitionIsAscending(service);
    }

    @Test
    @DisplayName("Сумма перевода должна быть положительной")
    void amountMustBePositive() {
        Account account = new Account("acc-1", 100);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TransferService().transfer(account, account, 0L));
    }

    @Test
    @DisplayName("Изменять и читать баланс в обход захвата нельзя")
    void balanceCannotBeTouchedWithoutLock() {
        Account account = new Account("acc-1", 100);

        assertThatIllegalStateException()
                .isThrownBy(() -> account.applyUnderLock(50))
                .withMessageContaining("только под захватом");
        assertThatIllegalStateException()
                .isThrownBy(account::balanceUnderLock)
                .withMessageContaining("только под захватом");
        assertThat(account.balance())
                .as("неудачная попытка не изменила баланс")
                .isEqualTo(100);
    }

    /**
     * Каждая запись журнала обязана быть парой «меньший идентификатор -> больший».
     *
     * <p>Проверяется именно это, а не «одна запись на всё»: при трёх счетах пар три, и каждая
     * обязана захватываться по возрастанию независимо от направления перевода.
     */
    private static void assertEveryLoggedAcquisitionIsAscending(TransferService service) {
        List<String> log = service.acquisitionLog();
        assertThat(log).as("журнал захватов не должен быть пустым").isNotEmpty();
        assertThat(log)
                .as("все потоки обязаны захватывать ресурсы в одном и том же порядке")
                .allSatisfy(entry -> {
                    String[] parts = entry.split(" -> ");
                    assertThat(parts).hasSize(2);
                    assertThat(parts[0].compareTo(parts[1]))
                            .as("запись журнала «%s» нарушает порядок возрастания", entry)
                            .isLessThan(0);
                });
    }
}
