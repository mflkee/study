def input_positive_integer(prompt, min_value):
    """
    Вводит положительное целое число с проверкой на корректность.

    Args:
        prompt (str): Сообщение для пользователя.
        min_value (int): Минимально допустимое значение.

    Returns:
        int: Введенное пользователем целое число (не менее min_value).
    """
    while True:
        try:
            value = int(input(prompt))
            if value >= min_value:
                return value
            else:
                print(f"Значение должно быть не менее {min_value}. Попробуйте снова.")
        except ValueError:
            print("Некорректный ввод. Введите целое число.")


def hanoi_tower(num_disks, from_rod, to_rod, aux_rod, steps):
    """
    Решает задачу Ханойской башни для заданного количества дисков.

    Args:
        num_disks (int): Количество дисков.
        from_rod (str): Имя исходного стержня.
        to_rod (str): Имя целевого стержня.
        aux_rod (str): Имя вспомогательного стержня.
        steps (list): Список шагов для записи решений.
    """
    if num_disks == 1:
        steps.append(f"Диск 1: {from_rod} -> {to_rod}")
        return
    hanoi_tower(num_disks - 1, from_rod, aux_rod, to_rod, steps)
    steps.append(f"Диск {num_disks}: {from_rod} -> {to_rod}")
    hanoi_tower(num_disks - 1, aux_rod, to_rod, from_rod, steps)


def main():
    num_disks = input_positive_integer(
        "Введите количество дисков (не менее 1): ", min_value=1
    )
    rods = ["Стержень 1", "Стержень 2", "Стержень 3"]  # Фиксированные стержни

    steps = []
    hanoi_tower(num_disks, rods[0], rods[2], rods[1], steps)

    # Вывод решения в консоль
    for step in steps:
        print(step)

    print(f"Решение за {len(steps)} ходов:")

    # Запись решения в файл
    with open("решение.txt", "w", encoding="utf-8") as file:
        file.write(f"Решение за {len(steps)} ходов:\n")
        file.write("\n".join(steps))

    print("Решение записано в файл 'решение.txt'.")


if __name__ == "__main__":
    main()
