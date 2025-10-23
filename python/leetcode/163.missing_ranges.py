from typing import List

class Solution:
    def missingRanges(self, nums:List[int], lower:int, upper:int) -> List[List[int]]:
        missing_elements = []
        lenght_nums = len(nums)
        if lenght_nums == 0:
            missing_elements.append([lower, upper])
            return missing_elements
        if lower < nums[0]:
            missing_elements.append([lower, (nums[0]-1)])
        for i in range(lenght_nums - 1):
            if nums[i+1] - nums[i] == 1:
                continue
            missing_elements.append([nums[i]+1, nums[i+1]-1])
        if upper > lenght_nums - 1:
            missing_elements.append([nums[lenght_nums-1], upper])
        return missing_elements

s = Solution()
nums = [2,3,4,10,11,18]
print(s.missingRanges(nums,1,20))
