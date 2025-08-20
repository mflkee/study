use axum::{extract::Form, response::IntoResponse};
use serde::Deserialize;
use crate::templates::{IndexTemplate, ResultTemplate};

#[derive(Deserialize)]
pub struct ConversionForm {
    from_unit: String,
    value: f64,
    to_unit: String,
}

pub async fn index() -> impl IntoResponse {
    IndexTemplate
}

pub async fn convert(Form(form): Form<ConversionForm>) -> impl IntoResponse {
    let raw_result = convert_temp(&form.from_unit, form.value, &form.to_unit);
    // Округляем до 3 знаков и формируем строку
    let result_str = format!("{:.3}", raw_result);

    let from_unit_display = unit_label(&form.from_unit);
    let to_unit_display = unit_label(&form.to_unit);

    ResultTemplate {
        value: form.value,
        from_unit: from_unit_display.to_string(),
        result_str,
        to_unit: to_unit_display.to_string(),
    }
}

fn convert_temp(from: &str, val: f64, to: &str) -> f64 {
    let celsius = match from {
        "c" => val,
        "f" => (val - 32.0) * 5.0 / 9.0,
        "k" => val - 273.15,
        _ => panic!("Invalid from unit"),
    };

    match to {
        "c" => celsius,
        "f" => celsius * 9.0 / 5.0 + 32.0,
        "k" => celsius + 273.15,
        _ => panic!("Invalid to unit"),
    }
}

fn unit_label(code: &str) -> &'static str {
    match code {
        "c" => "°C",
        "f" => "°F",
        "k" => "K",
        _ => "?",
    }
}
