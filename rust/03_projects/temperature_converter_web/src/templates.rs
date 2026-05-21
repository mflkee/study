// Шаблоны HTML через библиотеку Askama
// Askama компилирует шаблоны в Rust-код на этапе сборки
// Шаблоны находятся в папке templates/

use askama::Template;
use axum::response::IntoResponse;

// Шаблон главной страницы — форма конвертации
#[derive(Template)]
#[template(path = "index.html")]
pub struct IndexTemplate;

// Шаблон результата — отображает результат конвертации
// Все значения уже отформатированы перед передачей в шаблон
#[derive(Template)]
#[template(path = "result.html")]
pub struct ResultTemplate {
    pub value: f64,
    pub from_unit: String,
    pub result_str: String,
    pub to_unit: String,
}

// Реализуем IntoResponse для шаблонов, чтобы axum мог их вернуть как HTTP-ответ
// При ошибке рендеринга возвращаем 500 Internal Server Error
impl IntoResponse for IndexTemplate {
    fn into_response(self) -> axum::response::Response {
        match self.render() {
            Ok(html) => axum::response::Html(html).into_response(),
            Err(err) => (
                axum::http::StatusCode::INTERNAL_SERVER_ERROR,
                format!("Ошибка рендеринга шаблона: {err}"),
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
                format!("Ошибка рендеринга шаблона: {err}"),
            )
                .into_response(),
        }
    }
}
