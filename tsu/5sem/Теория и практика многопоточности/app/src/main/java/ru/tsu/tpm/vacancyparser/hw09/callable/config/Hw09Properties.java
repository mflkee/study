package ru.tsu.tpm.vacancyparser.hw09.callable.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки ДЗ 9 (префикс {@code app.hw09}).
 *
 * <p>Задаются в конфигурации и переопределяются аргументами запуска, чтобы демонстрацию можно было
 * ускорить без правки кода: {@code --app.hw09.period-seconds=1 --app.hw09.cycles=2}.
 */
@Component
@ConfigurationProperties(prefix = "app.hw09")
public class Hw09Properties {

    /** Период агрегатора, секунды. */
    private long periodSeconds = 2L;

    /** Сколько циклов агрегатора выполнить в демонстрации. */
    private int cycles = 3;

    public long getPeriodSeconds() {
        return periodSeconds;
    }

    public void setPeriodSeconds(long periodSeconds) {
        this.periodSeconds = periodSeconds;
    }

    public int getCycles() {
        return cycles;
    }

    public void setCycles(int cycles) {
        this.cycles = cycles;
    }
}
