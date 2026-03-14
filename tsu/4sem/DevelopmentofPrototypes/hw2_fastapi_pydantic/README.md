# FastAPI and Pydantic: user registration

Решение домашнего задания 2 по теме `FastAPI и Pydantic`.

## Что реализовано

- модель `UserRegistration` с валидацией всех обязательных полей;
- функция `register_user(data: dict)`, которая возвращает либо валидную модель, либо список ошибок;
- скрытие поля `password_confirm` при сериализации;
- FastAPI-эндпоинт `POST /register`;
- бонус: функция `create_recursive_data_model()` для рекурсивной JSON-модели.

## Запуск через uv

```bash
uv sync --dev
uv run uvicorn user_registration_service.main:app --reload
```

Документация FastAPI:

- `http://127.0.0.1:8000/docs`

## Проверка

```bash
uv run ruff check
uv run pytest
```

## Основной эндпоинт

- `POST /register`

Пример JSON:

```json
{
  "username": "student_01",
  "email": "student@example.com",
  "password": "SecurePass1",
  "password_confirm": "SecurePass1",
  "age": 21,
  "real_name": "Alex",
  "phone": "+7-999-12-34"
}
```
