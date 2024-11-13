my_list = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]


def binary_search(list, item):
    low = 0
    hight = len(list) - 1

    while low <= hight:
        mid = (low + hight) // 2
        if list[mid] == item:
            return mid
        if list[mid] > item:
            hight = mid - 1
        else:
            low = mid + 1
    return print("Нет такого")


print(binary_search(my_list, 16))
