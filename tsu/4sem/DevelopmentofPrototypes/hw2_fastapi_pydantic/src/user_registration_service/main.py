from __future__ import annotations

from typing import Any

from fastapi import FastAPI, HTTPException, status

from user_registration_service.models import UserRegistrationPublic
from user_registration_service.service import register_user

app = FastAPI(
    title="User Registration Service",
    description="FastAPI service for validating user registration data with Pydantic.",
    version="1.0.0",
)


@app.get("/")
async def root() -> dict[str, str]:
    return {"message": "User registration service is running. Open /docs."}


@app.post(
    "/register",
    response_model=UserRegistrationPublic,
    status_code=status.HTTP_201_CREATED,
)
async def register(payload: dict[str, Any]) -> UserRegistrationPublic:
    result = register_user(payload)
    if isinstance(result, list):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail=result,
        )
    return UserRegistrationPublic.from_registration(result)
