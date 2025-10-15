nums = [3, 4, 5, 2]
def twoSum(nums,target):
    cache_map = {}
    for index, value in enumerate(nums):
        if target - value in cache_map:
            return [cache_map[target-value], index]
        cache_map[value] = index
    return "Нет таких чисел"
print(twoSum(nums,9))


list_1 = [1, 2, 3, 4, 5]
def index_1(list_1:list, target:int):
    for index, value in enumerate(list_1):
        if value == target:
            return index

print(index_1(list_1,3))
