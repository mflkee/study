# list_1 = [1, 2, 3, 4]
# list_2 = [5, 6, 7]
list_3 = ["python", "java", "kotlin", "c++", "rust", "c", "javascript"]
# list_4 = [3, 1, 4, 6]
# i = 0
# list_1.clear() # []
# i = list_2.copy() # [2]
# i = list_3.count('python') # 1
# list_1.extend(list_2) # [1, 2, 3, 4, 2]
# list_1.append(5)  # [1, 2, 3, 4, 5]
# i = list_1.index(2) # 1
# list_1.insert(1, 666) # [1, 666, 2, 3, 4]
# i = list_1.pop(2) # 3
# list_1.remove(1) # [2, 3, 4]
# list_1.reverse()  # [4, 3, 2, 1]
# list_4.sort()

# print(list_1)
# print(list_2)
# print(list_3)
# print(i)
# print(list_4)

# # slice 
# list_4 = [1, 2, 3, 4, 5, 6, 7, 8]
# mid = len(list_4) // 2
# left_half = list_4[:mid]
# right_half = list_4[mid:]
# print(left_half)
# print(right_half)
# print(mid)

# list_1.extend(list_2[0:])
# print(list_1)


# value = (list_1[-2]/2) # берем предпослений элемент и делим на два
# value_2 = value * 2 # умножаем на 2 
# print(int(value_2)) # чтобы было int

# print(list_3[-1].title()) # Javascript

# # Добавление в середину списка нового элемента
# len_list_3 = len(list_3)
# # print(len_list_3)
# mid = len_list_3 // 2
# list_3.insert(mid,"go")
# print(list_3)
#
# del(list_3[1])
# print(list_3)
# list_3.pop() # удаляет последний элемент в списке
# print(list_3)

print(sorted(list_3)) # выводит отсортированный массив

for i in list_3: # выводим весь список в терминал
    if i in ['python', 'javascript']:
        print(f'This is high lavel LP {i.title()}')
    else:
        print(f'This is low lavel LP {i.title()}')







