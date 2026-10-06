package ru.tsu.tpm.vacancyparser.hw08.pool;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Обработчик отказа: задача выполняется потоком, который её отправил, и каждое срабатывание считается.
 *
 * <p>Выбран осознанно (см. отчёт). При переполнении очереди задача <b>не теряется</b> и не превращается
 * в исключение, требующее обработки в каждом месте отправки: вызывающий поток сам выполняет работу.
 * Побочный эффект — обратный откат по потокам: если источник отправляет быстрее, чем пул успевает,
 * суммарное время прогона растёт, и это видно в сводке как плата за гарантию «ни одна задача не
 * потеряна», а не как дефект.
 *
 * <p>Наследование от {@link ThreadPoolExecutor.CallerRunsPolicy} сохранено, чтобы не дублировать её
 * логику (в том числе корректную проверку «пул уже остановлен»): здесь добавлен только счётчик.
 */
public final class CountingCallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy {

    /** Имя обработчика для вывода и отчёта. */
    public static final String NAME = "CallerRunsPolicy (выполнение вызывающим потоком)";

    private final AtomicLong rejections = new AtomicLong();

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        rejections.incrementAndGet();
        super.rejectedExecution(task, executor);
    }

    /** Сколько раз пул отказался принять задачу и она ушла вызывающему потоку. */
    public long rejections() {
        return rejections.get();
    }

    /** Имя обработчика для вывода. */
    public String name() {
        return NAME;
    }
}
