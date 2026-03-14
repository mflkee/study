#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ItemType {
    Book,
    Magazine,
    LectureNotes,
}

#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord)]
pub enum Condition {
    New,
    Good,
    Worn,
    Critical,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LibraryItem {
    pub title: String,
    pub year: u16,
    pub pages: u16,
    pub item_type: ItemType,
    pub condition: Condition,
    pub author: Option<String>,
}

impl LibraryItem {
    pub fn book(author: &str, title: &str, year: u16, pages: u16, condition: Condition) -> Self {
        Self {
            title: title.to_string(),
            year,
            pages,
            item_type: ItemType::Book,
            condition,
            author: Some(author.to_string()),
        }
    }

    pub fn magazine(title: &str, year: u16, pages: u16, condition: Condition) -> Self {
        Self {
            title: title.to_string(),
            year,
            pages,
            item_type: ItemType::Magazine,
            condition,
            author: None,
        }
    }

    pub fn lecture_notes(title: &str, year: u16, pages: u16, condition: Condition) -> Self {
        Self {
            title: title.to_string(),
            year,
            pages,
            item_type: ItemType::LectureNotes,
            condition,
            author: None,
        }
    }
}

pub trait Describable {
    fn describe(&self) -> String;
    fn can_be_lent(&self) -> bool;
}

pub struct Library {
    pub name: String,
    pub items: Vec<LibraryItem>,
}

impl Library {
    pub fn new(name: &str) -> Self {
        Self {
            name: name.to_string(),
            items: Vec::new(),
        }
    }

    pub fn add_item(&mut self, _item: LibraryItem) {
        todo!("Добавляйте только экземпляры, которые ещё можно выдавать")
    }

    pub fn find_by_title(&self, _title: &str) -> Option<&LibraryItem> {
        todo!("Найдите материал по названию")
    }

    pub fn lend(&mut self, _title: &str) -> Option<LibraryItem> {
        todo!("Выдайте материал и удалите его из внутреннего хранилища")
    }

    pub fn items_by_type(&self, _item_type: ItemType) -> Vec<&LibraryItem> {
        todo!("Верните все материалы нужного типа")
    }
}

impl Describable for LibraryItem {
    fn describe(&self) -> String {
        todo!("Соберите краткое текстовое описание материала")
    }

    fn can_be_lent(&self) -> bool {
        todo!("Определите, можно ли выдавать материал читателю")
    }
}
