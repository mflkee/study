use std::collections::HashMap;

fn demo_vec() {

    // 1. Create a vector
    let v: Vec<i32> = Vec::new();
    println!("{v:#?}");

    let mut v2 = vec![1,2,3];
    println!("{:?}", v2);
    
    // 2. Add/remove values
    v2.push(4);
    println!("{:?}", v2);
    
    v2.pop();
    println!("{:?}", v2);
    
    // 3. Access to the elenents
    let third = &v2[2];
    println!("{}",third);
    let third2 = v2.get(2);
    println!("{:?}",third2);

    let mut v = vec![1, 2, 3];
    let first = v[0];
    v.push(4); // ошибка
    println!("{first}");
    
    // 4. Iteration
    for x in &v {
        println!("{}",x)
    }
    println!("{:?}", v);

    let mut v3: Vec<i32> = Vec::new();
    for x in &mut v {
        *x += 10;
        v3.push(*x);
        println!("{x}")

    }
    println!("{:?}", v3);

    // 5. Different types in one vec
    #[derive(Debug)]
    enum Cell{
        Int(i32),
        Float(f64),
        Text(String),
    }

    let row1 = vec![
        Cell::Int(10),
        Cell::Float(3.123),
        Cell::Text("Ohhh...".to_string())
    ];
    println!("{:?}", row1);

     for cell in &row1 {
      match cell {
          Cell::Int(i) => println!("int: {i}"),
          Cell::Float(f) => println!("float: {f}"),
          Cell::Text(t) => println!("text: {t}"),
      };
  }
}

fn demo_str(){
    let mut s = String::new();
    s.push_str("HELL FIRE");

    s.push('!');
    s.find(' ');

    let s2 = String::from("Hello");
    let s3 = "hello".to_string();
    println!("{s}\n{s2}\n{s3}");

    let s4 = "Gleb ".to_string();
    let s5 = "Makeev ".to_string();
    // let s45 = s4 + &s5
    // println!("{s45}");
    // or
    let s54 = format!("{s5}{s4}");
    println!("{s54}");

    for c in "HI".chars() {
    println!("{c}");
    }

    for b in "".bytes() {
        println!("{b}");
    }
    
}

fn demo_hashmap(){
    let mut scores = HashMap::new();
    scores.insert("Blue".to_string(), 10);
    scores.insert("Red".to_string(), 5);
    scores.insert("Green".to_string(), 8);

    println!("{scores:#?}");
    let mut group = 0;

    for (team, score) in &scores{
        println!("{team}: {score}");
        
        group += *score;
    }
    println!("Group points: {}", group);


    let a = scores.get("Red").copied().unwrap_or(0);
    println!("{a}");

    let team = "Blue".to_string();
    let score = scores.get(&team).copied().unwrap_or(0); 
    println!("{score}");

    scores.entry("Black".to_string()).or_insert(20);
    println!("{:?}", scores);

    let x = String::from("First");
    let y = String::from("Second");

    println!("{}, {}",x, y);

    let mut newhm = HashMap::new();
    newhm.insert(x, y);

    println!("{:?},", newhm);
    
    let mut map = HashMap::new();
    for word in "hello world world hello hi".split_whitespace() {
        let count = map.entry(word).or_insert(0);
        *count += 1;
    }
    println!("{:?}", map);

}

fn add_score(map: &mut HashMap<String, i32>, team: &str, points: i32){
    let score = map.entry(team.to_string()).or_insert(0);
    *score += points;
}


fn demo_vec_access(){
    let v = vec![1, 2, 3, 4, 5];
    let third: &i32 = &v[2];

    println!("The third element is {third}");

    let third: Option<&i32> = v.get(2);

    match third {
        Some(third) => println!("The third element is {third}"),
        None => println!("There is no third element."),
        }
}



fn main() {
    demo_vec();
    demo_str();
    demo_hashmap();

    let mut scores = HashMap::new();
    add_score(&mut scores, "Blue", 5);
    add_score(&mut scores, "Blue", 10);
    add_score(&mut scores, "Red", 8);
    println!("{:?}",scores);

    demo_vec_access();


}
