pub fn passing_students(scores: &[u8], pass_from: u8) -> usize {
    scores.iter().filter(|score| **score >= pass_from).count()
}

pub fn average_score(scores: &[u8]) -> Option<f32> {
    if scores.is_empty() {
        return None;
    }

    let total: u32 = scores.iter().map(|score| u32::from(*score)).sum();
    Some(total as f32 / scores.len() as f32)
}

#[cfg(test)]
mod tests {
    use super::{average_score, passing_students};

    #[test]
    fn counts_passing_students() {
        assert_eq!(passing_students(&[2, 3, 4, 5], 3), 3);
    }

    #[test]
    fn calculates_average_score() {
        assert_eq!(average_score(&[4, 4, 5]), Some(13.0 / 3.0));
        assert_eq!(average_score(&[]), None);
    }
}
