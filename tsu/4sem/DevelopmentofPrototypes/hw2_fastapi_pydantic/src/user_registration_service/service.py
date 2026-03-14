from __future__ import annotations

from typing import Any, TypeAlias

from pydantic import ValidationError

from user_registration_service.models import UserRegistration

RegistrationResult: TypeAlias = UserRegistration | list[str]


def _friendly_error_message(field: str, message: str) -> str:
    normalized_message = message.removeprefix("Value error, ")

    if field == "email" and "valid email address" in message.lower():
        return "Некорректный email-адрес."
    return normalized_message


def _format_validation_errors(exc: ValidationError) -> list[str]:
    errors: list[str] = []

    for error in exc.errors():
        location = ".".join(str(part) for part in error["loc"]) or "model"
        message = _friendly_error_message(location, error["msg"])
        errors.append(f"{location}: {message}")

    return errors


def register_user(data: dict[str, Any]) -> RegistrationResult:
    try:
        return UserRegistration.model_validate(data)
    except ValidationError as exc:
        return _format_validation_errors(exc)
