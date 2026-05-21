// Обработчики HTTP-запросов для конвертера температур
// index — отдаёт HTML-форму, convert — принимает данные и возвращает результат

use crate::templates::{IndexTemplate, ResultTemplate};
use axum::{extract::Form, response::IntoResponse};
use serde::Deserialize;

// Структура для парсинга данных из HTML-формы
// Поля соответствуют name-атрибутам в <input> / <select>
#[derive(Deserialize)]
pub struct ConversionForm {
    from_unit: String, // исходная единица: "c", "f" или "k"
    value: f64,        // значение температуры
    to_unit: String,   // целевая единица: "c", "f" или "k"
}

/// GET / — возвращает HTML-форму для ввода
pub async fn index() -> impl IntoResponse {
    IndexTemplate
}

/// POST /convert — принимает данные формы, конвертирует, возвращает результат
pub async fn convert(Form(form): Form<ConversionForm>) -> impl IntoResponse {
    let raw_result = convert_temp(&form.from_unit, form.value, &form.to_unit);
    let result_str = format!("{:.3}", raw_result); // округление до 3 знаков

    let from_unit_display = unit_label(&form.from_unit);
    let to_unit_display = unit_label(&form.to_unit);

    ResultTemplate {
        value: form.value,
        from_unit: from_unit_display.to_string(),
        result_str,
        to_unit: to_unit_display.to_string(),
    }
}

/// Математика конвертации: всё через Цельсий как промежуточную единицу
fn convert_temp(from: &str, val: f64, to: &str) -> f64 {
    // Сначала всё в Цельсий
    let celsius = match from {
        "c" => val,
        "f" => (val - 32.0) * 5.0 / 9.0,
        "k" => val - 273.15,
        _ => panic!("Неизвестная единица 'from'"),
    };
    // Потом из Цельсия в нужную единицу
    match to {
        "c" => celsius,
        "f" => celsius * 9.0 / 5.0 + 32.0,
        "k" => celsius + 273.15,
        _ => panic!("Неизвестная единица 'to'"),
    }
}

/// Возвращает человеко-читаемую метку для единицы измерения
fn unit_label(code: &str) -> &'static str {
    match code {
        "c" => "°C",
        "f" => "°F",
        "k" => "K",
        _ => "?",
    }
}
