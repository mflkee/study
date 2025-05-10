pub fn demo_array(){
    let mut nums: [i8; 6] = [1, 2, 3, 4, 5, 6];
    println!("{}", nums[0]);
    nums[0] *= 10;
    println!("{}", nums[0]);
}
