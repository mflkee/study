#[derive(Debug, Clone, PartialEq)]
pub struct Student {
    pub name: String,
    pub completed_modules: u32,
    pub average_score: f32,
    pub has_project: bool,
}

impl Student {
    pub fn new(name: &str, completed_modules: u32, average_score: f32, has_project: bool) -> Self {
        Self {
            name: name.to_string(),
            completed_modules,
            average_score,
            has_project,
        }
    }
}

pub fn select_for_final_project<'a>(_students: &'a [Student], _min_score: f32) -> Vec<&'a Student> {
    todo!("Отберите студентов, подходящих под критерии допуска к финальному проекту")
}

pub fn total_completed_modules(_students: &[Student]) -> u32 {
    todo!("Посчитайте общее число завершённых модулей")
}

pub fn adjust_scores<F>(_scores: &[u8], _transformer: F) -> Vec<u8>
where
    F: Fn(u8) -> u8,
{
    todo!("Примените переданное замыкание ко всем оценкам")
}

pub fn flatten_attempts(_attempts: &[Vec<u8>]) -> Vec<u8> {
    todo!("Соберите все попытки в один плоский список")
}

pub fn best_score_per_student(_attempts: &[Vec<u8>]) -> Vec<Option<u8>> {
    todo!("Для каждого студента найдите его лучшую попытку")
}
