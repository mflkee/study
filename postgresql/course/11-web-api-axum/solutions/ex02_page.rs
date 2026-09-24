//! Решение упражнения 02: «Пагинация значений датчиков».
//! Скопируйте содержимое в `exercises/src/ex02_page.rs` после попытки.

/// Нормализованные параметры пагинации (limit, offset).
pub fn page_params(limit_raw: Option<String>, offset_raw: Option<String>) -> (u32, u32) {
    let limit = limit_raw
        .and_then(|s| s.parse::<u32>().ok())
        .unwrap_or(50)
        .clamp(1, 200);
    let offset = offset_raw
        .and_then(|s| s.parse::<u32>().ok())
        .unwrap_or(0)
        .min(1_000_000);
    (limit, offset)
}
