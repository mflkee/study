-- Упражнение 02: заготовка — обычный индекс по quality (не partial!).
-- Из-за низкой кардинальности B-tree по quality почти бесполезен:
-- нужен partial-индекс: ON measurements (ts) WHERE quality = 1.

CREATE INDEX idx_measurements_bad_quality ON measurements (quality);

-- TODO: добавь также композитный индекс на devices (line_id, device_type).