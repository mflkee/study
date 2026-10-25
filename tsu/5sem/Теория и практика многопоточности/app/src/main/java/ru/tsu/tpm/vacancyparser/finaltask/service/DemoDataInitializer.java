package ru.tsu.tpm.vacancyparser.finaltask.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.tsu.tpm.vacancyparser.finaltask.config.FinalTaskProperties;
import ru.tsu.tpm.vacancyparser.finaltask.model.CrawlReport;

/**
 * Стартовый наполнитель: поднимает данные без единого действия проверяющего.
 *
 * <p>После запуска приложение само ставит в очередь адреса локального источника и (по настройке
 * {@code app.final.demo.crawl-on-startup}) сразу выполняет первый обход. Поэтому последовательность
 * проверки сводится к «запустить и открыть {@code /final/answer}»: к моменту готовности приложения в H2
 * уже лежат вакансии.
 *
 * <p>Наполнитель активен только в режиме Итогового ДЗ. При {@code --app.hw=hwNN} он не создаётся.
 */
@Component
@ConditionalOnProperty(name = "app.hw", havingValue = "final", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final LocalVacancySource source;
    private final CrawlQueue queue;
    private final VacancyCrawlService crawlService;
    private final VacancyStorageService storage;
    private final FinalTaskProperties properties;

    public DemoDataInitializer(
            LocalVacancySource source,
            CrawlQueue queue,
            VacancyCrawlService crawlService,
            VacancyStorageService storage,
            FinalTaskProperties properties) {
        this.source = source;
        this.queue = queue;
        this.crawlService = crawlService;
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getDemo().isEnabled()) {
            log.info("стартовый наполнитель пропущен: app.final.demo.enabled=false");
            return;
        }
        List<String> urls = source.urls();
        if (urls.isEmpty()) {
            log.warn("стартовый наполнитель: локальный источник не поднят, очередь остаётся пустой");
            return;
        }
        int removed = storage.deleteStaleDemoVacancies();
        if (removed > 0) {
            log.info("стартовый наполнитель: удалено записей прошлых запусков заглушки: {}", removed);
        }
        int added = queue.offerAll(urls);
        log.info("стартовый наполнитель: источник {}, в очередь добавлено адресов: {}",
                source.baseUrl(), added);

        if (!properties.getDemo().isCrawlOnStartup()) {
            log.info("стартовый обход отключён — данные появятся после первого цикла расписания");
            return;
        }
        CrawlReport report = crawlService.crawlQueue("старт приложения", urls.size());
        log.info("стартовый обход: адресов {}, успешно {}, сбоев {}, сохранено {}, обновлено {}, время {} мс",
                report.requested(), report.succeeded(), report.failed(),
                report.saved(), report.updated(), report.durationMillis());
    }
}
