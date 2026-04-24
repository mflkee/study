# Домашнее задание 3

Проект демонстрирует модель данных для таблицы из `students.csv` с использованием `SQLAlchemy`, `Alembic` и минимального приложения на `FastAPI`.

## Структура модели

Плоская таблица из CSV нормализована на четыре сущности:

- `faculties` — факультеты.
- `subjects` — дисциплины. В CSV столбец называется `Курс`, но по данным в нём лежат названия предметов.
- `students` — студенты, уникальность определяется сочетанием `Фамилия + Имя + Факультет`.
- `grades` — оценки по предметам. Поле `source_row_number` хранит номер строки исходного CSV, чтобы импорт был идемпотентным и не терял повторяющиеся попытки по одному предмету.

## Как запустить

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
alembic upgrade head
python -m app.import_csv
uvicorn app.main:app --reload
```

После запуска можно открыть:

- `GET /health`
- `GET /stats`
- `GET /students`

## Миграции

Начальная миграция лежит в `alembic/versions/20260424_01_create_initial_tables.py`.

Применение миграции:

```bash
alembic upgrade head
```
