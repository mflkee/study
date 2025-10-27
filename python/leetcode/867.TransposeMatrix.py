from typing import List


class Solution:
    def transpose(self, matrix: List[List[int]]) -> List[List[int]]:
        rows = len(matrix)
        cols = len(matrix[0])
        
        # Создаем пустую матрицу для результата
        new_matrix = [[0] * rows for _ in range(cols)]
        
        for i in range(rows):
            for j in range(cols):
                new_matrix[j][i] = matrix[i][j]
        
        return new_matrix

                

matrix = [[1,2,3],
          [4,5,6],
          [7,8,9]]

s = Solution()
print(s.transpose(matrix))
