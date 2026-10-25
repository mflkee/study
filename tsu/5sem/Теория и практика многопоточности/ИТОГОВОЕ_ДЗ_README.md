# Итоговое домашнее задание — вариант 3: парсер вакансий

Многопоточный бэкенд на Spring Boot, который собирает вакансии со страниц, складывает их в H2 и
отдаёт через REST. Проверка **не требует интернета**: приложение само поднимает локальный
HTTP-источник со страницами вакансий, размечанными как на hh.ru.

- **Модуль:** `app/` (Spring Boot 4.1.1, Java 21, Maven)
- **Порт:** `8081`
- **Точка входа:** `ru.tsu.tpm.vacancyparser.VacancyParserApplication`
- **Пакет итогового задания:** `ru.tsu.tpm.vacancyparser.finaltask`

---

## 1. Быстрый старт

Требования: JDK 21+ и Maven 3.9+.

```bash
cd app
mvn spring-boot:run
```

Приложение готово, когда в логе появится строка `Started VacancyParserApplication`. Сразу после неё
выполняется стартовый обход — дождитесь строки `стартовый обход: адресов 12, успешно 12 ...` (обычно
1–2 секунды). После этого в H2 лежат 12 вакансий из локального источника.

Проверка в другом терминале:

```bash
curl -s "http://localhost:8081/final/answer?size=3"
```

Если порт 8081 занят:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
```

Остановка — `Ctrl+C`.

---

## 2. Сценарий проверки (3 шага)

### Шаг 1. Результаты уже есть

```bash
curl -s "http://localhost:8081/final/answer?page=0&size=5&sort=salary&direction=desc"
```

Ожидается JSON: `total = 12`, в `items` — вакансии по убыванию зарплаты, у каждой заполнены
`title`, `company`, `salary`, `city`, `requirements`, `publishedAt`, `fetchedAt`.

### Шаг 2. POST с адресами: запуск обхода

Адреса локального источника отдаёт эндпоинт `GET /final/source` (порт заглушки выбирается
случайно при каждом запуске, поэтому адреса нужно брать оттуда):

```bash
curl -s http://localhost:8081/final/source
```

Ответ содержит `baseUrl`, `pageCount` и массив `urls`. Пример запроса (подставьте свои адреса):

```bash
curl -s -X POST http://localhost:8081/final/posts \
  -H 'Content-Type: application/json' \
  -d '{"urls":["http://127.0.0.1:34567/vacancies/0","http://127.0.0.1:34567/vacancies/1","http://127.0.0.1:34567/vacancies/2"]}'
```

Или одной командой, без ручного копирования (нужен `jq`):

```bash
curl -s -X POST http://localhost:8081/final/posts \
  -H 'Content-Type: application/json' \
  -d "$(curl -s http://localhost:8081/final/source | jq -c '{urls: .urls}')"
```

В ответе (показан вариант со всеми 12 адресами из `urls`):

```json
{
  "acceptedUrls": 12,
  "duplicateUrls": 0,
  "savedFromBody": 0,
  "queueSize": 0,
  "crawl": {
    "runId": 2,
    "trigger": "POST /final/posts",
    "requested": 12,
    "succeeded": 12,
    "failed": 0,
    "saved": 0,
    "updated": 12,
    "durationMillis": 463.6,
    "workerThreads": ["final-crawl-1", "final-crawl-2", "final-crawl-3", "final-crawl-4"],
    "outcomes": [ ... ]
  },
  "message": "принято в очередь: 12; обход #2: успешно 12 из 12, сохранено 0, обновлено 12, время 463.6 мс"
}
```

`updated = 12` — потому что эти же адреса уже обходились на старте: повторный обход обновляет
записи, а не создаёт дубликаты.

### Шаг 3. Фильтр, сортировка и пагинация из H2

```bash
curl -s -G http://localhost:8081/final/answer \
  --data-urlencode "city=Москва" \
  -d "minSalary=150000" \
  -d "sort=salary" \
  -d "direction=desc" \
  -d "page=0" \
  -d "size=3"
