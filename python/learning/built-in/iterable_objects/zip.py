from itertools import zip_longest

list_1 = ["Король", "Дама", "Валет"]
list_2 = [13, 12]
output = list(zip(list_1, list_2))
print(output)
for pair in zip(list_1, list_2, strict=False):
    print(pair)

print(list(zip_longest(list_1,list_2, fillvalue=None)))

dict_1 = [("x", 1), ("y", 2), ("z", 3)]
value_1, value_2 = zip(*dict_1)
print(value_1)
print(value_2)


matrix_1 = [
    [1,2,3],
    [4,5,6]
]
print(matrix_1)
