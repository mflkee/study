package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

class ThreadStateObserverTest {

    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
    private final ConsoleOutput console =
            new ConsoleOutput(new PrintStream(sink, true, StandardCharsets.UTF_8));
    private final ThreadStateObserver observer = new ThreadStateObserver(console);

    @Test
    @DisplayName("Повторное наблюдение того же состояния не создаёт нового перехода")
    void repeatedStateProducesSingleRecord() {
        observer.record("Worker", Thread.State.NEW);
        boolean secondTime = observer.record("Worker", Thread.State.NEW);
        boolean thirdTime = observer.record("Worker", Thread.State.NEW);

        assertThat(secondTime).as("состояние не менялось — перехода быть не должно").isFalse();
        assertThat(thirdTime).isFalse();
        assertThat(observer.transitions())
                .as("три одинаковых наблюдения дают ровно одну запись")
                .hasSize(1);
    }

    @Test
    @DisplayName("Смена состояния создаёт вторую запись с предыдущим и новым состояниями")
    void stateChangeProducesSecondRecord() {
        observer.record("Worker", Thread.State.NEW);
        boolean changed = observer.record("Worker", Thread.State.RUNNABLE);

        assertThat(changed).isTrue();
        assertThat(observer.transitions()).hasSize(2);

        ThreadStateObserver.Transition second = observer.transitions().get(1);
        assertThat(second.threadName()).isEqualTo("Worker");
        assertThat(second.previous()).isEqualTo(Thread.State.NEW);
        assertThat(second.current()).isEqualTo(Thread.State.RUNNABLE);
        assertThat(second.at()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
    }

    @Test
    @DisplayName("Первое наблюдение помечается отсутствием предыдущего состояния")
    void firstObservationHasNoPreviousState() {
        observer.record("Worker", Thread.State.NEW);

        ThreadStateObserver.Transition first = observer.transitions().get(0);
        assertThat(first.previous()).isNull();
        assertThat(first.previousLabel()).contains("не наблюдалось");
        assertThat(console.captured()).contains("не наблюдалось").contains("NEW");
    }

    @Test
    @DisplayName("Хронология переходов упорядочена по времени и не переставляется")
    void transitionsAreChronological() {
        observer.record("A", Thread.State.NEW);
        observer.record("B", Thread.State.NEW);
        observer.record("A", Thread.State.RUNNABLE);
        observer.record("B", Thread.State.TERMINATED);

        List<ThreadStateObserver.Transition> transitions = observer.transitions();
        assertThat(transitions).hasSize(4);
        for (int i = 1; i < transitions.size(); i++) {
            assertThat(transitions.get(i).at())
                    .as("время переходов не должно убывать")
                    .isGreaterThanOrEqualTo(transitions.get(i - 1).at());
        }
    }

    @Test
    @DisplayName("На неполном наборе из пяти состояний недостающим помечается шестое")
    void incompleteSetReportsMissingState() {
        for (Thread.State state : List.of(
                Thread.State.NEW,
                Thread.State.RUNNABLE,
                Thread.State.WAITING,
                Thread.State.BLOCKED,
                Thread.State.TERMINATED)) {
            observer.record("Worker", state);
        }

        assertThat(observer.missingStates())
                .as("не зафиксировано ровно одно состояние — TIMED_WAITING")
                .containsExactly(Thread.State.TIMED_WAITING);
    }

    @Test
    @DisplayName("На полном наборе список недостающих пуст")
    void completeSetHasNoMissingStates() {
        for (Thread.State state : ThreadStateObserver.ALL_STATES) {
            observer.record("Worker", state);
        }

        assertThat(observer.missingStates()).isEmpty();
        assertThat(observer.allObservedStates()).containsExactlyInAnyOrderElementsOf(ThreadStateObserver.ALL_STATES);
    }

    @Test
    @DisplayName("Сводка печатает набор состояний по каждому потоку отдельно")
    void summaryPrintsPerThreadSets() {
        observer.record("A", Thread.State.NEW);
        observer.record("A", Thread.State.TERMINATED);
        observer.record("B", Thread.State.NEW);
        observer.record("B", Thread.State.RUNNABLE);
        observer.record("B", Thread.State.TIMED_WAITING);

        observer.printSummary();

        String output = console.captured();
        assertThat(output).contains("A → NEW, TERMINATED");
        assertThat(output).contains("B → NEW, RUNNABLE, TIMED_WAITING");
        assertThat(observer.statesOf("A")).containsExactlyInAnyOrder(Thread.State.NEW, Thread.State.TERMINATED);
        assertThat(observer.statesOf("B")).containsExactlyInAnyOrder(
                Thread.State.NEW, Thread.State.RUNNABLE, Thread.State.TIMED_WAITING);
    }

    @Test
    @DisplayName("Все шесть состояний печатаются в сводке как не зафиксированные, когда их нет")
    void summaryWarnsWhenNothingObserved() {
        observer.printSummary();

        assertThat(console.captured())
                .contains("ВНИМАНИЕ")
                .contains("NEW, RUNNABLE, WAITING, BLOCKED, TIMED_WAITING, TERMINATED");
    }

    @Test
    @DisplayName("Наблюдатель берёт состояние из фактического getState() потока")
    void observationReadsActualThreadState() throws InterruptedException {
        CountDownLatch parked = new CountDownLatch(1);
        Thread real = new Thread(() -> {
            synchronized (parked) {
                parked.countDown();
                try {
                    parked.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "RealWorker");
        real.start();
        assertThat(parked.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            observer.register(real);
            observer.observe(real);

            assertThat(observer.statesOf("RealWorker")).containsExactly(Thread.State.WAITING);
            assertThat(observer.transitions().get(0).current())
                    .as("записано должно быть фактическое состояние, а не предполагаемое")
                    .isEqualTo(real.getState());
        } finally {
            real.interrupt();
            real.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    @Test
    @DisplayName("Подмена состояния в сводке расходится с фактическим и ломает проверку")
    void injectedStateIsDetectedAgainstActualState() throws InterruptedException {
        CountDownLatch parked = new CountDownLatch(1);
        Thread real = new Thread(() -> {
            synchronized (parked) {
                parked.countDown();
                try {
                    parked.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "HonestWorker");
        real.start();
        assertThat(parked.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            observer.register(real);
            observer.observe(real);
            assertThat(observer.verifyAgainstActualStates())
                    .as("до подмены расхождений быть не должно")
                    .isEmpty();

            observer.record("HonestWorker", Thread.State.BLOCKED);

            assertThat(observer.statesOf("HonestWorker"))
                    .as("в сводке появится состояние, которого поток не достигал")
                    .contains(Thread.State.BLOCKED);
            assertThat(observer.verifyAgainstActualStates())
                    .as("подмена обязана быть обнаружена сверкой с getState()")
                    .hasSize(1)
                    .allSatisfy(mismatch -> assertThat(mismatch)
                            .contains("HonestWorker")
                            .contains("BLOCKED")
                            .contains(real.getState().name()));
        } finally {
            real.interrupt();
            real.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    @Test
    @DisplayName("Проверка сообщает о незарегистрированном потоке")
    void verificationReportsUnregisteredThread() {
        observer.record("Ghost", Thread.State.NEW);

        assertThat(observer.verifyAgainstActualStates())
                .as("без зарегистрированного потока сверить не с чем")
                .hasSize(1)
                .allSatisfy(mismatch -> assertThat(mismatch).contains("не зарегистрирован"));
    }
}
