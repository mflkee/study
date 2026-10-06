package ru.tsu.tpm.vacancyparser.hw07.deadlock.livelock;

/**
 * Детектор отсутствия продвижения: отличает livelock от обычной долгой работы.
 *
 * <p>Признак livelock — растёт число попыток, но не растёт число успешно завершённых итераций: потоки
 * активны, а результата нет. Если успешные итерации происходят, это обычная (пусть и долгая) работа, а
 * не livelock. Детектор намеренно простой: сравнить два счётчика, снятых на одном окне наблюдения.
 */
public final class LivelockDetector {

    /**
     * Вердикт детектора.
     *
     * @param livelock  подтверждено ли отсутствие продвижения
     * @param attempts  число попыток за окно
     * @param successes число успешных итераций за окно
     * @param reason    пояснение вердикта
     */
    public record Verdict(boolean livelock, long attempts, long successes, String reason) {
    }

    private LivelockDetector() {
    }

    /** Оценить продвижение по счётчикам попыток и успехов. */
    public static Verdict evaluate(long attempts, long successes) {
        if (attempts <= 0) {
            return new Verdict(false, attempts, successes, "попыток не было — наблюдать нечего");
        }
        if (successes == 0) {
            return new Verdict(
                    true,
                    attempts,
                    successes,
                    "попытки растут (" + attempts + "), успешных итераций нет — отсутствие продвижения");
        }
        return new Verdict(
                false, attempts, successes, "успешные итерации происходят (" + successes + ") — это обычная работа");
    }
}
