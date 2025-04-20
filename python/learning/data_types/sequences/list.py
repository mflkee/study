list_1 = [1, 2, 3, 4]
list_2 = [2]
list_3 = ['python', 'java', 'kotlin', 'c++', 'rust', 'c', 'javascript']
i = 0
# list_1.clear() # []
# i = list_2.copy() # [2]
# i = list_3.count('python') # 1
# list_1.extend(list_2) # [1, 2, 3, 4, 2]
list_1.append(5) # [1, 2, 3, 4, 5]
# i = list_1.index(2) # 1
# list_1.insert(1, 666) # [1, 666, 2, 3, 4]
# i = list_1.pop(2) # 3
# list_1.remove(1) # [2, 3, 4]
list_1.reverse() # [4, 3, 2, 1]
list_3.sort()

print(list_1)
print(list_2)
print(list_3)
print(i)

