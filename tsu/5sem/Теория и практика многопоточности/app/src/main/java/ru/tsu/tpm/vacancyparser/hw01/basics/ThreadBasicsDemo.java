package ru.tsu.tpm.vacancyparser.hw01.basics;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.hw01.basics.ThreadBasicsService.WorkerGroup;

/**
 * Демонстрация ДЗ 1 «Основы потоков».
 *
 * <p>Запускается только при {@code --app.hw=hw01}. Порядок демонстрации выбран так, чтобы
 * каждый пункт чек-листа был виден в выводе буквально: создание потоков обоими способами,
 * именование и нумерация, список активных потоков с состояниями.
 *
 * <p>После завершения процесс завершается с кодом {@code 0} — иначе приложение осталось бы
 * висеть на поднятом HTTP-контексте. Без {@code --app.hw} демо не выполняется вовсе.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw01")
public class ThreadBasicsDemo implements ApplicationRunner {

    private final ThreadBasicsService service;
    private final ConsoleOutput console;

    public ThreadBasicsDemo(ThreadBasicsService service, ConsoleOutput console) {
        this.service = service;
        this.console = console;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        console.section("ДЗ 1. Основы потоков");
        console.info("Шаг 1. Создаём два потока двумя способами и запускаем их");
        console.info("Шаг 2. Наблюдаем список активных потоков, пока демонстрационные ещё живы");
        console.info("Шаг 3. Отпускаем потоки и дожидаемся их окончания через join()");
        console.raw("");

        WorkerGroup group = service.startWorkers();

        console.raw("Список активных потоков на момент наблюдения:");
        service.printActiveThreads();
        group.openGate();
        group.joinAll();

        service.printWorkerStates(group);

        console.info("Демонстрация завершена: оба способа создания потоков показаны, "
                + "имена и номера выведены, список активных потоков напечатан");
        console.raw("");

        System.exit(0);
    }
}