```

Ожидается `total = 3` (Москва, зарплата ≥ 150 000: Java-разработчик, DevOps-инженер,
Python-разработчик) и три первых элемента по убыванию зарплаты.

---

## 3. Офлайн-проверка без источника (по желанию)

Можно не ждать обход и не использовать заглушку — прислать готовый HTML. Парсер разберёт его тем же
кодом:

```bash
curl -s -X POST http://localhost:8081/final/posts \
  -H 'Content-Type: application/json' \
  -d '{"url":"http://offline.example/java","html":"<html><head><title>Java-разработчик (офлайн)</title><meta property=\"article:published_time\" content=\"2026-10-05\"></head><body><h1 data-qa=\"vacancy-title\">Java-разработчик (офлайн)</h1><a data-qa=\"vacancy-company-name\">ООО Офлайн</a><span data-qa=\"vacancy-salary\">от 180 000 ₽</span><div data-qa=\"vacancy-view-location\">Томск</div><div data-qa=\"vacancy-description\"><p>Java 21, Spring Boot, H2</p></div></body></html>"}'
```

Ожидается `"savedFromBody": 1`, после чего:

```bash
curl -s -G http://localhost:8081/final/answer --data-urlencode "q=офлайн"
```

Или совсем без HTML — готовой вакансией:

```bash
curl -s -X POST http://localhost:8081/final/posts \
  -H 'Content-Type: application/json' \
  -d '{"vacancies":[{"url":"offline://manual/1","title":"Тестовая вакансия","company":"ООО Тест","salary":123456,"city":"Томск","requirements":"Проверка сохранения без сети","publishedAt":"2026-10-05"}]}'
```

---

## 4. Эндпоинты

| Метод | Путь | Назначение |
|---|---|---|
| `GET` | `/final` | карта эндпоинтов |
| `POST` | `/final/posts` | принять `{"urls":[...]}` и запустить обход; `{"url":...,"html":...}` — офлайн-разбор; `{"vacancies":[...]}` — сохранить готовые; `"crawl": false` — только в очередь |
| `GET` | `/final/answer` | результаты из H2: `page`, `size`, `sort`, `direction`, `city`, `q`, `minSalary` |
| `GET` | `/final/stats` | данные, очередь, пул, счётчики, отчёт последнего обхода |
| `GET` | `/final/source` | адреса локального демонстрационного источника |
| `POST` | `/final/crawl` | принудительный обход очереди (`?limit=N`); при пустой очереди — обход источника |
| `POST` | `/final/reset` | очистить H2 и очередь перед повторной проверкой |

`sort`: `id`, `title`, `company`, `salary`, `city`, `publishedAt`, `fetchedAt`.
`direction`: `asc`, `desc` (по умолчанию `desc`). `size` ограничен 200.

---

## 5. Где смотреть многопоточность

1. **Имена потоков в логе.** Каждая страница логируется потоком пула:

   ```
   обход #2: http://127.0.0.1:34567/vacancies/0 — «Java-разработчик», поток final-crawl-1, 124.7 мс
   обход #2: http://127.0.0.1:34567/vacancies/1 — «Senior Java Developer», поток final-crawl-3, 126.1 мс
   ...
   обход #2 (POST /final/posts): адресов 12, успешно 12, сбоев 0, сохранено 0, обновлено 12,
   время 463.6 мс, потоки: [final-crawl-1, final-crawl-2, final-crawl-3, final-crawl-4]
   ```

   Задержка страницы — 120 мс (`app.final.demo.page-delay-ms`), но с накладными расходами HTTP и разбора
   каждая страница занимает ~165 мс. Сумма времён по страницам ≈ 2 с, а весь обход — ~0,46 с: страницы
   обрабатывались одновременно четырьмя потоками, а не по очереди.

2. **`GET /final/stats`**: `poolSize`, `workerThreads`, `perThreadProcessed` — сколько страниц обработал
   каждый поток; `lastRun.outcomes[].thread` — какой поток обработал каждый адрес.

3. **Сравнение с одним потоком:**

   ```bash
   mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8082 --app.final.pool.size=1"
   ```

   Время обхода вырастет примерно вчетверо, а в `workerThreads` останется один `final-crawl-1`.

4. **Дамп потоков:** имена `final-crawl-N` видны в `jstack <pid>` (PID печатается Spring Boot в
   стартовом логе). Пул не демонический и корректно останавливается при закрытии контекста.

---

## 6. Расписание

Периодический обход очереди — `@Scheduled(fixedDelayString = "${app.final.schedule.fixed-delay-ms}")`,
класс `CrawlScheduler`. По умолчанию:

- первый цикл — через 5 с после старта (`initial-delay-ms=5000`);
- далее — каждые 20 с после завершения предыдущего (`fixed-delay-ms=20000`);
- за цикл берётся до 20 адресов (`batch-size=20`);
- если очередь пуста, расписание снова наполняет её адресами источника — так видно, что периодический
  обход работает, а не сработал один раз.

Изменить расписание без правки кода:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--app.final.schedule.fixed-delay-ms=5000"
```

