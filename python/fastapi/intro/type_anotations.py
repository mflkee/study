def get_full_name(first_name: str, second_name: str):
    full_name = first_name.title() + " " + second_name.title()
    return full_name


id = get_full_name("глеб", "макеев")
print(id)

"""
Функция делает следующее:
    Принимает first_name и last_name.
    Преобразует первую букву содержимого каждой переменной в верхний регистр с title().
    Соединяет их через пробел.
"""

print("---")


def get_name_age(name: str, age: int):
    name_age = name + ", " + str(age)
    return name_age


print(get_name_age(id, 26))
