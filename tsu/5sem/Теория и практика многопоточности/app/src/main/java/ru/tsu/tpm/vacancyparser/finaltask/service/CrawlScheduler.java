package ru.tsu.tpm.vacancyparser.finaltask.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.finaltask.config.FinalTaskProperties;
import ru.tsu.tpm.vacancyparser.finaltask.model.CrawlReport;

/**
 * Периодический обход очереди по расписанию ({@code @Scheduled}).
 *
 * <p>Режим {@code fixedDelay}: следующий запуск отсчитывается от завершения предыдущего, поэтому
 * медленный обход не наслаивается сам на себя. Дополнительно {@link VacancyCrawlService} запрещает
 * параллельные обходы, так что даже запуск «вручную» через HTTP не создаёт двойную нагрузку.
 *
 * <p>Если очередь пуста, расписание снова наполняет её демонстрационными адресами: так проверяющий
 * видит, что периодический обход действительно работает, а не сработал один раз на старте. На реальном
 * источнике вместо этого стоял бы планировщик обхода лент вакансий.
 *
 * <p>Бин существует только в режиме Итогового ДЗ (по умолчанию); при {@code --app.hw=hwNN} расписание
 * не создаётся и не мешает демонстрациям прошлых заданий.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "final", matchIfMissing = true)
public class CrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrawlScheduler.class);

    private final VacancyCrawlService crawlService;
    private final CrawlQueue queue;
    private final LocalVacancySource source;
    private final FinalTaskProperties properties;

    public CrawlScheduler(
            VacancyCrawlService crawlService,
            CrawlQueue queue,
            LocalVacancySource source,
            FinalTaskProperties properties) {
        this.crawlService = crawlService;
        this.queue = queue;
        this.source = source;
        this.properties = properties;
    }

    /** Один цикл расписания: при необходимости наполнить очередь и обойти пачку адресов. */
    @Scheduled(
            initialDelayString = "${app.final.schedule.initial-delay-ms:5000}",
            fixedDelayString = "${app.final.schedule.fixed-delay-ms:20000}")
    public void crawlOnSchedule() {
        seedIfEmpty();
        CrawlReport report =
                crawlService.crawlQueue("расписание @Scheduled", properties.getSchedule().getBatchSize());
        if (report.requested() > 0) {
            log.info("расписание: обход #{} — адресов {}, успешно {}, сбоев {}, обновлено {}",
                    report.runId(), report.requested(), report.succeeded(), report.failed(), report.updated());
        } else if (!report.skipped()) {
            log.info("расписание: очередь пуста, следующий цикл через {} мс",
                    properties.getSchedule().getFixedDelayMs());
        }
    }

    private void seedIfEmpty() {
        if (!properties.getDemo().isEnabled() || queue.size() > 0) {
            return;
        }
        int added = queue.offerAll(source.urls());
        if (added > 0) {
            log.info("расписание: очередь была пуста, добавлено демонстрационных адресов: {}", added);
        }
    }
}
