package ru.tsu.tpm.vacancyparser.common;

/**
 * Сведения о текущем процессе JVM.
 *
 * <p>Диагностика зависших потоков внешними средствами (`jstack`, `jcmd`, `kill -3`) требует знать PID
 * процесса. Определять его вручную не нужно: {@link ProcessHandle#current()} даёт собственный PID
 * процесса, в котором работает приложение. Демонстрации печатают его, чтобы ручную команду можно было
 * повторить «как написано» — без подбора номера процесса.
 */
public final class ProcessInfo {

    private ProcessInfo() {
    }

    /** PID текущего процесса. */
    public static long currentPid() {
        return ProcessHandle.current().pid();
    }

    /** Строка с PID в принятом в отчётах формате. */
    public static String pidLine() {
        return "Процесс: pid " + currentPid();
    }
}
