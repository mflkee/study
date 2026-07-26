# Working in the Terminal and Cargo

Этот файл играет ту же роль, что и краткий терминальный гайд в исходном
репозитории, но адаптирован под повседневную работу с Rust.

## Минимальный набор инструментов

- `rustc` - компилятор Rust;
- `cargo` - сборка, тесты, запуск и управление проектом;
- `rustfmt` - форматирование;
- `clippy` - линтер;
- `git` - контроль версий;
- редактор кода: VS Code, Zed, Helix, Neovim или другой удобный редактор.

## Базовые команды терминала

```bash
pwd
ls
cd <путь>
mkdir <папка>
```

Если в пути есть пробелы, заключайте путь в кавычки:

```bash
cd "~/study/rust/codex-rust-study-materials/Homework/1.4 Iterators and Closures in Rust/starter"
```

## Базовые команды cargo

### Создание проекта

```bash
cargo new my-app
cargo new my-lib --lib
```

### Сборка и запуск

```bash
cargo build
cargo run
cargo run -- arg1 arg2
```

### Тесты

```bash
cargo test
cargo test parse_command
cargo test --test 01_basics
cargo test -- --nocapture
```

### Проверка без запуска бинарника

```bash
cargo check
```

### Форматирование и линтинг

```bash
cargo fmt
cargo fmt --check
cargo clippy --all-targets --all-features -- -D warnings
```

### Документация

```bash
cargo doc --open
```

## Типичная структура проекта

```text
project/
  Cargo.toml
  src/
    main.rs
```

Для библиотеки:

```text
project/
  Cargo.toml
  src/
    lib.rs
```

Если проект растёт, выносите код в модули:

```text
src/
  lib.rs
  domain.rs
  storage.rs
  errors.rs
```

## Полезный цикл работы

1. Открыть папку проекта.
2. Запустить `cargo check`.
3. Написать или изменить код.
4. Запустить `cargo fmt`.
5. Запустить `cargo clippy --all-targets --all-features -- -D warnings`.
6. Запустить `cargo test`.
7. Сделать коммит в `git`.

## Что важно помнить студенту

- Сообщения компилятора в Rust часто содержат подсказку, что именно исправить.
- Ошибка borrow checker не означает, что язык мешает. Обычно это сигнал, что
  надо пересмотреть владение данными и границы ответственности функций.
- Если функция не должна забирать владение, сначала подумайте о `&str`, `&[T]`
  или `&T`.
- Если вы пишете библиотечный код, `unwrap` и `expect` обычно плохой знак. Лучше
  вернуть `Result`.
