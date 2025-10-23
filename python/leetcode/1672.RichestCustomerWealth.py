from typing import List

class Solution:
    def maximumWealth(self, accounts: List[List[int]]) -> int:
        max_in_rows = []
        for i in range(len(accounts)):
            max_in_rows.append(sum(accounts[i]))
        return max(max_in_rows)

accounts = [[1,2,3],[3,2,1]]
s = Solution()
print(s.maximumWealth(accounts))



