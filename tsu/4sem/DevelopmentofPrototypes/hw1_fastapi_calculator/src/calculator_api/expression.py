from __future__ import annotations

import ast
import operator
from dataclasses import dataclass
from typing import TypeAlias

Number: TypeAlias = int | float
Operand: TypeAlias = Number | str

_ALLOWED_BINARY_OPERATORS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
}

_ALLOWED_UNARY_OPERATORS = {
    ast.UAdd: lambda value: value,
    ast.USub: operator.neg,
}


def _normalize_result(value: float) -> int | float:
    return int(value) if value.is_integer() else value


def format_number(value: Number) -> str:
    as_float = float(value)
    return str(int(as_float)) if as_float.is_integer() else str(as_float)


def _is_number_constant(node: ast.AST) -> bool:
    return (
        isinstance(node, ast.Constant)
        and isinstance(node.value, (int, float))
        and not isinstance(node.value, bool)
    )


def validate_expression(expression: str) -> str:
    normalized = expression.strip()
    if not normalized:
        raise ValueError("Expression must not be empty.")

    try:
        parsed = ast.parse(normalized, mode="eval")
    except SyntaxError as exc:
        raise ValueError("Expression has invalid syntax.") from exc

    allowed_nodes = (
        ast.Expression,
        ast.BinOp,
        ast.UnaryOp,
        ast.Add,
        ast.Sub,
        ast.Mult,
        ast.Div,
        ast.Constant,
        ast.UAdd,
        ast.USub,
        ast.Load,
    )

    for node in ast.walk(parsed):
        if _is_number_constant(node):
            continue
        if isinstance(node, allowed_nodes):
            continue
        raise ValueError("Only numbers, parentheses and +, -, *, / are allowed.")

    return normalized


def evaluate_expression(expression: str) -> int | float:
    validated = validate_expression(expression)
    parsed = ast.parse(validated, mode="eval")
    result = _evaluate_node(parsed.body)
    return _normalize_result(float(result))


def _evaluate_node(node: ast.AST) -> float:
    if _is_number_constant(node):
        return float(node.value)

    if isinstance(node, ast.BinOp):
        operation = _ALLOWED_BINARY_OPERATORS.get(type(node.op))
        if operation is None:
            raise ValueError("Unsupported binary operator.")

        left = _evaluate_node(node.left)
        right = _evaluate_node(node.right)

        if isinstance(node.op, ast.Div) and right == 0:
            raise ZeroDivisionError("Division by zero is not allowed.")

        return float(operation(left, right))

    if isinstance(node, ast.UnaryOp):
        operation = _ALLOWED_UNARY_OPERATORS.get(type(node.op))
        if operation is None:
            raise ValueError("Unsupported unary operator.")
        return float(operation(_evaluate_node(node.operand)))

    raise ValueError("Unsupported expression node.")


@dataclass
class ExpressionStore:
    current_expression: str | None = None

    def get(self) -> str | None:
        return self.current_expression

    def set(self, expression: str) -> str:
        self.current_expression = validate_expression(expression)
        return self.current_expression

    def compose(self, left: Operand, op: str, right: Operand) -> str:
        if op not in {"+", "-", "*", "/"}:
            raise ValueError("Operation must be one of: +, -, *, /.")

        expression = f"{self._stringify_operand(left)} {op} {self._stringify_operand(right)}"
        self.current_expression = validate_expression(expression)
        return self.current_expression

    def append(self, op: str, operand: Operand) -> str:
        if self.current_expression is None:
            raise ValueError("Current expression is not set.")
        return self.compose(self.current_expression, op, operand)

    def evaluate_current(self) -> int | float:
        if self.current_expression is None:
            raise ValueError("Current expression is not set.")
        return evaluate_expression(self.current_expression)

    @staticmethod
    def _stringify_operand(value: Operand) -> str:
        if isinstance(value, str):
            return f"({validate_expression(value)})"
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ValueError("Operand must be a number or expression string.")
        return format_number(value)
