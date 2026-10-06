package ru.tsu.tpm.vacancyparser.hw06.atomics;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Запуск {@link NonVolatileStopProcess} в отдельной JVM и разбор его вывода.
 *
 * <p>Используется и демонстрацией, и тестом: дефект видимости не должен жить в той же JVM, где идут
 * замеры и остальные проверки. Дочерний процесс запускается тем же {@code java}, что и текущий, а в
 * качестве classpath берётся расположение собственных классов — у демонстрационного процесса нет
 * внешних зависимостей, поэтому одного каталога сборки достаточно.
 */
public final class VisibilityDefectProbe {

    /** Предел времени на дочерний процесс: он обязан завершаться сам. */
    static final long PROCESS_TIMEOUT_SECONDS = 30L;

    /**
     * Итог запуска процесса-демонстрации.
     *
     * @param workerStopped остановился ли поток с {@code non-volatile} признаком
     * @param output        полный вывод дочернего процесса
     * @param elapsedNanos  время работы дочернего процесса
     */
    public record DefectOutcome(boolean workerStopped, String output, long elapsedNanos) {
    }

    private VisibilityDefectProbe() {
    }

    /** Запустить процесс-демонстрацию и дождаться его завершения. */
    public static DefectOutcome run() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                javaBinary(),
                "-Dfile.encoding=UTF-8",
                "-cp",
                classpath(),
                NonVolatileStopProcess.class.getName());
        builder.redirectErrorStream(true);

        long start = System.nanoTime();
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
        long elapsedNanos = System.nanoTime() - start;

        boolean stopped = output.contains(NonVolatileStopProcess.STOPPED_MARKER + "true");
        return new DefectOutcome(stopped, output, elapsedNanos);
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /** Расположение собственных классов: каталог сборки или jar, из которого запущено приложение. */
    static String classpath() {
        URL location = NonVolatileStopProcess.class.getProtectionDomain().getCodeSource().getLocation();
        try {
            return Paths.get(location.toURI()).toString();
        } catch (URISyntaxException e) {
            return Paths.get(location.getPath()).toString();
        }
    }
}
