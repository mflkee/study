#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ParallelError {
    ZeroWorkers,
    EmptyInput,
}

pub fn split_into_chunks(
    _numbers: &[u64],
    _worker_count: usize,
) -> Result<Vec<Vec<u64>>, ParallelError> {
    todo!("Разбейте входные данные на чанки для потоков")
}

pub fn parallel_sum(_numbers: &[u64], _worker_count: usize) -> Result<u64, ParallelError> {
    todo!("Посчитайте сумму чисел параллельно")
}

pub fn parallel_max(_numbers: &[u64], _worker_count: usize) -> Result<u64, ParallelError> {
    todo!("Найдите максимум параллельно")
}

pub fn parallel_word_count(
    _lines: &[String],
    _worker_count: usize,
) -> Result<usize, ParallelError> {
    todo!("Посчитайте число слов в наборе строк, используя несколько потоков")
}