или в `app/src/main/resources/application.properties`. В логе каждый цикл отмечается строкой
`расписание: обход #N — ...`.

---

## 7. H2: где данные

- Файл БД: `app/data/finaltask.mv.db` (каталог `data` создаётся при первом запуске рядом с рабочей
  директорией `mvn spring-boot:run`).
- URL: `jdbc:h2:file:./data/finaltask;AUTO_SERVER=TRUE`
- Схема создаётся Hibernate (`spring.jpa.hibernate.ddl-auto=update`), таблица — `vacancies`,
  уникальный индекс по `url`.
- Данные хранятся в файле и переживают перезапуск приложения. Записи, присланные через API
  (`offline://...` и обычные URL), сохраняются; демонстрационные записи заглушки при каждом старте
  пересобираются заново, потому что порт заглушки меняется (старые записи прошлых запусков удаляются
  автоматически, чтобы не копились дубликаты одной вакансии с разными портами).
- Посмотреть содержимое без остановки приложения (H2 shell из локального Maven-репозитория):

  ```bash
  java -cp "$(ls ~/.m2/repository/com/h2database/h2/*/h2-*.jar | head -1)" \
    org.h2.tools.Shell \
    -url "jdbc:h2:file:./data/finaltask;AUTO_SERVER=TRUE" -user sa -password ""
  ```

  ```sql
  SELECT id, title, company, salary, city, published_at FROM vacancies ORDER BY salary DESC;
  ```

- Полный сброс: `POST /final/reset` или удалить каталог с файлом БД (`rm -rf app/data` из корня
  курса, `rm -rf data` из каталога `app`).

---

## 8. Настройки (`app.final.*`)

| Параметр | По умолчанию | Смысл |
|---|---|---|
| `app.final.pool.size` | 4 | потоков в собственном пуле обхода |
| `app.final.pool.queue-capacity` | 64 | ёмкость очереди пула |
| `app.final.pool.shutdown-timeout-ms` | 5000 | ожидание задач при остановке пула |
| `app.final.http.connect-timeout-ms` | 2000 | таймаут соединения |
| `app.final.http.read-timeout-ms` | 5000 | таймаут чтения ответа |
| `app.final.crawl.total-timeout-ms` | 30000 | предел времени на весь обход |
| `app.final.schedule.initial-delay-ms` | 5000 | пауза до первого цикла расписания |
| `app.final.schedule.fixed-delay-ms` | 20000 | пауза между циклами |
| `app.final.schedule.batch-size` | 20 | адресов за цикл |
| `app.final.demo.enabled` | true | локальный источник страниц |
| `app.final.demo.pages` | 12 | число страниц источника |
| `app.final.demo.page-delay-ms` | 120 | задержка страницы (делает параллелизм измеримым) |
| `app.final.demo.crawl-on-startup` | true | обход сразу после старта |

---

## 9. Как устроено

