package ru.tsu.tpm.vacancyparser.hw02.lifecycle;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;

/**
 * Демонстрация ДЗ 2 «Жизненный цикл потока».
 *
 * <p>Запускается только при {@code --app.hw=hw02}. Все демонстрационные потоки уже завершены
 * через {@code join()}, поэтому после печати сводки контекст закрывается сам: останавливается
 * Tomcat, и приложение штатно выходит с кодом {@code 0} — без {@code System.exit(0)}, как требует
 * design D6.
 *
 * <p><b>Признак неуспеха.</b> Если набор наблюдённых состояний оказался неполон, демонстрация
 * сообщает об этом в консоли и возвращает вызывающей стороне ненулевой код. Механизм Spring Boot
 * {@code ExitCodeGenerator} здесь не подходит: {@code SpringApplication} читает его только на пути
 * исключения, а при штатном возвращении из {@code ApplicationRunner.run()} код процесса берётся
 * из самого процесса. Поэтому ненулевой код выставляется явно — {@code System.exit(1)} вызывается
 * <b>только</b> в ветке неуспеха; штатное завершение остаётся без него и происходит естественно.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw02")
public class ThreadLifecycleDemo implements ApplicationRunner {

    /** Код выхода при неполном наборе наблюдённых состояний. */
    private static final int FAILURE_EXIT_CODE = 1;

    private final LifecycleObservationService service;
    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;

    public ThreadLifecycleDemo(
            LifecycleObservationService service, ConsoleOutput console, ConfigurableApplicationContext context) {
        this.service = service;
        this.console = console;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = report(service.observe(), console);
        console.raw("");

        context.close();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Сообщить итог демонстрации и вернуть код выхода.
     *
     * <p>Вынесено отдельно от {@link #run(ApplicationArguments)} намеренно: {@code run()} завершает
     * процесс, а проверить признак неуспеха нужно юнит-тестом, не убивая тестовую JVM.
     *
     * @return {@code 0} при полном наборе состояний, {@link #FAILURE_EXIT_CODE} при неполном
     */
    static int report(LifecycleObservationService.Observation observation, ConsoleOutput console) {
        if (observation.success()) {
            console.info("Демонстрация завершена: зафиксированы все шесть состояний потока, "
                    + "переходы и сводка напечатаны, все потоки завершены");
            return 0;
        }
        console.warn("Демонстрация завершена с неуспехом: набор наблюдённых состояний неполон, "
                + "недостигнуто: " + String.join("; ", observation.unreached()));
        return FAILURE_EXIT_CODE;
    }
}
