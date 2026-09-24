-- 00-setup.sql — данные для REST API (модуль 11, база course_m06).
-- Идемпотентно: если у устройства НЕТ свежих измерений (за 12 часов) —
-- докидываем почасовую серию за последние сутки (для current/trends/values).

INSERT INTO measurements (device_id, ts, value)
SELECT d.id, now() - (g || ' hours')::interval, (1000 + g * 3.5)::numeric(20,6)
  FROM devices d, generate_series(0, 23) g
 WHERE d.tag = 'M-01-001'
   AND (SELECT max(m.ts) FROM measurements m
         JOIN devices dd ON dd.id = m.device_id
        WHERE dd.tag = 'M-01-001') < now() - interval '12 hours';

INSERT INTO measurements (device_id, ts, value)
SELECT d.id, now() - (g || ' hours')::interval, (850 + g * 0.1)::numeric(20,6)
  FROM devices d, generate_series(0, 23) g
 WHERE d.tag = 'D-01-001'
   AND (SELECT max(m.ts) FROM measurements m
         JOIN devices dd ON dd.id = m.device_id
        WHERE dd.tag = 'D-01-001') < now() - interval '12 hours';

-- Контроль API-данных: непустые серии за последние сутки.
SELECT d.tag, count(m.*) AS points,
       max(m.ts) FILTER (WHERE m.ts > now() - interval '1 day') IS NOT NULL AS has_recent
  FROM devices d LEFT JOIN measurements m ON m.device_id = d.id
 WHERE d.tag IN ('M-01-001', 'D-01-001')
 GROUP BY d.tag ORDER BY d.tag;