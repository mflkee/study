def binary_search(arr, target):
    low = 0
    high = len(arr) - 1 
    while high >= low:
        mid = (low + high) // 2
        if target == list[mid]:
            return mid

        if list[mid] < target:
            low = mid + 1

        else:
            high = mid - 1
    return None

try:
    guesss = int(input("Введите искомое чилсо: "))
except ValueError: 
    print("Введите целое число")

my_list = []
for i in range(1,20):
    my_list.append(i)

