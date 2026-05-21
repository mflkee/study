// Коллекции: Vec, String, HashMap
// https://doc.rust-lang.org/book/ch08-00-common-collections.html
// Три основные коллекции из стандартной библиотеки

use std::collections::HashMap;

// ============ Vec ============
fn demo_vec() {
    println!("=== Vec ===");

    // 1. Создание вектора
    let v: Vec<i32> = Vec::new();          // пустой вектор
    let mut v2 = vec![1, 2, 3];            // макрос vec! — удобнее
    println!("Исходный: {:?}", v2);

    // 2. Добавление и удаление
    v2.push(4);                            // добавить в конец
    println!("После push: {:?}", v2);

    v2.pop();                              // удалить с конца
    println!("После pop: {:?}", v2);

    // 3. Доступ к элементам
    let third = &v2[2];                    // по индексу (паника если нет)
    println!("Третий: {third}");

    let third2 = v2.get(2);                // безопасный доступ через Option
    println!("Третий (safe): {:?}", third2);

    // Демонстрация ошибки заимствования:
    // let mut v = vec![1, 2, 3];
    // let first = &v[0];      // неизменяемое заимствование
    // v.push(4);              // ОШИБКА: mutable borrow после immutable
    // println!("{first}");

    // 4. Итерация
    println!("Итерация:");
    for x in &v2 {
        println!("  {x}");
    }

    // Изменение элементов через итератор
    let mut v3: Vec<i32> = Vec::new();
    for x in &mut v2 {
        *x += 10;           // разыменовываем и изменяем
        v3.push(*x);
    }
    println!("После +10: {:?}", v3);

    // 5. Разные типы в одном векторе через enum
    #[derive(Debug)]
    enum Cell {
        Int(i32),
        Float(f64),
        Text(String),
    }

    let row1 = vec![
        Cell::Int(10),
        Cell::Float(3.123),
        Cell::Text("Ого...".to_string()),
    ];
    println!("Разные типы: {:?}", row1);

    for cell in &row1 {
        match cell {
            Cell::Int(i) => println!("  int: {i}"),
            Cell::Float(f) => println!("  float: {f}"),
            Cell::Text(t) => println!("  text: {t}"),
        }
    }
}

// ============ String ============
fn demo_str() {
    println!("\n=== String ===");

    // Создание и модификация
    let mut s = String::new();
    s.push_str("HELL FIRE");
    s.push('!');                         // добавить символ
    println!("{s}");

    let s2 = String::from("Hello");
    let s3 = "hello".to_string();        // &str -> String
    println!("{s2}\n{s3}");

    // Конкатенация
    let s4 = "Gleb ".to_string();
    let s5 = "Makeev ".to_string();
    // let s45 = s4 + &s5;  // + перемещает s4, берёт &s5
    let s54 = format!("{s5}{s4}");       // format! не забирает владение
    println!("{s54}");

    // Итерация по символам (Unicode)
    for c in "HI".chars() {
        println!("char: {c}");
    }

    // Итерация по байтам
    for b in "HI".bytes() {
        println!("byte: {b}");
    }
}

// ============ HashMap ============
fn demo_hashmap() {
    println!("\n=== HashMap ===");

    // Создание и вставка
    let mut scores = HashMap::new();
    scores.insert("Blue".to_string(), 10);
    scores.insert("Red".to_string(), 5);
    scores.insert("Green".to_string(), 8);

    println!("{:?}", scores);

    // Итерация по парам ключ-значение
    let mut group = 0;
    for (team, score) in &scores {
        println!("{team}: {score}");
        group += *score;
    }
    println!("Всего очков: {group}");

    // Безопасный доступ к значению
    let a = scores.get("Red").copied().unwrap_or(0);
    println!("Red: {a}");

    // Entry API: or_insert — вставляет если ключа нет
    scores.entry("Black".to_string()).or_insert(20);
    println!("После entry: {:?}", scores);

    // Владение: HashMap забирает владение ключами и значениями
    let x = String::from("First");
    let y = String::from("Second");
    let mut newhm = HashMap::new();
    newhm.insert(x, y);    // x и y перемещены в HashMap
    // println!("{x}");    // ОШИБКА: x moved

    // Подсчёт слов через entry API
    let mut map = HashMap::new();
    for word in "hello world world hello hi".split_whitespace() {
        let count = map.entry(word).or_insert(0);
        *count += 1;
    }
    println!("Частотность: {:?}", map);
}

// Функция для демонстрации передачи HashMap по ссылке
fn add_score(map: &mut HashMap<String, i32>, team: &str, points: i32) {
    let score = map.entry(team.to_string()).or_insert(0);
    *score += points;
}

// Безопасный доступ к вектору
fn demo_vec_access() {
    println!("\n=== Безопасный доступ к Vec ===");

    let v = vec![1, 2, 3, 4, 5];
    let third: &i32 = &v[2];               // может паниковать
    println!("Третий элемент (index): {third}");

    let third: Option<&i32> = v.get(2);    // безопасно
    match third {
        Some(third) => println!("Третий элемент (get): {third}"),
        None => println!("Нет третьего элемента"),
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
    println!("{:?}", scores);

    demo_vec_access();
}
