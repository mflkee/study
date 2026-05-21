# Rust Learning Workspace

Репозиторий для изучения Rust. Всё разбито по темам — от основ до проектов.

## Структура

```text
rust/
├── 01_basics/              # Основы (проходить последовательно)
│   ├── 01_variables/       # Переменные, мутабельность, shadowing
│   ├── 02_data_types/      # Типы данных: скалярные и составные
│   ├── 03_functions/       # Функции, выражения, возврат значений
│   ├── 04_control_flow/    # if/else, loop, while, for
│   ├── 05_ownership/       # Владение, ссылки, заимствование
│   ├── 06_slices/          # Срезы строк и массивов
│   ├── 07_structs/         # Структуры, методы, impl
│   ├── 08_enums/           # Перечисления, match, Option
│   ├── 09_collections/     # Vec, String, HashMap
│   ├── 10_modules/         # Модули: backyard, restaurant
│   └── 11_handling_errors/ # Обработка ошибок: expect vs match
│
├── 02_exercises/           # Упражнения по темам
│   ├── guessing_game/      # Игра "Угадай число"
│   ├── fibonacci/          # Числа Фибоначчи
│   ├── 01_vectors/         # Vec: стек, безопасный доступ, сдвиг, таблица
│   ├── 02_strings/         # String: объединение, UTF-8, Pig Latin
│   └── 03_hashmaps/        # HashMap: частотность слов, статистика
│
├── 03_projects/            # Проекты
│   ├── temperature_converter/       # CLI-конвертер температур
│   ├── temperature_converter_web/   # Web-версия (Axum + htmx)
│   └── company_directory_cli/      # Справочник сотрудников (CLI)
│
└── 04_experiments/         # Эксперименты
    └── mathmagic/          # Математический фокус с матрицами
```

## Использование

```bash
# Basics (проходить по порядку)
cargo run -p basics_variables
cargo run -p basics_data_types
cargo run -p basics_functions
cargo run -p basics_control_flow
cargo run -p basics_ownership
cargo run -p basics_slices
cargo run -p basics_structs
cargo run -p basics_enums
cargo run -p basics_collections

# Exercises
cargo run -p guessing_game
cargo run -p fibonacci

# Projects
cargo run -p temperature_converter
cargo run -p company_directory_cli
cargo run -p temperature_converter_web  # http://localhost:3000

# Experiments
cargo run -p mathmagic
```

### Тесты

```bash
# Все тесты
cargo test

# Тесты конкретного крейта
cargo test -p <package-name>
```

### Сборка

```bash
cargo check        # Проверка без оптимизаций
cargo build        # Полная сборка
cargo build --release  # Релизная сборка
```

## Как пользоваться

1. **Начни с `01_basics/01_variables`** и проходи темы по порядку
2. **Читай русские комментарии** в коде — они объясняют ключевые концепции
3. **После основ** переходи к упражнениям в `02_exercises/`
4. **Проекты** в `03_projects/` — примеры реального кода (CLI, Web, справочник)
5. **Экспериментируй** в `04_experiments/` или создавай свои крейты

## Добавление новых примеров

```bash
cargo new 01_basics/09_topic_name
```

Затем добавь путь в `Cargo.toml` в секцию `[workspace].members` и переименуй пакет:

```toml
[package]
name = "basics_topic_name"
```

## Ресурсы

- [The Rust Programming Language](https://doc.rust-lang.org/book/)
- [Rust by Example](https://doc.rust-lang.org/rust-by-example/)
- [Rust Standard Library](https://doc.rust-lang.org/std/)
