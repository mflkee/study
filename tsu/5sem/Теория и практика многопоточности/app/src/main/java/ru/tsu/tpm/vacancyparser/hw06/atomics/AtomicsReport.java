package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Формирование вывода ДЗ 6.
 *
 * <p>Как и в ДЗ 3–5, печать отделена от измерений: строки — чистые функции от результатов, поэтому
 * их можно проверить, не запуская нагрузку, а печать не попадает в измеряемый участок.
 */
public final class AtomicsReport {

    private AtomicsReport() {
    }

    /** Итог остановки рабочего потока по {@code volatile}-признаку. */
    public static List<String> flagStopBlock(FlagStopScenario.StopOutcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add("    рабочий поток: " + FlagStopScenario.WORKER_NAME);
        lines.add(String.format(
                Locale.ROOT,
                "    итераций до запроса завершения: %d, ожидание завершения: %d мс",
                outcome.iterations(),
                outcome.waitMillis()));
        lines.add("    поток завершился по volatile-флагу: " + marker(outcome.stoppedInTime()));
        if (outcome.dump() != null) {
            lines.add("    поток НЕ завершился в отведённое время — дамп потоков:");
            outcome.dump().lines().forEach(line -> lines.add("        " + line));
        } else {
            lines.add("    запас времени не исчерпан, дамп потоков не потребовался");
        }
        return lines;
    }

