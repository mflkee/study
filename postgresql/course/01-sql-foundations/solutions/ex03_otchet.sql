-- Решение упражнения 03: последнее значение за сутки через row_number.
CREATE VIEW last_daily_measurement AS
SELECT device_id, day, value AS last_value
FROM (
    SELECT device_id,
           ts::date AS day,
           value,
           row_number() OVER (PARTITION BY device_id, ts::date
                              ORDER BY ts DESC) AS rn
    FROM measurements
) t
WHERE rn = 1;