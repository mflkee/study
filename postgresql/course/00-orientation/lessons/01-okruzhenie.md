# Урок 01: Настройка окружения

**Закрывает цель:** рабочая среда для всего курса (Rust, Docker, sqlx-cli, psql)
**Время:** 40 мин

## Зачем это нужно

Весь курс построен на выполнении кода на твоей машине. Если окружение не готово, каждый следующий модуль начнётся с одной и той же боли «у меня не работает». Один раз настраиваем — дальше только учимся.

## Ключевые идеи (сжато)

- Три проверяемых инструмента: **Rust toolchain** (`rustc`/`cargo`), **Docker** (daemon + CLI), **sqlx-cli** (миграции БД).
- Клиент **psql** отдельно не нужен: он живёт в контейнере стенда.
- Все команды проверки — однострочные; выводишь версии — окружение готово.
- Версии на момент курса: Rust 1.98, Docker 29.x, sqlx-cli 0.9, PostgreSQL 18.6.

## Разбор на примере

Проверяем по очереди. Каждая команда должна вернуть версию, а не «command not found».

```bash
# 1. Rust toolchain: компилятор и менеджер пакетов
rustc --version      # rustc 1.98.1 (48a229cea 2026-09-01)
cargo --version      # cargo 1.98.1

# 2. Docker: CLI и встроенный compose
docker --version     # Docker version 29.8.1
docker compose version   # Docker Compose version v2.x

# 3. Утилита миграций экосистемы sqlx
sqlx --version       # sqlx-cli 0.9.0
```

Что происходит внутри: `rustc --version` печатает версию компилятора; если бинарь не найден, шелл ищет его в `PATH` и пишет `command not found` с кодом выхода 127. Отсутствие в `PATH` — самая частая причина «не установлено».

Установка, если чего-то нет:

```bash
# Rust через rustup (управление несколькими версиями; на Arch можно и pacman -S rust)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"

# sqlx-cli (компилируется из исходников, ~1-2 мин)
cargo install sqlx-cli --no-default-features --features rustls,postgres --locked
```

Команда `cargo install sqlx-cli` собирает утилиту в `~/.cargo/bin` (по умолчанию). Флаги: `--no-default-features` отключает лишнее, `--features rustls,postgres` включает поддержку TLS криптографией rustls и драйвер PostgreSQL. На машине автора курса эта команда установила sqlx-cli 0.9.0 без ошибок.

## Как это устроено под капотом

- **rustup** кладёт toolchain в `~/.rustup`, а симлинки на бинари — в `~/.cargo/bin`. Именно поэтому после установки нужно `source ~/.cargo/env` (добавляет `~/.cargo/bin` в `PATH`).
- **Docker** — клиент-серверная архитектура: `docker` (CLI) обращается к демону через `/var/run/docker.sock`. Ошибка «permission denied» при `docker ps` означает, что пользователь не в группе `docker`.
- **sqlx-cli** работает с БД через переменную окружения `DATABASE_URL` вида `postgres://user:password@host:port/db`.

## Типичные ошибки и грабли

1. **`cargo: command not found` после установки rustup** — забыли `source ~/.cargo/env` или переоткрыть терминал.
2. **`docker: permission denied`** — пользователь не в группе `docker`: `sudo usermod -aG docker $USER` и перезайти в сессию.
3. **`sqlx: command not found`** — `~/.cargo/bin` не в `PATH`.
4. **Порт занят** — если локальный PostgreSQL уже слушает 5432, стенд курса всё равно не конфликтует: у него порт 15432 (см. урок 02).
5. **Версии не совпадают с курсом** — это нормально, если твоя версия новее и стабильнее; главное — команды работают.

## Мини-задание

Выполни три команды проверки и запиши вывод. Если чего-то не хватает — установи по инструкции выше.

<details>
<summary>Ответ (что должно получиться)</summary>

```
$ rustc --version
rustc 1.98.1 (...)
$ cargo --version
cargo 1.98.1 (...)
$ sqlx --version
sqlx-cli 0.9.0
```

Точная дата и hash в скобках могут отличаться — важен мажор/минор (1.98, 0.9).
</details>

## Как это спросят на собеседовании

1. «Как ты разворачиваешь локальное окружение для разработки на Rust + PostgreSQL?»
2. «Что такое DATABASE_URL и зачем она sqlx-cli?»
3. «Чем docker клиент отличается от docker демона?»

## Что читать дальше

- Установка Rust (rustup): <https://www.rust-lang.org/tools/install>
- The Rust Book, глава 1 (установка): <https://doc.rust-lang.org/book/ch01-01-installation.html>
- Установка Docker: <https://docs.docker.com/engine/install/>
- sqlx-cli (README): <https://github.com/launchbadge/sqlx/tree/main/sqlx-cli>