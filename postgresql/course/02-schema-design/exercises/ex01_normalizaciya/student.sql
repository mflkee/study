-- Упражнение 01: заготовка — плоская таблица «всё в одном».
-- Перепиши на нормализованную схему: ex01_lines / ex01_devices / ex01_events.

CREATE TABLE ex01_events (
    id          serial PRIMARY KEY,
    line_name   text,
    device_tag  text,
    device_type text,
    model       text,
    ts          timestamptz,
    value       numeric(20,6)
);