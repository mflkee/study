grid = [
  ["1","1","1","1","0"],
  ["1","1","0","1","0"],
  ["1","1","0","0","0"],
  ["0","0","0","0","0"]
]


for i in range(len(grid)):
    for j in range(len(grid[0])):
        print(f"grid[{i}][{j}] = {grid[i][j]}")



# for i in grid:
#     print(i)
#
# for i in grid:
#     print(i[0])



# ones = sum(cell == "1" for row in grid for cell in row)
# zeros = sum(cell == "0" for row in grid for cell in row)
# print("ones:", ones, "zeros:", zeros)


