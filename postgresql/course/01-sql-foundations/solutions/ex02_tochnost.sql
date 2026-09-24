-- Решение упражнения 02: NUMERIC + точное округление.
CREATE TABLE ex02_netto (
    id         serial PRIMARY KEY,
    brutto     numeric(20,6),
    moisture   numeric(20,6),
    impurities numeric(20,6),
    netto      numeric(20,6)
);

INSERT INTO ex02_netto (brutto, moisture, impurities)
VALUES (1000.0, 0.005, 0.002);

UPDATE ex02_netto
SET netto = round(brutto * (1 - moisture) * (1 - impurities), 3);

-- netto = 993.010 (float8 вариант дал бы 993.0100000000001 — см. лабу)