from typing import List

class Solution:
    def removeElement(self, nums: List[int], val: int) -> int:
        k = 0
        for i in range(len(nums)):
            if nums[i] != val:
                nums[k] = nums[i]
                k += 1
        return k

s = Solution()
nums = [2, 2, 3, 4, 2]
val = 2
k = s.removeElement(nums, val)
print(k)       # 2
print(nums[:k])  # [3, 4]

