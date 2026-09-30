package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

/**
 * Поток без фазы ожидания — самый простой случай.
 *
 * <p>Единственная фаза «ожидания» — её отсутствие. Набор наблюдённых состояний:
 * {@code NEW} → {@code RUNNABLE} → {@code TERMINATED}. Нужен как базовое звено: он доказывает,
 * что состояния не подклеиваются, а читаются, и служит контрастом к потокам с примитивами
 * ожидания.
 */
public final class LifecycleStartWorker extends LifecycleWorker {

    public LifecycleStartWorker(
            String name, RunnableWindow workWindow, RunnableWindow finishWindow, long safetyMillis) {
        super(name, workWindow, finishWindow, safetyMillis);
    }

    @Override
    protected void awaitWaitPhase() {
        // Ожиданий нет намеренно: поток сразу переходит к финальному окну работы.
    }
}
