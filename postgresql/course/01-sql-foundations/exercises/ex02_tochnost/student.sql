-- Упражнение 02: заготовка — намеренно на float8 (потеря точности).
-- Перепиши на NUMERIC и round(..., 3).

CREATE TABLE ex02_netto (
    id         serial PRIMARY KEY,
    brutto     float8,
    moisture   float8,
    impurities float8,
    netto      float8
);

INSERT INTO ex02_netto (brutto, moisture, impurities)
VALUES (1000.0, 0.005, 0.002);

-- TODO: считается в float8 — из-за двоичного представления результат "грязный".
-- Перепиши таблицу на NUMERIC(20,6) и посчитай с точным округлением.
UPDATE ex02_netto SET netto = brutto * (1 - moisture) * (1 - impurities);