-- Решение упражнения 02: partial-индекс под «брак» и композитный под «устройства линии».

-- Редкие строки (quality = 1) — partial: индекс только по ним, по ts (для диапазона).
CREATE INDEX idx_measurements_bad_quality
    ON measurements (ts)
    WHERE quality = 1;

-- Типовой запрос «устройства линии по типу».
CREATE INDEX idx_devices_line_type
    ON devices (line_id, device_type);