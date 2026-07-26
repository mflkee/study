use starter::{ParallelError, parallel_max, parallel_sum, parallel_word_count, split_into_chunks};

#[test]
fn splits_input_into_chunks() {
    let chunks = split_into_chunks(&[1, 2, 3, 4, 5], 2).unwrap();

    assert_eq!(chunks.len(), 2);
    assert_eq!(chunks[0], vec![1, 2, 3]);
    assert_eq!(chunks[1], vec![4, 5]);
}

#[test]
fn rejects_zero_workers() {
    let error = split_into_chunks(&[1, 2, 3], 0).unwrap_err();

    assert_eq!(error, ParallelError::ZeroWorkers);
}

#[test]
fn calculates_parallel_sum() {
    let sum = parallel_sum(&[10, 20, 30, 40], 2).unwrap();

    assert_eq!(sum, 100);
}

#[test]
fn calculates_parallel_max() {
    let max = parallel_max(&[10, 70, 30, 40], 3).unwrap();

    assert_eq!(max, 70);
}

#[test]
fn counts_words_in_parallel() {
    let lines = vec![
        "Rust is strict".to_string(),
        "Borrow checker helps".to_string(),
        "Threads can be safe".to_string(),
    ];

    let count = parallel_word_count(&lines, 2).unwrap();

    assert_eq!(count, 10);
}
