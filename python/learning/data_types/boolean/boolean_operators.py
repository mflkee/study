int_1 = 9
int_2 = 10
bool_int = int_1 <= int_2
print(bool_int)

list_1 = [1, 2, 3, 4, 5, 6]
list_2 = [1, 2, 3, 4, 5, 6]
bool_list = list_1 == list_2
bool_list_obj = list_1 is list_2

print(
    f"Проверка эдентичности содержания списков: {bool_list} \nПроверка эдентичности обьекта в памяти: {bool_list_obj}"
)


float_1 = 13.40
float_2 = 13.4
bool_float = float_1 != float_2
print(bool_float)
