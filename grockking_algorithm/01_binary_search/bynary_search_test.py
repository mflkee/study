my_list = list(range(2, 31, 2))
print(my_list)


def binary_search(list, item):
    low = 0
    hight = len(list) - 1
    while low <= hight:
        mid = (low + hight) // 2
        if item == list[mid]:
            return mid
        if item > list[mid]:
            low = mid + 1
        else:
            hight = mid - 1


print(binary_search(my_list, 8))
