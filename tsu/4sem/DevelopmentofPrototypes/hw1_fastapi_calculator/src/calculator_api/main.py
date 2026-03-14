from __future__ import annotations

from typing import Literal, NoReturn

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from calculator_api.expression import (
    ExpressionStore,
    evaluate_expression,
    format_number,
)

app = FastAPI(
    title="FastAPI Calculator",
    description="Simple calculator with support for complex arithmetic expressions.",
    version="1.0.0",
)

store = ExpressionStore()


class ExpressionPayload(BaseModel):
    expression: str = Field(..., examples=["(2 + 3) * 4 + (10 - 6) / 2"])


class ComposeExpressionPayload(BaseModel):
    left: float | str = Field(..., examples=["2 + 3"])
    op: Literal["+", "-", "*", "/"]
    right: float | str = Field(..., examples=[4])


class AppendExpressionPayload(BaseModel):
    op: Literal["+", "-", "*", "/"]
    operand: float | str = Field(..., examples=["(10 - 6) / 2"])


def _operation_response(
    a: float,
    b: float,
    operator_symbol: str,
    result: int | float,
) -> dict[str, str | int | float]:
    return {
        "expression": f"{format_number(a)} {operator_symbol} {format_number(b)}",
        "result": result,
    }


def _raise_bad_request(exc: ValueError | ZeroDivisionError) -> NoReturn:
    raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/")
async def root() -> dict[str, str]:
    return {
        "message": "FastAPI calculator is running. Open /docs to explore the API.",
    }


@app.get("/operations/add")
async def add(a: float, b: float) -> dict[str, str | int | float]:
    result = a + b
    return _operation_response(
        a,
        b,
        "+",
        int(result) if result.is_integer() else result,
    )


@app.get("/operations/subtract")
async def subtract(a: float, b: float) -> dict[str, str | int | float]:
    result = a - b
    return _operation_response(
        a,
        b,
        "-",
        int(result) if result.is_integer() else result,
    )


@app.get("/operations/multiply")
async def multiply(a: float, b: float) -> dict[str, str | int | float]:
    result = a * b
    return _operation_response(
        a,
        b,
        "*",
        int(result) if result.is_integer() else result,
    )


@app.get("/operations/divide")
async def divide(a: float, b: float) -> dict[str, str | int | float]:
    if b == 0:
        raise HTTPException(status_code=400, detail="Division by zero is not allowed.")
    result = a / b
    return _operation_response(
        a,
        b,
        "/",
        int(result) if result.is_integer() else result,
    )


@app.put("/expression")
async def set_expression(payload: ExpressionPayload) -> dict[str, str]:
    try:
        return {"expression": store.set(payload.expression)}
    except ValueError as exc:
        _raise_bad_request(exc)


@app.post("/expression/compose")
async def compose_expression(payload: ComposeExpressionPayload) -> dict[str, str]:
    try:
        return {"expression": store.compose(payload.left, payload.op, payload.right)}
    except ValueError as exc:
        _raise_bad_request(exc)


@app.post("/expression/append")
async def append_expression(payload: AppendExpressionPayload) -> dict[str, str]:
    try:
        return {"expression": store.append(payload.op, payload.operand)}
    except ValueError as exc:
        _raise_bad_request(exc)


@app.get("/expression")
async def get_expression() -> dict[str, str | None]:
    return {"expression": store.get()}


@app.post("/expression/evaluate")
async def evaluate_current_expression() -> dict[str, str | int | float]:
    try:
        expression = store.get()
        if expression is None:
            raise ValueError("Current expression is not set.")
        return {
            "expression": expression,
            "result": store.evaluate_current(),
        }
    except (ValueError, ZeroDivisionError) as exc:
        _raise_bad_request(exc)


@app.post("/expression/parse-and-evaluate")
async def parse_and_evaluate(
    payload: ExpressionPayload,
) -> dict[str, str | int | float]:
    try:
        expression = store.set(payload.expression)
        return {
            "expression": expression,
            "result": evaluate_expression(expression),
        }
    except (ValueError, ZeroDivisionError) as exc:
        _raise_bad_request(exc)
