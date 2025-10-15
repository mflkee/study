from typing import Optional
from typing_extensions import TypeForm


class TreeNode:
    def __init__(self, val=0, left=None, right=None):
        self.val = val
        self.left = left
        self.right = right


class Solution:
    def isSameTree(self, p: Optional[TreeNode], q: Optional[TreeNode]) -> bool:
        if p is None and q is None:
            return True
        if p is None or q is None:
            return False
        if p.val == q.val:
            return self.isSameTree(p.left, q.left) and self.isSameTree(p.right, q.right)
        return False


node1_left = TreeNode(2)
node1_right = TreeNode(1)
node1 = TreeNode(1, node1_left, node1_right)

node2_left = TreeNode(2)
node2_right = TreeNode(3)
node2 = TreeNode(1, node2_left, node2_right)

s = Solution()
result = s.isSameTree(node1, node2)
print(result)
