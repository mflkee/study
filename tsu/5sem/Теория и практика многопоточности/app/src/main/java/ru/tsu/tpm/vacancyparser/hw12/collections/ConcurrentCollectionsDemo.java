package ru.tsu.tpm.vacancyparser.hw12.collections;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.common.ConsoleOutput;
import ru.tsu.tpm.vacancyparser.common.ProcessInfo;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.ThreadDump;
import ru.tsu.tpm.vacancyparser.hw04.synchronization.TimeBoxedExecution;
import ru.tsu.tpm.vacancyparser.hw12.collections.benchmark.ConcurrentBenchmarks;
import ru.tsu.tpm.vacancyparser.hw12.collections.cache.CacheStats;
import ru.tsu.tpm.vacancyparser.hw12.collections.cache.VacancyCache;
import ru.tsu.tpm.vacancyparser.hw12.collections.config.Hw12Properties;
import ru.tsu.tpm.vacancyparser.hw12.collections.events.VacancyEventBus;
import ru.tsu.tpm.vacancyparser.hw12.collections.model.VacancyCard;
import ru.tsu.tpm.vacancyparser.hw12.collections.ranking.SalaryRanking;
import ru.tsu.tpm.vacancyparser.hw12.collections.report.ConcurrentReport;

/**
 * Демонстрация ДЗ 12 «Многопоточные коллекции»: кэш вакансий и система событий.
 *
 * <p>Этапы: конкурентная загрузка кэша (проверка, что загрузчик вызывается по одному разу на ключ),
 * истечение срока жизни, вытеснение по ёмкости, доставка событий подписчикам, рейтинг на
 * {@code ConcurrentSkipListMap} и замеры «конкурентная коллекция против обычной с блокировкой».
 *
 * <p>Этапы 2 и 3 работают на отдельных экземплярах кэша: так счётчики истечений и вытеснений относятся
 * ровно к проверяемому сценарию, а не смешиваются с фоном первого этапа. Все кэши пишут события в одну
 * шину, поэтому журнал и счётчики по типам показывают суммарную картину.
 *
 * <p>Запускается только при {@code --app.hw=hw12}. Поток уборщика кэша и поток-диспетчер событий —
 * демоны; после демонстрации они останавливаются, и это проверяется числом живых потоков.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "hw12")
public class ConcurrentCollectionsDemo implements ApplicationRunner {

    /** Предел времени на всю демонстрацию. */
    static final long TIME_LIMIT_MILLIS = 180_000L;

    /** Код выхода, если демонстрация не уложилась в лимит. */
    static final int TIME_LIMIT_EXIT_CODE = 3;

    private static final String[] CITIES = {
            "Томск", "Новосибирск", "Москва", "Санкт-Петербург", "Екатеринбург", "Удалённо"
    };

    private final ConsoleOutput console;
    private final ConfigurableApplicationContext context;
    private final Hw12Properties properties;

    public ConcurrentCollectionsDemo(
            ConsoleOutput console, ConfigurableApplicationContext context, Hw12Properties properties) {
        this.console = console;
        this.context = context;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        console.section("ДЗ 12. Многопоточные коллекции: кэш вакансий и система событий");
        console.raw("");
        console.raw(ProcessInfo.pidLine());
        console.raw("Настройки ДЗ 12:");
        console.item("клиентов кэша: " + properties.getThreads() + ", ключей: " + properties.getKeys()
                + ", обращений на поток: " + properties.getOpsPerThread());
        console.item("срок жизни: " + properties.getTtlMillis() + " мс, ёмкость: "
                + properties.getCacheCapacity() + ", предел журнала событий: " + properties.getLogLimit());
        console.item("вытеснение проверяется на кэше ёмкостью " + properties.getEvictionCapacity()
                + " при " + properties.getEvictionInsertions() + " вставках");
        console.item("подписчиков на шине: " + properties.getSubscribers());
        console.item("замеры: " + properties.getBenchmarkThreads() + " потоков × "
                + properties.getBenchmarkOpsPerThread() + " операций, ключей "
                + properties.getBenchmarkKeys());

        TimeBoxedExecution.Outcome outcome;
        try {
            outcome = TimeBoxedExecution.run(TIME_LIMIT_MILLIS, this::runStages);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            console.warn("демонстрация прервана: " + e.getMessage());
            context.close();
            System.exit(TIME_LIMIT_EXIT_CODE);
            return;
        }

        console.raw("");
        if (outcome.completed() && outcome.failure() == null) {
            console.info("Демонстрация завершена: кэш и шина событий остановлены, потерь и расхождений нет");
        } else if (outcome.failure() != null) {
            console.warn("демонстрация завершилась ошибкой: " + outcome.failure());
        } else {
            console.warn("демонстрация не уложилась в " + TIME_LIMIT_MILLIS + " мс — снимаю дамп потоков");
            console.raw(ThreadDump.capture());
        }

        context.close();
        console.raw("");
        console.raw("живых потоков ДЗ 12 после завершения: " + ThreadDump.liveThreadsWithPrefixes(
                VacancyCache.SWEEPER_THREAD, VacancyEventBus.DISPATCHER_THREAD, "hw12-cache-client-"));
        if (!(outcome.completed() && outcome.failure() == null)) {
            System.exit(TIME_LIMIT_EXIT_CODE);
        }
    }

    private void runStages() {
        try {
            runStagesChecked();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("демонстрация прервана", e);
        }
    }

    private void runStagesChecked() throws InterruptedException {
        VacancyEventBus bus = new VacancyEventBus(properties.getLogLimit());
        List<AtomicLong> subscriberCounts = new ArrayList<>();
        List<Queue<Long>> subscriberOrders = new ArrayList<>();
        for (int index = 0; index < properties.getSubscribers(); index++) {
            AtomicLong counter = new AtomicLong();
            Queue<Long> order = new ConcurrentLinkedQueue<>();
            bus.subscribe(event -> {
                counter.incrementAndGet();
                order.add(event.sequence());
            });
            subscriberCounts.add(counter);
            subscriberOrders.add(order);
        }

        VacancyCache mainCache = new VacancyCache(
                properties.getCacheCapacity(), properties.getTtlMillis(), bus::publish);
        try {
            List<VacancyCard> knownCards = new CopyOnWriteArrayList<>();
            stageCacheUnderLoad(mainCache, knownCards);
            stageExpiration(bus, knownCards);
            stageEviction(bus, knownCards);
            stageEvents(bus, subscriberCounts, subscriberOrders);
            stageRanking(knownCards);
            stageBenchmarks();
            console.raw("");
            console.raw("Этап 7. Итоговое состояние основного кэша");
            console.raw("    " + ConcurrentReport.cacheLine(mainCache.stats()));
            console.raw("    основной кэш создавался со сроком жизни " + mainCache.ttlMillis()
                    + " мс, поэтому за время замеров его записи успели истечь");
        } finally {
            mainCache.close();
            bus.close();
        }
    }

    /** Этап 1. Много потоков одновременно просят одни и те же ключи. */
    private void stageCacheUnderLoad(VacancyCache cache, List<VacancyCard> knownCards)
            throws InterruptedException {
        console.raw("");
        console.raw("Этап 1. Конкурентная загрузка кэша: ConcurrentHashMap + compute");
        ConcurrentHashMap<String, AtomicInteger> loaderCalls = new ConcurrentHashMap<>();
        int threads = properties.getThreads();
        int keys = properties.getKeys();
        int operations = properties.getOpsPerThread();

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Thread> clients = new ArrayList<>(threads);
        for (int index = 0; index < threads; index++) {
            int threadIndex = index;
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int operation = 0; operation < operations; operation++) {
                        String id = "vac-" + ((operation + threadIndex) % keys);
                        cache.getOrLoad(id, key -> {
                            loaderCalls.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
                            sleep(properties.getLoaderDelayMillis());
                            VacancyCard card = cardFor(key);
                            knownCards.add(card);
                            return card;
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "hw12-cache-client-" + (index + 1));
            thread.setDaemon(true);
            clients.add(thread);
            thread.start();
        }

        ready.await();
        long startNanos = System.nanoTime();
        start.countDown();
        done.await();
        double durationMillis = (System.nanoTime() - startNanos) / 1_000_000.0;
        for (Thread client : clients) {
            client.join(1_000L);
        }

        int maxCalls = loaderCalls.values().stream().mapToInt(AtomicInteger::get).max().orElse(0);
        console.raw(String.format(
                Locale.ROOT,
                "    обращений %d за %.3f мс (%.0f оп/с), потоков %d",
                (long) threads * operations, durationMillis,
                threads * operations * 1_000.0 / durationMillis, threads));
        console.raw("    загрузчик вызван для " + loaderCalls.size() + " ключей из " + keys
                + ", максимум вызовов на ключ: " + maxCalls
                + " (однократная загрузка: " + marker(maxCalls == 1) + ")");
        console.raw("    " + ConcurrentReport.cacheLine(cache.stats()));
    }

    /** Этап 2. Записи исчезают по сроку жизни (отдельный кэш, чтобы счётчики были чистыми). */
    private void stageExpiration(VacancyEventBus bus, List<VacancyCard> knownCards)
            throws InterruptedException {
        console.raw("");
        console.raw("Этап 2. Истечение срока жизни: DelayQueue + поток-уборщик");
        int ttlKeys = properties.getTtlKeys();
        try (VacancyCache ttlCache = new VacancyCache(Math.max(4, ttlKeys * 2), properties.getTtlMillis(), (type, id) -> bus.publish(type, id))) {
            for (int index = 0; index < ttlKeys; index++) {
                VacancyCard card = new VacancyCard(
                        "ttl-" + index, "Временная вакансия " + index, "Томск", 90_000 + index * 1_000, 4.0);
                ttlCache.put(card);
                knownCards.add(card);
            }

            long deadline = System.nanoTime()
                    + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(properties.getTtlMillis() + 5_000L);
            long alive;
            do {
                alive = java.util.stream.IntStream.range(0, ttlKeys)
                        .filter(index -> ttlCache.peek("ttl-" + index).isPresent())
                        .count();
                if (alive == 0) {
                    break;
                }
                Thread.sleep(5L);
            } while (System.nanoTime() < deadline);

            boolean removed = ttlCache.awaitExpirations(ttlKeys, 3_000L);
            CacheStats stats = ttlCache.stats();
            console.raw("    положено " + ttlKeys + " записей со сроком жизни " + ttlCache.ttlMillis() + " мс");
            console.raw("    видимых записей после срока: " + alive + ", физически удалено уборщиком: "
                    + stats.expirations() + " (ожидалось " + ttlKeys + ", успех: " + marker(removed) + ")");
            console.raw("    ёмкость " + ttlCache.capacity() + ", записей осталось: " + stats.size());
        }
    }

    /** Этап 3. Кэш ограничен по ёмкости: старые записи вытесняются. */
    private void stageEviction(VacancyEventBus bus, List<VacancyCard> knownCards) {
        console.raw("");
        console.raw("Этап 3. Вытеснение по ёмкости: ConcurrentLinkedDeque порядка вставки");
        int capacity = properties.getEvictionCapacity();
        try (VacancyCache evictCache = new VacancyCache(capacity, properties.getTtlMillis(), (type, id) -> bus.publish(type, id))) {
            for (int index = 0; index < properties.getEvictionInsertions(); index++) {
                VacancyCard card = cardFor("evict-" + index);
                evictCache.put(card);
                knownCards.add(card);
            }
            CacheStats stats = evictCache.stats();
            console.raw("    добавлено " + properties.getEvictionInsertions() + " записей при ёмкости " + capacity);
            console.raw("    вытеснений: " + stats.evictions() + ", записей в кэше: " + stats.size()
                    + " (предел соблюдён: " + marker(stats.size() <= capacity) + ")");
            console.raw("    вытесняется ровно разница: " + properties.getEvictionInsertions()
                    + " − " + capacity + " = " + (properties.getEvictionInsertions() - capacity));
        }
    }

    /** Этап 4. Шина событий: доставка подписчикам, счётчики, одинаковый порядок. */
    private void stageEvents(
            VacancyEventBus bus, List<AtomicLong> subscriberCounts, List<Queue<Long>> subscriberOrders)
            throws InterruptedException {
        console.raw("");
        console.raw("Этап 4. Система событий: LinkedBlockingQueue + CopyOnWriteArrayList + ConcurrentHashMap");
        boolean flushed = bus.flush(10_000L);
        console.raw("    " + ConcurrentReport.eventSummary(
                bus.published(), bus.delivered(), bus.rejected()));
        console.raw("    доставка завершена: " + marker(flushed) + ", сбойных подписчиков: "
                + bus.subscriberFailures() + ", подписчиков: " + bus.subscriberCount());

        StringBuilder received = new StringBuilder();
        for (int index = 0; index < subscriberCounts.size(); index++) {
            if (received.length() > 0) {
                received.append(", ");
            }
            received.append("подписчик-").append(index + 1).append("=")
                    .append(subscriberCounts.get(index).get());
        }
        List<Long> firstOrder = new ArrayList<>(subscriberOrders.get(0));
        boolean sameOrder = subscriberOrders.stream()
                .allMatch(order -> new ArrayList<>(order).equals(firstOrder));
        console.raw("    получили: " + received);
        console.raw("    одинаковый порядок событий у всех подписчиков: " + marker(sameOrder)
                + " (рассылает один поток-диспетчер)");
        console.raw("    счётчики по типам: " + String.join("; ", ConcurrentReport.eventCountLines(bus.counts())));
        console.raw("    журнал последних событий: " + bus.recent().size() + " из предела " + bus.logLimit()
                + ", самое свежее: " + (bus.recent().isEmpty() ? "—" : bus.recent().get(0).reportLine()));
    }

    /** Этап 5. Топ вакансий по зарплате на ConcurrentSkipListMap. */
    private void stageRanking(List<VacancyCard> knownCards) {
        console.raw("");
        console.raw("Этап 5. Рейтинг по зарплате: ConcurrentSkipListMap + CopyOnWriteArrayList");
        SalaryRanking ranking = new SalaryRanking();
        knownCards.forEach(ranking::add);
        console.raw("    вакансий в рейтинге: " + ranking.size() + ", максимальная зарплата: "
                + ranking.highestSalary() + " ₽");
        console.raw("    топ-" + properties.getTopSize() + " по зарплате:");
        ConcurrentReport.rankingLines(ranking, properties.getTopSize())
                .forEach(line -> console.raw("      " + line));
    }

    /** Этап 6. Замеры: конкурентная коллекция против обычной с блокировкой (лучшее из двух прогонов). */
    private void stageBenchmarks() throws InterruptedException {
        console.raw("");
        console.raw("Этап 6. Замеры коллекций (90% чтений, 10% записей; лучшее из двух прогонов)");
        int threads = properties.getBenchmarkThreads();
        int keys = properties.getBenchmarkKeys();
        int ops = properties.getBenchmarkOpsPerThread();

        ConcurrentBenchmarks.MapRun concurrent = best(
                ConcurrentBenchmarks.concurrentMap(threads, keys, ops),
                ConcurrentBenchmarks.concurrentMap(threads, keys, ops));
        ConcurrentBenchmarks.MapRun synchronizedMap = best(
                ConcurrentBenchmarks.synchronizedMap(threads, keys, ops),
                ConcurrentBenchmarks.synchronizedMap(threads, keys, ops));
        console.raw("    " + concurrent.reportLine());
        console.raw("    " + synchronizedMap.reportLine());
        console.raw("    " + ConcurrentReport.mapComparison(concurrent, synchronizedMap));

        ConcurrentBenchmarks.CounterRun adder = best(
                ConcurrentBenchmarks.longAdder(threads, ops),
                ConcurrentBenchmarks.longAdder(threads, ops));
        ConcurrentBenchmarks.CounterRun atomic = best(
                ConcurrentBenchmarks.atomicLong(threads, ops),
                ConcurrentBenchmarks.atomicLong(threads, ops));
        console.raw("    " + adder.reportLine());
        console.raw("    " + atomic.reportLine());
        console.raw("    " + ConcurrentReport.counterComparison(adder, atomic));
    }

    /** Детерминированная карточка по идентификатору. */
    private static VacancyCard cardFor(String id) {
        int hash = Math.abs(id.hashCode());
        return new VacancyCard(
                id,
                "Вакансия " + id,
                CITIES[hash % CITIES.length],
                60_000 + hash % 190_000,
                3.5 + (hash % 15) / 10.0);
    }

    private static ConcurrentBenchmarks.MapRun best(
            ConcurrentBenchmarks.MapRun first, ConcurrentBenchmarks.MapRun second) {
        return first.durationNanos() <= second.durationNanos() ? first : second;
    }

    private static ConcurrentBenchmarks.CounterRun best(
            ConcurrentBenchmarks.CounterRun first, ConcurrentBenchmarks.CounterRun second) {
        return first.durationNanos() <= second.durationNanos() ? first : second;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String marker(boolean value) {
        return value ? "да" : "нет";
    }
}
