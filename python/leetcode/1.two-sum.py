nums = [3, 4, 5, 2]
def twoSum(nums,target):
    cache_map = {}
    for index, value in enumerate(nums):
        if target - value in cache_map:
            return [cache_map[target-value], index]
        cache_map[value] = index
    return "Нет таких чисел"

print(twoSum(nums,9))
