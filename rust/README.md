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
│   ├── 09_collections/     # Vec, String, HashMap (+ HASHMAP.md)
│   ├── 10_modules/         # Модули: backyard, restaurant
│   ├── 11_handling_errors/ # Обработка ошибок: expect vs match
│   └── 12_generic_types/   # Дженерики: устранение дублирования
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
├── 04_experiments/         # Эксперименты
│   └── mathmagic/          # Математический фокус с матрицами
│
├── 05_algorithms/          # Алгоритмы
│   └── bs/                 # Бинарный поиск
│
├── digital-logic/          # Мини-курс: цифровая логика (гейты → регистры)
└── rust-study-materials/   # Импортированный курс: Theory / Practice / Homework
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
cargo run -p backyard              # модули
cargo run -p restaurant            # модули
cargo run -p ai_vers               # ошибки: expect
cargo run -p handing_errors        # ошибки: match
cargo run -p error_reporting       # ошибки: ?-оператор
cargo run -p removing_dublication  # дженерики

# Exercises
cargo run -p guessing_game
cargo run -p fibonacci
cargo run -p ex_vec_stack
cargo run -p ex_safe_get
cargo run -p ex_borrow_shift
cargo run -p ex_table_row
cargo run -p ex_join_with_comma
cargo run -p ex_utf8_trap
cargo run -p ex_pig_latin
cargo run -p ex_word_frequencies
cargo run -p ex_stats_mean_median_mode
cargo run -p ex_group_by
cargo run -p ex_inventory_upsert
cargo run -p ex_top_n
cargo run -p ex_merge_maps

# Projects
cargo run -p temperature_converter
cargo run -p company_directory_cli
cargo run -p temperature_converter_web  # http://localhost:3000

# Experiments & Algorithms
cargo run -p mathmagic
cargo run -p bs

# Digital logic (мини-курс)
cd digital-logic && cargo run --bin step1-task
```

> Не помнишь имя пакета? `cargo metadata --no-deps --format-version 1 | grep -o '"name":"[^"]*"'`
> или просто `grep -r '^name' */**/Cargo.toml`.

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

## Шпаргалки и заметки

Материалы, которые не влезают в комментарии к коду:

- [`01_basics/09_collections/HASHMAP.md`](01_basics/09_collections/HASHMAP.md) —
  полный разбор `HashMap`: entry API, владение, порядок обхода, `BTreeMap`, рецепты.
- [`01_basics/09_collections/VEC_STRING.md`](01_basics/09_collections/VEC_STRING.md) —
  `Vec` и `String`: методы, срезы, владение, ловушки UTF-8.
- [`01_basics/11_handling_errors/OPTION_RESULT.md`](01_basics/11_handling_errors/OPTION_RESULT.md) —
  `Option`, `Result`, оператор `?`, свои типы ошибок, `panic!` vs `Result`.
- [`01_basics/12_generic_types/GENERICS_TRAITS.md`](01_basics/12_generic_types/GENERICS_TRAITS.md) —
  дженерики, трейты, `dyn` vs `impl Trait`, времена жизни.
- [`digital-logic/README.md`](digital-logic/README.md) — мини-курс по цифровой
  логике: гейты → таблицы истинности → сумматор → триггеры (лекции + задания + разборы).
- [`02_exercises/README_collections.md`](02_exercises/README_collections.md) — индекс
  упражнений по коллекциям.
- [`rust-study-materials/`](rust-study-materials/README.md) — вводный курс:
  теория, практика, домашние задания, Rust-koans.

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
