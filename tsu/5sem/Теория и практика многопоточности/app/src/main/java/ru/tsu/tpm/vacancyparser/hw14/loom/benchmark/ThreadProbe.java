package ru.tsu.tpm.vacancyparser.hw14.loom.benchmark;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «Паспорт» потока: виртуальный он или платформенный и на каком carrier-потоке исполняется.
 *
 * <p>Виртуальный поток не привязан к потоку ОС: планировщик монтирует его на свободный
 * carrier-поток (обычный поток {@code ForkJoinPool}) на время работы и снимает на время блокировки.
 * В JDK 21 нет публичного метода «дай мне мой carrier», но {@link Thread#toString()} виртуального
 * потока содержит смонтированный carrier:
 *
 * <pre>{@code
 * VirtualThread[#42,hw14-vt-7]/runnable@ForkJoinPool-1-worker-3
 * }</pre>
 *
 * <p>Это и есть самый честный способ показать разницу между «виртуальный поток» и «поток ОС»: у
 * платформенного потока carrier — он сам, поэтому в его строке нет части {@code @...}, а у
 * виртуального она есть и меняется от блокировки к блокировке.
 *
 * @param name    имя потока
 * @param id      идентификатор потока
 * @param virtual поток виртуальный ({@link Thread#isVirtual()})
 * @param carrier имя carrier-потока; пусто у платформенного потока
 * @param state   состояние/статус из строкового представления (например, {@code runnable})
 */
public record ThreadProbe(String name, long id, boolean virtual, String carrier, String state) {

    private static final Pattern VIRTUAL =
            Pattern.compile("VirtualThread\\[#(\\d+),([^]]*)]/([^@]+)(?:@(.+))?");

    public ThreadProbe {
        name = name == null ? "" : name;
        carrier = carrier == null ? "" : carrier;
        state = state == null ? "" : state;
    }

    /** Снять «паспорт» с потока. */
    public static ThreadProbe of(Thread thread) {
        if (thread == null) {
            throw new IllegalArgumentException("поток не задан");
        }
        String text = thread.toString();
        String name = thread.getName();
        String state = "";
        String carrier = "";
        if (thread.isVirtual()) {
            Matcher matcher = VIRTUAL.matcher(text);
            if (matcher.matches()) {
                name = matcher.group(2);
                state = matcher.group(3);
                carrier = matcher.group(4) == null ? "" : matcher.group(4);
            }
        }
        // У платформенного потока (Thread[#21,main,5,main]) carrier-потока нет: он сам себе carrier,
        // а поле состояния в строке отсутствует — оставляем его пустым, чтобы не путать с приоритетом.
        return new ThreadProbe(name, thread.threadId(), thread.isVirtual(), carrier, state);
    }

    /** Смонтирован ли виртуальный поток на carrier в момент снятия «паспорта». */
    public boolean mounted() {
        return virtual && !carrier.isBlank();
    }

    /** Строка для журнала: имя потока и carrier. */
    public String label() {
        if (!virtual) {
            return name + " (платформенный)";
        }
        return name + (carrier.isBlank() ? " (снят с carrier)" : " @" + carrier);
    }

    /** Короткое описание для таблицы отчёта. */
    public String shortLabel() {
        return String.format(Locale.ROOT, "%s%s", virtual ? "virtual" : "platform",
                carrier.isBlank() ? "" : "@" + carrier);
    }
}
