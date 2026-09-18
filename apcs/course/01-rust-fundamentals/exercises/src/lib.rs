//! Упражнения Модуля 1.
//!
//! Каждый файл — одно упражнение на тему урока. Файлы НЕ сотрозависимы.
//! Рабочая схема:
//! 1. Читаете задание в заголовке файла.
//! 2. Пробуете написать решение САМИ (закомментировав готовое).
//! 3. Проверяете: `cargo test -p exercises` — всё зелёное.
//!
//! Все упражнения решены «эталонно»: `cargo test -p exercises` зелёный
//! сразу. Задача — понять решение, переписать по памяти и убедиться,
//! что тесты по-прежнему проходят.

pub mod ex01_ownership;
pub mod ex02_borrowing;
pub mod ex03_lifetimes;
pub mod ex04_structs_enums;
pub mod ex05_traits;
pub mod ex06_collections;
pub mod ex07_errors;
pub mod ex08_threads;
pub mod ex09_practice;