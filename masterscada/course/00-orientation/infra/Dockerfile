# Образ стенда: postgres:16 + русская локаль.
#
# Зачем: вендор инициализирует кластер так —
#   pg-setup initdb --tune=1c --locale=ru_RU.UTF-8   (ust_postgresql_linux.html)
# Локаль влияет на сортировку/сравнение текста (ORDER BY по русским именам объектов, отчёты,
# выборки из архивов — модули 02 и 07). В базовом образе ru_RU.UTF-8 не сгенерирована, поэтому
# генерируем её здесь, а кластер создаётся с POSTGRES_INITDB_ARGS (см. docker-compose.yml).
#
# Внимание: локаль кластера задаётся только при `initdb`, т.е. при первом создании тома.
# Сменить её позже = сбросить стенд (`docker compose down -v`) и поднять заново.
FROM postgres:16

RUN localedef -i ru_RU -f UTF-8 ru_RU.UTF-8 \
 && locale -a | grep -qx 'ru_RU.utf8'
