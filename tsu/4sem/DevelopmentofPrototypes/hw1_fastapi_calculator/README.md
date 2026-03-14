# FastAPI calculator

Решение домашнего задания по теме "Введение в бэкенд. Асинхронность в Python".

## Что умеет приложение

- Сложение, вычитание, умножение и деление.
- Сохранение текущего выражения.
- Построение выражения из `left`, `op`, `right`.
- Добавление новой операции к текущему выражению.
- Вычисление сложных выражений со скобками и приоритетом операций.

## Запуск через uv

```bash
uv sync --dev
uv run uvicorn calculator_api.main:app --reload
```

После запуска документация FastAPI будет доступна по адресу:

- `http://127.0.0.1:8000/docs`

## Тесты

```bash
uv run pytest
```

## Основные эндпоинты

- `GET /operations/add?a=2&b=3`
- `GET /operations/subtract?a=5&b=2`
- `GET /operations/multiply?a=4&b=6`
- `GET /operations/divide?a=10&b=2`
- `PUT /expression`
- `POST /expression/compose`
- `POST /expression/append`
- `GET /expression`
- `POST /expression/evaluate`
- `POST /expression/parse-and-evaluate`
