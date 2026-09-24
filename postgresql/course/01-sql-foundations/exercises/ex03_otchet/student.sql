-- Упражнение 03: заготовка — max(value) вместо «последнего значения».
-- Перепиши на row_number() OVER (PARTITION BY device_id, ts::date ORDER BY ts DESC).

CREATE VIEW last_daily_measurement AS
SELECT device_id,
       ts::date AS day,
       max(value) AS last_value
FROM measurements
GROUP BY device_id, ts::date;