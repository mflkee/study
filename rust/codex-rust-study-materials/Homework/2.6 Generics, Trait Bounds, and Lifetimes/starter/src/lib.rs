pub trait Scorable {
    fn score(&self) -> u32;
    fn title(&self) -> &str;
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct HomeworkResult {
    pub title: String,
    pub score: u32,
    pub attempts: u8,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExamResult {
    pub title: String,
    pub score: u32,
    pub oral_bonus: u8,
}

impl Scorable for HomeworkResult {
    fn score(&self) -> u32 {
        todo!("Верните итоговый балл домашней работы")
    }

    fn title(&self) -> &str {
        todo!("Верните название домашней работы")
    }
}

impl Scorable for ExamResult {
    fn score(&self) -> u32 {
        todo!("Верните итоговый балл экзамена")
    }

    fn title(&self) -> &str {
        todo!("Верните название экзамена")
    }
}

pub fn best_item<T: Scorable>(_items: &[T]) -> Option<&T> {
    todo!("Найдите элемент с наибольшим баллом")
}

pub fn average_score<T: Scorable>(_items: &[T]) -> Option<f32> {
    todo!("Посчитайте средний балл по набору элементов")
}

pub fn top_titles<'a, T: Scorable>(_items: &'a [T], _min_score: u32) -> Vec<&'a str> {
    todo!("Верните названия элементов, у которых балл не ниже порога")
}

pub fn longer_title<'a>(_left: &'a str, _right: &'a str) -> &'a str {
    todo!("Верните более длинную строку")
}
