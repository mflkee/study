use removing_dublication::largest_value_in_list;

fn main() {
    let list_el = vec![12, 59, 2, 100, 9999, 23];
    let result = largest_value_in_list(&list_el);
    println!("{result}");

    let list_el = vec![12, 59, 2, 100, 0, 23];
    let result = largest_value_in_list(&list_el);
    println!("{result}");
}