    /** Итог проверки инварианта счётчика. */
    public static List<String> counterInvariantBlock(int threads, int operationsPerThread, long value, long expected) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(
                Locale.ROOT,
                "    потоков: %d, операций на поток: %d, ожидалось: %d",
                threads,
                operationsPerThread,
                expected));
        lines.add(String.format(
                Locale.ROOT,
                "    фактическое значение: %d, расхождений: %d — %s",
                value,
                expected - value,
                value == expected ? "инвариант выполнен" : "ИНВАРИАНТ НАРУШЕН"));
        return lines;
    }

    /** Конкурентное создание значения кэша: корректная реализация против учебного дефекта. */
    public static List<String> cacheBlock(CacheRaceRunner.RaceOutcome correct, CacheRaceRunner.RaceOutcome broken) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(String.format(Locale.ROOT, "    потоков, одновременно обратившихся к пустому кэшу: %d", correct.threads()));
        lines.add(String.format(
                Locale.ROOT,
                "    корректная реализация (compareAndSet): созданий = %d, вызовов фабрики = %d, "
                        + "различных экземпляров у потоков = %d",
                correct.creations(),
                correct.factoryInvocations(),
                correct.distinctInstances()));
        lines.add(String.format(
                Locale.ROOT,
                "    учебный дефект (check-then-act): созданий = %d, различных экземпляров у потоков = %d",
                broken.creations(),
                broken.distinctInstances()));
        lines.add("    корректная реализация создала значение ровно один раз: " + marker(correct.createdOnce())
                + ", все потоки получили один экземпляр: " + marker(correct.singleInstance()));
        lines.add("    дефектная реализация создала значение более одного раза: "
                + marker(broken.creations() > 1) + ", экземпляры различались: "
                + marker(broken.distinctInstances() > 1));
        return lines;
    }

    /** Дефект видимости: поток без {@code volatile} не увидел установленный флаг. */
    public static List<String> volatileDefectBlock(VisibilityDefectProbe.DefectOutcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("    отдельный процесс: поток с признаком БЕЗ volatile");
        for (String line : outcome.output().lines().toList()) {
            if (!line.isBlank()) {
                lines.add("        " + line);
            }
        }
        lines.add("    поток увидел установленный флаг: " + marker(outcome.workerStopped())
                + " (false = «зомби»-цикл, дефект видимости воспроизведён)");
        lines.add(String.format(Locale.ROOT, "    время дочернего процесса: %.3f мс", millis(outcome.elapsedNanos())));
        return lines;
    }

    /** Нагрузочный прогон по трём механизмам сразу. */
    public static List<String> mechanismsBlock(AtomicsBenchmark.MechanismsOutcome outcome) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(String.format(
                Locale.ROOT, "    потоков: %d, операций на поток: %d", outcome.threads(), outcome.operationsPerThread()));
        lines.add(String.format(
                Locale.ROOT,
                "    остановка по volatile-флагу: завершился = %s, итераций = %d, ожидание = %d мс",
                marker(outcome.stopStoppedInTime()),
                outcome.stopIterations(),
                outcome.stopWaitNanos() / 1_000_000L));
        lines.add(String.format(
                Locale.ROOT,
                "    AtomicInteger: значение = %d из %d (%s)",
                outcome.atomicValue(),
                outcome.atomicExpected(),
                outcome.counterExact() ? "точно" : "ПОТЕРИ"));
        lines.add(String.format(
                Locale.ROOT,
                "    singleton-кэш: созданий = %d, различных экземпляров = %d (%s)",
                outcome.cacheCreations(),
                outcome.cacheDistinctInstances(),
                outcome.cacheCreatedOnce() ? "один экземпляр" : "ДЕФЕКТ"));
        lines.add(String.format(Locale.ROOT, "    время прогона: %.3f мс", millis(outcome.elapsedNanos())));
        lines.add("    живых потоков демонстрации после прогона: " + outcome.liveThreads());
        return lines;
    }

    /** Сравнение трёх вариантов счётчика: все прогоны, а не только лучший. */
    public static List<String> counterComparisonBlock(AtomicsBenchmark.CounterComparison comparison) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Сравнение механизмов счётчика (одинаковая нагрузка, один процесс):");
        lines.add(String.format(
                Locale.ROOT, "    потоков: %d, операций на поток: %d, ожидаемый итог: %d",
                comparison.threads(), comparison.operationsPerThread(), comparison.expected()));

        for (CounterVariant variant : CounterVariant.values()) {
            List<Long> nanos = comparison.nanosFor(variant.label());
            lines.add("    " + variant.label() + ":");
            for (AtomicsBenchmark.CounterRun run : comparison.runsFor(variant.label())) {
                lines.add(String.format(
                        Locale.ROOT,
                        "        %9.3f мс, счётчик %d из %d%s",
                        millis(run.nanos()),
                        run.value(),
                        run.expected(),
                        run.exact() ? "" : " (потеряно " + run.lost() + ")"));
            }
            lines.add(String.format(
                    Locale.ROOT,
                    "        среднее %.3f мс, минимум %.3f мс, максимум %.3f мс",
                    millis(average(nanos)),
                    millis(nanos.stream().mapToLong(Long::longValue).min().orElse(0L)),
                    millis(nanos.stream().mapToLong(Long::longValue).max().orElse(0L))));
        }
        return lines;
    }

    /**
     * Вывод о различии механизмов, привязанный к измеренным числам.
     *
     * <p>Формулировка намеренно оставляет место для «разницы не показано» и никогда не говорит
     * «атомарные всегда быстрее»: разница зависит от числа потоков и наличия реальной конкуренции.
     */
    public static String conclusion(AtomicsBenchmark.CounterComparison comparison) {
        String monitor = CounterVariant.MONITOR.label();
        String atomic = CounterVariant.ATOMIC.label();
        List<Long> monitorNanos = comparison.nanosFor(monitor);
        List<Long> atomicNanos = comparison.nanosFor(atomic);
        if (monitorNanos.isEmpty() || atomicNanos.isEmpty()) {
            return "недостаточно замеров для вывода";
        }

        double monitorAverage = average(monitorNanos);
        double atomicAverage = average(atomicNanos);
        long monitorMin = monitorNanos.stream().mapToLong(Long::longValue).min().orElse(0L);
        long monitorMax = monitorNanos.stream().mapToLong(Long::longValue).max().orElse(0L);
        long atomicMin = atomicNanos.stream().mapToLong(Long::longValue).min().orElse(0L);
        long atomicMax = atomicNanos.stream().mapToLong(Long::longValue).max().orElse(0L);
        boolean overlaps = monitorMin <= atomicMax && atomicMin <= monitorMax;

        if (overlaps) {
            return String.format(
                    Locale.ROOT,
                    "на этой машине при %d потоках разница между монитором и AtomicInteger "
                            + "укладывается в разброс повторов (%.3f мс против %.3f мс), то есть "
                            + "по времени выполнения они равноценны",
                    comparison.threads(),
                    millis((long) monitorAverage),
                    millis((long) atomicAverage));
        }
        String faster = atomicAverage < monitorAverage ? "AtomicInteger" : "монитор";
        double ratio = Math.max(monitorAverage, atomicAverage) / Math.min(monitorAverage, atomicAverage);
        return String.format(
                Locale.ROOT,
                "на этой машине при %d потоках быстрее %s — %.2f× (%.3f мс против %.3f мс)",
                comparison.threads(),
                faster,
                ratio,
                millis((long) monitorAverage),
                millis((long) atomicAverage));
    }

    /** Отдельный вывод о дефекте {@code volatile}-счётчика и о корректности остальных вариантов. */
    public static String volatileVerdict(AtomicsBenchmark.CounterComparison comparison) {
        List<AtomicsBenchmark.CounterRun> broken = comparison.runsFor(CounterVariant.VOLATILE_BROKEN.label());
        long worstLoss = broken.stream().mapToLong(AtomicsBenchmark.CounterRun::lost).max().orElse(0L);
        return String.format(
                Locale.ROOT,
                "volatile-счётчик потерял обновления в %d из %d прогонов (максимальная потеря %d из %d); "
                        + "монитор и AtomicInteger точны во всех прогонах",
                broken.stream().filter(run -> !run.exact()).count(),
                broken.size(),
                worstLoss,
                comparison.expected());
    }

    private static String marker(boolean ok) {
        return ok ? "да" : "НЕТ";
    }

    private static double average(List<Long> nanos) {
        return nanos.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double millis(double nanos) {
        return nanos / 1_000_000.0;
    }
}
