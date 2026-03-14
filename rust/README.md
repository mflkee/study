# Rust Learning Workspace

Репозиторий для изучения Rust с правильной структурой.

## Структура

```text
rust/
├── 01_basics/              # Основы (проходить последовательно)
│   ├── 01_variables/       # Переменные, мутабельность, shadowing
│   ├── 02_data_types/      # Типы данных: скалярные и составные
│   ├── 03_functions/       # Функции и модули
│   ├── 04_control_flow/    # if/else, loop, while, for
│   ├── 05_ownership/       # Владение и заимствование
│   ├── 06_slices/          # Срезы
│   ├── 07_structs/         # Структуры и методы
│   └── 08_enums/           # Перечисления и pattern matching
│
├── 02_exercises/           # Упражнения
│   ├── guessing_game/      # Игра "Угадай число"
│   ├── fibonacci/          # Генератор чисел Фибоначчи
│   └── temperature_converter/ # Конвертер температур
│
├── 03_projects/            # Проекты
│   └── temperature_converter_web/ # Web-версия (Axum + htmx)
│
└── 04_experiments/         # Эксперименты
    └── mathmagic/          # Математические фокусы
```

## Использование

### Запуск примеров

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

# Exercises
cargo run -p guessing_game
cargo run -p fibonacci
cargo run -p temperature_converter

# Projects
cargo run -p temperature_converter_web

# Experiments
cargo run -p mathmagic
```

### Тесты

```bash
# Тесты для конкретного крейта
cargo test -p <package-name>

# Все тесты
cargo test
```

### Сборка

```bash
# Проверка без оптимизаций
cargo check

# Полная сборка
cargo build

# Сборка в режиме release
cargo build --release
```

## Рекомендации по изучению

1. **Начните с `01_basics/`** — проходите темы по порядку (01 → 08)
2. **Читайте комментарии** в коде — там есть ссылки на Rust Book
3. **После основ** переходите к `02_exercises/`
4. **Изучайте проекты** когда почувствуете уверенность
5. **Экспериментируйте** в `04_experiments/` или создавайте свои крейты

## Добавление новых примеров

1. Создайте новый крейт в соответствующей папке:

   ```bash
   cargo new 01_basics/09_topic_name
   ```

2. Добавьте его в `Cargo.toml` в секцию `[workspace].members`
3. Переименуйте пакет в `Cargo.toml` (имя не должно начинаться с цифры):

   ```toml
   [package]
   name = "basics_topic_name"
   ```

## Ресурсы

- [The Rust Programming Language (книга)](https://doc.rust-lang.org/book/)
- [Rust by Example](https://doc.rust-lang.org/rust-by-example/)
- [Rust Standard Library](https://doc.rust-lang.org/std/)
