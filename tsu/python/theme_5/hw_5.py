from datetime import datetime

# Определение форматов дат для каждой газеты
formats = {
    "The Moscow Times": "%A, %B %d, %Y",
    "The Guardian": "%A, %d.%m.%y",
    "Daily News": "%A, %d %B %Y",
    "The Time": "%d.%m.%Y"
}

def parse_date(gazeta, date_str):
    # Парсит строку даты в объект datetime по заданному формату
    try:
        return datetime.strptime(date_str, formats[gazeta])
    except (KeyError, ValueError):
        print(f"Ошибка: Неправильный формат даты для '{gazeta}' или газета не найдена.")
        return None

def main():
    # Спецсимвол для завершения программы
    stop_symbol = "#quit"
    
    while True:
        gazeta = input("Введите название газеты (или '#quit' для выхода): ")
        if gazeta.lower() == stop_symbol:
            break
        
        date_str = input("Введите дату в соответствующем формате: ")
        
        if date_str.lower() == stop_symbol:
            break
        
        result = parse_date(gazeta, date_str)
        if result:
            print(f"Дата в формате datetime: {result}")

if __name__ == "__main__":
    main()
