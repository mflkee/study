def binarysearch(lst, target):
    left = 0
    right = len(lst) - 1
    while left <= right:
        mid = (left + right) // 2
        if target == lst[mid]:
            return mid
        if target < mid:
            right = mid - 1
        else:
            left = mid + 1
    return  

lst_1 = [1, 2, 3, 4, 5, 6, 7, 8]
print(binarysearch(lst_1,7))

