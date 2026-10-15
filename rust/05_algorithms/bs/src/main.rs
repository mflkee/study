fn main() {
    let nums: [i32; 7] = [1, 2, 3, 4, 5, 6, 7];
    let target = 5;

    match bs(&nums, target) {
        Some(i) => println!(" Число под инкдексом {i}"),
        None => println!("Число не нашлось"),
    };
}

fn bs(arr: &[i32], target: i32) -> Option<usize> {
    let mut low = 0;
    let mut hight = arr.len() - 1;

    while low <= hight {
        let mid = (hight + low) / 2;
        if arr[mid] == target {
            return Some(mid);
        } else if arr[mid] < target {
            low = mid + 1;
        } else {
            hight = mid - 1
        }
    }
    None
}
