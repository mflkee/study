use rust_testing_example::{completion_percent, normalize_title, split_tags};

#[test]
fn integration_workflow_covers_public_api() {
    let title = normalize_title("  Final   project ");
    let tags = split_tags("rust, cli, testing");
    let percent = completion_percent(3, 5);

    assert_eq!(title, "Final project");
    assert_eq!(tags.len(), 3);
    assert_eq!(percent, Some(60));
}
