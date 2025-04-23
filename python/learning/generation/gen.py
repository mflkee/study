from random import choice, randint

list_1 = []
for i in range(1, 16):
    list_1.append(i)

print(list_1)


list_2 = [i for i in range(1, 16)]
print(list_2)

list_3 = [i**2 for i in list_2]
print(list_3)

list_4 = [i // 2 for i in list_3]
print(list_4)

dict_1 = {"python": 3, "rust": 10, "c++": 9, "js": 5}
print(dict_1)
list_5 = [[i, dict_1[i]] for i in dict_1]
print(list_5) # [['python', 3], ['rust', 10], ['c++', 9], ['js', 5]]

# эти две генерации эквивалентны друг другу:
list_6 = [j for i in list_5 for j in i]
print(list_6)

list_7 = []
for i in list_5:
    for j in i:
        list_7.append(j)
print(list_7)

# извлекаем цифры из str и передаем в список:
string_1 = "a6bad98be13a0fd9a4a528689d9e122b  gen.py"
list_8 = [int(i) for i in string_1 if "0"<=i<="9"] # диапазон чисел сравнивается числовым кодом
print(list_8)

string_2 = "123456789" 
for i in string_2:
    print(ord(i)) # вывод числового кодf

# Генерация четных, нечетных, рандомных упорядоченных, рандомных чисел
list_9 = list(range(0,101,1))
print(list_9)
list_9 = [choice(list_9) for _ in range(10)]
print(list_9)
list_9.sort()
print(list_9)

print("-----------------------------------------------------------------")
print("КОНСОЛЬНАЯ ИГРА УГАДАЙ ЧИСЛО В РАНДОМНО СГЕНЕРИРОВАННОМ СПИСКЕ!!!")
print("-----------------------------------------------------------------")
x = input("")



