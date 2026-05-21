// Company Directory CLI — справочник сотрудников
// Демонстрирует: HashMap, структуры, ввод/вывод, обработка ошибок
// Хранит список сотрудников по отделам и позволяет искать

use std::collections::HashMap;
use std::io;

/// Структура сотрудника с именем и должностью
#[derive(Debug, Clone)]
struct Employee {
    name: String,
    position: String,
}

fn main() {
    println!("📋 Справочник сотрудников");
    println!("Команды: add — добавить, list — список, find — найти, quit — выход");

    // Ключ — название отдела, значение — список сотрудников
    let mut directory: HashMap<String, Vec<Employee>> = HashMap::new();

    loop {
        println!("\nВведите команду:");
        let mut input = String::new();
        io::stdin().read_line(&mut input).expect("Ошибка ввода");
        let input = input.trim();

        match input {
            "add" => add_employee(&mut directory),
            "list" => list_all(&directory),
            "find" => find_employee(&directory),
            "quit" => {
                println!("До свидания!");
                break;
            }
            _ => println!("Неизвестная команда. Используйте: add, list, find, quit"),
        }
    }
}

/// Добавляет нового сотрудника в отдел
fn add_employee(dir: &mut HashMap<String, Vec<Employee>>) {
    let mut name = String::new();
    let mut department = String::new();
    let mut position = String::new();

    println!("Имя сотрудника:");
    io::stdin().read_line(&mut name).expect("Ошибка ввода");
    println!("Отдел:");
    io::stdin().read_line(&mut department).expect("Ошибка ввода");
    println!("Должность:");
    io::stdin().read_line(&mut position).expect("Ошибка ввода");

    let emp = Employee {
        name: name.trim().to_string(),
        position: position.trim().to_string(),
    };
    // entry API: создаём пустой вектор если отдела ещё нет
    dir.entry(department.trim().to_string())
        .or_insert(Vec::new())
        .push(emp);

    println!("✅ Сотрудник добавлен");
}

/// Выводит всех сотрудников по отделам
fn list_all(dir: &HashMap<String, Vec<Employee>>) {
    if dir.is_empty() {
        println!("Справочник пуст");
        return;
    }
    for (dept, employees) in dir {
        println!("\n--- {dept} ---");
        for emp in employees {
            println!("  {} — {}", emp.name, emp.position);
        }
    }
}

/// Ищет сотрудника по имени (частичное совпадение)
fn find_employee(dir: &HashMap<String, Vec<Employee>>) {
    let mut query = String::new();
    println!("Введите имя для поиска:");
    io::stdin().read_line(&mut query).expect("Ошибка ввода");
    let query = query.trim().to_lowercase();

    let mut found = false;
    for (_dept, employees) in dir {
        for emp in employees {
            if emp.name.to_lowercase().contains(&query) {
                println!("  {} — {}", emp.name, emp.position);
                found = true;
            }
        }
    }
    if !found {
        println!("Сотрудники не найдены");
    }
}
