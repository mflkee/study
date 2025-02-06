def find_by_key():

    measuring_instruments = [
        {"id": 1, "name": "УДВН", "serial_number": "3261"},
        {"id": 2, "name": "УДВН", "serial_number": "3262"},
        {"id": 3, "name": "FVM", "serial_number": "21184805"},
        {"id": 4, "name": "FVM", "serial_number": "21184811"},
    ]

    for item in measuring_instruments:
        if item.get("serial_number") == "3262":  # Используем метод .get() для безопасного доступа к ключу
            return item

if __name__ == "__main__":
    result = find_by_key()
    print(result)

