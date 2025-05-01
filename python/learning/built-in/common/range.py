numbers = list(range(-10, 6))  # создание списка от -10 до  R
print(numbers)

even_numbers = list(range(2, 11, 2))
print(even_numbers)

squares_1 = []
for value in range(1, 11):
    square = value**2
    squares_1.append(square)
print(squares_1)

squares_2 = [value**2 for value in range(1,11)]
print(squares_2)

lst = list(range(1,1000000))
print(min(lst))
print(max(lst))
print(sum(lst))

lst_2 = list(range(3,31))
for i in lst_2:
    if i % 3 == 0:
        print(i)

lst_3 = list(range(1,11))
for i in lst_3:
    print(i **3)

lst_4 = [i**3 for i in range(1,11)]
print(lst_4)


