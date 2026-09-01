target = 98
nums: list[int] = [3, 5, 11, 15, 47, 98]


def bsearch(nums, target):
    low = 0
    hight: int = len(nums) - 1
    while low <= hight:
        mid: int = (low + hight) // 2
        if nums[mid] == target:
            return mid
        if target < nums[mid]:
            hight: int = mid - 1
        else:
            low: int = mid + 1


print(bsearch(nums, target))
