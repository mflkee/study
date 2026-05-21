// Конвертер температур (Web-версия на Axum + htmx)
// Демонстрирует: веб-сервер, роутинг, HTML-шаблоны, htmx для AJAX
// Используемые библиотеки: axum (веб-фреймворк), askama (шаблоны), tokio (асинхронность)

mod handlers;
mod templates;

use axum::{
    routing::{get, post},
    Router,
};
use handlers::{convert, index};
use tower_http::services::ServeDir;

#[tokio::main] // макрос для запуска асинхронного рантайма Tokio
async fn main() {
    // Настройка маршрутов (роутинг)
    let app = Router::new()
        .route("/", get(index))                    // GET / — главная страница
        .route("/convert", post(convert))          // POST /convert — конвертация
        .nest_service("/static", ServeDir::new("static")); // статические файлы

    // Привязка к localhost:3000
    let listener = tokio::net::TcpListener::bind("127.0.0.1:3000")
        .await
        .unwrap();
    println!("Сервер запущен на http://{}", listener.local_addr().unwrap());
    axum::serve(listener, app).await.unwrap();
}
