/// Убирает лишние пробелы и нормализует заголовок.
///
/// ```
/// use rust_testing_example::normalize_title;
///
/// assert_eq!(normalize_title("  Learn   Rust  "), "Learn Rust");
/// ```
pub fn normalize_title(title: &str) -> String {
    title.split_whitespace().collect::<Vec<_>>().join(" ")
}

pub fn split_tags(input: &str) -> Vec<String> {
    input
        .split(',')
        .map(str::trim)
        .filter(|tag| !tag.is_empty())
        .map(ToOwned::to_owned)
        .collect()
}

pub fn completion_percent(done: usize, total: usize) -> Option<u8> {
    if total == 0 || done > total {
        return None;
    }

    Some(((done as f32 / total as f32) * 100.0).round() as u8)
}

#[cfg(test)]
mod tests {
    use super::{completion_percent, normalize_title, split_tags};

    #[test]
    fn normalizes_title() {
        assert_eq!(normalize_title(" Rust   testing "), "Rust testing");
    }

    #[test]
    fn splits_tags() {
        assert_eq!(
            split_tags("rust, testing, cli"),
            vec!["rust".to_string(), "testing".to_string(), "cli".to_string()]
        );
    }

    #[test]
    fn computes_completion_percent() {
        assert_eq!(completion_percent(2, 4), Some(50));
        assert_eq!(completion_percent(5, 4), None);
    }
}
