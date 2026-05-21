// Модульная система: библиотечный крейт restaurant
// Демонстрирует re-export (pub use) для удобного публичного API
// front_of_house::hosting переэкспортируется на верхний уровень

mod front_of_house;

// re-export: делаем hosting доступным как crate::hosting
pub use crate::front_of_house::hosting;

pub fn eat_at_restaurant() {
    // Теперь можно вызывать hosting::add_to_waitlist()
    // напрямую, без front_of_house::hosting::add_to_waitlist()
    hosting::add_to_waitlist();
}
