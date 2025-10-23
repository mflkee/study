from typing import List

class Solution:
    def diagonalSum(self, mat: List[List[int]]) -> int:
        n = len(mat)
        primary = sum(mat[i][i] for i in range(n))
        secondary = sum(mat[i][n - 1 - i] for i in range(n))
        mid_n = n // 2
        if len(mat)%2!=0:
            return primary + secondary - mat[mid_n][mid_n]
        else:
            return primary + secondary


mat = [[1,1,1,1],[1,1,1,1],[1,1,1,1],[1,1,1,1]]
s = Solution()
print(s.diagonalSum(mat))