```
finaltask/
├── config/   FinalTaskProperties, FinalTaskConfig (пул + @EnableScheduling)
├── model/    Vacancy (JPA), ParsedVacancy, CrawlReport, UrlOutcome
├── repo/     VacancyRepository (Spring Data JPA)
├── service/  PageFetcher (RestTemplate), HtmlVacancyParser,
│             CrawlQueue, VacancyCrawlService, VacancyStorageService,
│             VacancyQueryService, LocalVacancySource,
│             CrawlScheduler (@Scheduled), DemoDataInitializer
└── web/      FinalTaskController + dto/
```

Поток выполнения одного обхода:

1. `POST /final/posts` (или расписание/старт) кладёт адреса в `CrawlQueue` — `LinkedBlockingQueue`
   плюс `ConcurrentHashMap` ожидающих адресов, чтобы не дублировать очередь.
2. `VacancyCrawlService` берёт пачку и на каждый адрес создаёт `CompletableFuture.supplyAsync(...,
   crawlExecutor)`. Пул собран вручную (`ThreadPoolExecutor`, ограниченная `ArrayBlockingQueue`,
   `CallerRunsPolicy`, имена потоков `final-crawl-N`).
3. В потоках пула `PageFetcher` (RestTemplate, таймауты, UTF-8) скачивает страницу, а
   `HtmlVacancyParser` разбирает JSON-LD `JobPosting`, атрибуты `data-qa` и запасные `<h1>/<meta>`.
   Результаты складываются в `ConcurrentHashMap`.
4. После ожидания всех задач (`CompletableFuture.allOf` с общим таймаутом) `VacancyStorageService`
   одним потоком делает пакетный upsert в H2: `findByUrlIn` + `saveAll`.
5. `GET /final/answer` читает `findAll()` и делает фильтрацию и сортировку через `parallelStream()`.

Почему `RestTemplate`, а не `WebClient`: параллельность задаётся явным пулом, реактивный стек не нужен;
`RestTemplate` уже входит в `spring-boot-starter-web`, честно блокируется в потоке пула, и именно это
позволяет показать одновременную работу N потоков без второй модели исполнения и лишней зависимости.

Потокобезопасные структуры: `LinkedBlockingQueue`, `ConcurrentHashMap`, `CopyOnWriteArrayList`
(история обходов), `AtomicLong` (счётчики, в том числе по потокам), `ReentrantLock.tryLock()` —
запрет двух одновременных обходов (расписание и HTTP-запрос не дублируют работу).

---

## 10. Сборка и тесты

```bash
cd app
mvn -q -DskipTests package     # сборка исполняемого jar
mvn test                       # все тесты, включая hw01..hw09
```

Готовый jar: `app/target/vacancy-parser.jar`.

```bash
java -jar target/vacancy-parser.jar
```

Тесты итогового задания: `HtmlVacancyParserTest` (разбор HTML и зарплаты), `CrawlQueueTest`
(дубликаты, повторная постановка, 8 конкурентных постановщиков).

---

## 11. Ограничения и риски

- Парсер регэкспный и рассчитан на JSON-LD `JobPosting` и разметку `data-qa` (hh.ru-подобную). Для
  произвольной вёрстки потребуется заменить `HtmlVacancyParser` (например, на Jsoup) — остальной
  конвейер не меняется.
- Реальные сайты могут требовать заголовки, куки и защиту от ботов; демонстрация намеренно работает на
  локальном источнике, чтобы проверка была воспроизводимой и без сети.
- H2 файловая: `AUTO_SERVER=TRUE` позволяет подключиться к работающей БД внешним инструментом, но
  запускать два экземпляра приложения на одном каталоге `data/` не рекомендуется — они будут делить
  одну схему и мешать друг другу.
- Параллельные обходы запрещены: если расписание и POST совпали, один из них вернёт `"skipped": true`
  и пояснение — это защита от двойной нагрузки, а не ошибка.
- `GET /final/answer` читает все строки и фильтрует в памяти: для учебного объёма это нормально; на
  больших данных фильтры нужно вынести в SQL-запрос.
