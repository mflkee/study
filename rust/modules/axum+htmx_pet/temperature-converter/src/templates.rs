use askama::Template;
use axum::response::IntoResponse;

// Шаблон главной страницы (форма)
#[derive(Template)]
#[template(path = "index.html")]
pub struct IndexTemplate;

// Шаблон результата — значения уже округлены и форматированы
#[derive(Template)]
#[template(path = "result.html")]
pub struct ResultTemplate {
    pub value: f64,
    pub from_unit: String,
    pub result_str: String,
    pub to_unit: String,
}

// Преобразуем шаблон в HTTP‑ответ
impl IntoResponse for IndexTemplate {
    fn into_response(self) -> axum::response::Response {
        match self.render() {
            Ok(html) => axum::response::Html(html).into_response(),
            Err(err) => (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                format!("Template render error: {}", err),
            )
                .into_response(),
        }
    }
}

impl IntoResponse for ResultTemplate {
    fn into_response(self) -> axum::response::Response {
        match self.render() {
            Ok(html) => axum::response::Html(html).into_response(),
            Err(err) => (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                format!("Template render error: {}", err),
            )
                .into_response(),
        }
    }
}
