from typing import List
class Solution:
    def matrixReshape(self, mat: List[List[int]], r: int, c: int) -> List[List[int]]:
        if r * c != len(mat) * len(mat[0]):
            return mat
        one_val = []
        for i in range(len(mat)):
            for j in range(len(mat[i])):
                one_val.append(mat[i][j])

        result = []
        for i in range(0, len(one_val), c):
            result.append(one_val[i:i + c])

        return result

s = Solution()
mat = [[1,2],[3,4]]
print(s.matrixReshape(mat,2,4))
print(s.matrixReshape(mat,1,4))
