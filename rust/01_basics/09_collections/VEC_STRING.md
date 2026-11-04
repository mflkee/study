# Vec и String — шпаргалка и грабли

Две самые ходовые коллекции Rust. Обе владеют данными в куче и умеют расти,
но у каждой свои ловушки: у `Vec` — владение и индексация, у `String` — UTF-8.

- Rust Book: <https://doc.rust-lang.org/book/ch08-00-common-collections.html>
- std `Vec`: <https://doc.rust-lang.org/std/vec/struct.Vec.html>
- std `String`: <https://doc.rust-lang.org/std/string/struct.String.html>
- Живые примеры: `02_exercises/01_vectors/`, `02_exercises/02_strings/`

## 1. Vec&lt;T&gt; — создание

```rust
let mut v: Vec<i32> = Vec::new();
let v2 = vec![1, 2, 3];                       // макрос — короче
let v3 = vec![0; 5];                          // [0, 0, 0, 0, 0]
let v4: Vec<i32> = (1..=5).collect();         // из итератора
let v5 = Vec::with_capacity(100);             // сразу резерв — меньше реаллокаций
```

## 2. Основные операции

| Операция               | Что делает                              |
|------------------------|-----------------------------------------|
| `push(x)` / `pop()`    | в конец / с конца (`Option<T>`)         |
| `insert(i, x)`         | вставить по индексу (сдвиг хвоста)      |
| `remove(i)`            | удалить по индексу (сдвиг хвоста)       |
| `v[i]`                 | **паникует**, если вышли за границы     |
| `get(i)`               | `Option<&T>` — безопасно                |
| `len()` / `is_empty()` | размер                                  |
| `contains(&x)`         | есть ли элемент (`PartialEq`)           |
| `extend(iter)`         | добавить много                          |

```rust
let mut v = vec![10, 20, 30];
v.push(40);
let removed = v.pop();          // Some(40)

let a = &v[1];                  // 20 — паника при выходе за границы
let b = v.get(99);              // None — безопасно
```

## 3. Владение и заимствование — главная грабля

Нельзя менять вектор, пока держишь ссылку на его элемент:

```rust
let mut v = vec![1, 2, 3];
let first = &v[0];      // неизменяемое заимствование
// v.push(4);           // ОШИБКА: mutable borrow while immutable borrow exists
println!("{first}");    // borrow заканчивается здесь
v.push(4);              // теперь можно
```

Причина: `push` может реаллоцировать буфер, и `first` повиснет. Компилятор
ловит это заранее.

## 4. Итерация

```rust
for x in &v        { }   // x: &T
for x in &mut v    { *x += 1; }   // x: &mut T
for x in v         { }   // владение, вектор потреблён (into_iter)

v.iter().map(|x| x * 2).collect::<Vec<_>>();
```

## 5. Полезные методы

```rust
let mut v = vec![3, 1, 2, 2, 5];

v.sort();                       // [1, 2, 2, 3, 5]
v.dedup();                      // [1, 2, 3, 5] — убирает ПОДРЯД идущие дубли
v.retain(|&x| x % 2 == 1);      // [1, 3, 5]
v.reverse();
let s: i32 = v.iter().sum();

v.windows(2);                   // скользящие окна: [a,b], [b,c], ...
v.chunks(2);                    // куски по 2

let words = vec!["a", "b", "c"];
let joined = words.join(", ");  // "a, b, c"
```

> `dedup()` убирает только соседние повторы — сначала `sort()`, потом `dedup()`.

## 6. Срезы `&[T]`

Срез — «окно» в чужой массив/вектор, без владения:

```rust
fn sum(nums: &[i32]) -> i32 { nums.iter().sum() }
let v = vec![1, 2, 3, 4];
sum(&v);                        // &Vec<i32> сам превращается в &[i32]
let tail = &v[2..];             // срез [3, 4]
```

В сигнатуре функции почти всегда лучше `&[T]`, а не `&Vec<T>` — принимает и
вектор, и массив, и срез.

## 7. String и &str

- `String` — владеющая, изменяемая строка (данные в куче);
- `&str` — заимствованный строковый срез (часто — литерал или кусок `String`).

```rust
let s = String::from("hello");
let t = "hello".to_string();
let u: &str = &s;               // &String -> &str (deref coercion)

let mut m = String::new();
m.push_str("ab");               // добавить строку
m.push('c');                    // добавить символ

let combined = format!("{s} {t}");   // format! не забирает владение
let moved = s + " world";            // `+` ЗАБИРАЕТ s, правый — &str
```

## 8. UTF-8 — где всё ломается

```rust
let s = "привет";
s.len();                    // 12 — это БАЙТЫ, а не буквы!
s.chars().count();          // 6 — символы
&s[0..2];                   // "пр" — ок (граница символа)
// &s[0..1];                // ПАНИКА: режем букву 'п' посередине
```

Выводы:

- длина строки ≠ число символов; индексация строки по «номеру буквы» **не работает**;
- для посимвольной работы — `.chars()`, `.char_indices()`, а не индексы;
- обратный проход по строке — `s.chars().rev()` (у `&str` нет обратной индексации);
- резать строку можно только по границам символов (или `get` возвращает `None`/`is_char_boundary`).

Частые методы:

```rust
"  hi  ".trim()                 // "hi"
"a,b,c".split(',').collect::<Vec<_>>()
"s".split_whitespace()
"ok".starts_with('o')
"abc".replace('a', "A")         // "Abc"
"no".to_lowercase()
"42".parse::<i32>()             // Ok(42)
```

В сигнатурах принимай `&str`, а не `&String` — тогда функция берёт и литерал,
и `String`, и срез.

## 9. Частые ошибки

- `v[i]`/`&s[a..b]` вместо `get` там, где выход за границы возможен.
- Менять `Vec`, пока держишь ссылку на элемент (ошибка borrow).
- Путать `len()` (байты) и `chars().count()` (символы).
- Резать строку не по границе символа.
- `&String` в параметрах вместо `&str`; `&Vec<T>` вместо `&[T]`.
- `dedup()` без предварительной `sort()`.

## 10. Что потрогать руками

- `02_exercises/01_vectors/01_vec_stack` … `04_table_row`
- `02_exercises/02_strings/01_join_with_comma` … `03_pig_latin`
- `01_basics/06_slices`, `01_basics/09_collections`

## 11. Вопросы для самопроверки

1. Почему `&v[0]` и `v.push(x)` не могут сосуществовать?
2. Что отличает `v.get(i)` от `v[i]`?
3. Почему `"привет".len() == 12`, а не 6?
4. Когда в сигнатуре писать `&str`, а не `&String`?
5. Зачем `dedup()` просят вызывать после `sort()`?
